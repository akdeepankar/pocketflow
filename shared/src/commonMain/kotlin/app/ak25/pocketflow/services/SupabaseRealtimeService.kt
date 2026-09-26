package app.ak25.pocketflow.services

import app.ak25.pocketflow.models.Workflow
import app.ak25.pocketflow.storage.LocalStorage
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import kotlinx.serialization.Serializable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*

object SupabaseRealtimeService {

    sealed class RealtimeEvent {
        data class WorkflowUpserted(val workflow: Workflow) : RealtimeEvent()
        data class WorkflowDeleted(val workflowId: String) : RealtimeEvent()
        data class PresenceChanged(val workflowId: String, val activeUsers: List<String>) : RealtimeEvent()
        data class PresenceUpserted(val presenceId: String, val userId: String, val metadata: JsonObject) : RealtimeEvent()
        data class PresenceDeleted(val presenceId: String, val userId: String) : RealtimeEvent()
    }

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private var channelWorkflows: RealtimeChannel? = null
    private var channelPresence: RealtimeChannel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var jobs = mutableListOf<Job>()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    fun connect() {
        if (channelWorkflows != null) return
        
        println("[SupabaseRealtime] Connecting to realtime channels...")
        
        val chWf = supabaseClient.realtime.channel("workflows-db-changes")
        val chPres = supabaseClient.realtime.channel("presence-db-changes")
        
        channelWorkflows = chWf
        channelPresence = chPres

        val jobWf = scope.launch {
            try {
                chWf.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = SupabaseConfig.TABLE_WORKFLOWS
                }.collect { action ->
                    println("[SupabaseRealtime] Workflows table change: $action")
                    when (action) {
                        is PostgresAction.Insert -> {
                            val dbWf = json.decodeFromJsonElement<SupabaseWorkflow>(action.record)
                            val workflow = dbWf.toCore()
                            _events.emit(RealtimeEvent.WorkflowUpserted(workflow))
                        }
                        is PostgresAction.Update -> {
                            val dbWf = json.decodeFromJsonElement<SupabaseWorkflow>(action.record)
                            val workflow = dbWf.toCore()
                            _events.emit(RealtimeEvent.WorkflowUpserted(workflow))
                        }
                        is PostgresAction.Delete -> {
                            val oldWf = json.decodeFromJsonElement<SupabaseWorkflow>(action.oldRecord)
                            _events.emit(RealtimeEvent.WorkflowDeleted(oldWf.id))
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                println("[SupabaseRealtime] Workflows channel flow error: ${e.message}")
            }
        }
        
        val jobPres = scope.launch {
            try {
                chPres.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = SupabaseConfig.TABLE_PRESENCE
                }.collect { action ->
                    println("[SupabaseRealtime] Presence table change: $action")
                    when (action) {
                        is PostgresAction.Insert -> {
                            val dbPres = json.decodeFromJsonElement<SupabasePresence>(action.record)
                            val meta = buildJsonObject {
                                put("workflowId", JsonPrimitive(dbPres.workflowId))
                                put("userId", JsonPrimitive(dbPres.userId))
                                put("userName", JsonPrimitive(dbPres.userName))
                                put("updatedAt", JsonPrimitive(dbPres.updatedAt))
                                put("action", JsonPrimitive(dbPres.action))
                                put("cursorX", JsonPrimitive(dbPres.cursorX))
                                put("cursorY", JsonPrimitive(dbPres.cursorY))
                                put("nodeId", JsonPrimitive(dbPres.nodeId))
                            }
                            _events.emit(RealtimeEvent.PresenceUpserted(dbPres.id, dbPres.userId, meta))
                        }
                        is PostgresAction.Update -> {
                            val dbPres = json.decodeFromJsonElement<SupabasePresence>(action.record)
                            val meta = buildJsonObject {
                                put("workflowId", JsonPrimitive(dbPres.workflowId))
                                put("userId", JsonPrimitive(dbPres.userId))
                                put("userName", JsonPrimitive(dbPres.userName))
                                put("updatedAt", JsonPrimitive(dbPres.updatedAt))
                                put("action", JsonPrimitive(dbPres.action))
                                put("cursorX", JsonPrimitive(dbPres.cursorX))
                                put("cursorY", JsonPrimitive(dbPres.cursorY))
                                put("nodeId", JsonPrimitive(dbPres.nodeId))
                            }
                            _events.emit(RealtimeEvent.PresenceUpserted(dbPres.id, dbPres.userId, meta))
                        }
                        is PostgresAction.Delete -> {
                            val oldPres = json.decodeFromJsonElement<SupabasePresence>(action.oldRecord)
                            val presenceId = oldPres.id
                            
                            // Extract prefix from presenceId, e.g. pfn_abc123xy_workflowId -> abc123xy
                            val parts = presenceId.split("_")
                            val userPrefix = if (parts.size >= 2) parts[1] else ""
                            
                            // Find the member whose ID prefix matches userPrefix
                            val resolvedUserId = if (userPrefix.isNotEmpty()) {
                                // Try to match from active workflow members in local storage or share info
                                try {
                                    val workflowId = if (parts.size >= 3) parts[2] else ""
                                    val share = WorkflowShareRepository.getCachedSharePublic(workflowId)
                                    val match = share?.members?.firstOrNull { it.userId.take(8) == userPrefix }?.userId
                                        ?: (if (share?.ownerUserId?.take(8) == userPrefix) share.ownerUserId else "")
                                    match.ifEmpty { userPrefix }
                                } catch (e: Exception) {
                                    userPrefix
                                }
                            } else {
                                ""
                            }
                            
                            _events.emit(RealtimeEvent.PresenceDeleted(presenceId, resolvedUserId))
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                println("[SupabaseRealtime] Presence channel flow error: ${e.message}")
            }
        }
        
        jobs.add(jobWf)
        jobs.add(jobPres)

        scope.launch {
            try {
                chWf.subscribe()
                chPres.subscribe()
                println("[SupabaseRealtime] Subscribed to realtime channels")
            } catch (e: Exception) {
                println("[SupabaseRealtime] Subscribe error: ${e.message}")
            }
        }
    }

    fun disconnect() {
        println("[SupabaseRealtime] Disconnecting realtime channels...")
        jobs.forEach { it.cancel() }
        jobs.clear()
        
        scope.launch {
            try {
                channelWorkflows?.unsubscribe()
                channelPresence?.unsubscribe()
            } catch (e: Exception) {
                // ignore
            } finally {
                channelWorkflows = null
                channelPresence = null
            }
        }
    }

    private var activePresenceChannel: RealtimeChannel? = null
    private var activeBroadcastChannel: RealtimeChannel? = null
    private var activeWorkflowId: String? = null

    fun isPresenceSubscribed(): Boolean {
        val channel = activePresenceChannel ?: return false
        return channel.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED
    }

    suspend fun joinPresenceChannel(
        workflowId: String,
        userId: String,
        userName: String,
        onPresenceUpdated: (members: List<UserPresenceState>, nodes: List<NodePresenceState>) -> Unit
    ) {
        println("[RealtimeDebug] ➡️ joinPresenceChannel: workflowId=$workflowId, userId=$userId, userName=$userName")
        try {
            supabaseClient.realtime.connect()
            println("[RealtimeDebug] Explicit connect() initiated on realtime client")
        } catch (e: Exception) {
            println("[RealtimeDebug] Explicit connect() call error: ${e.message}")
        }
        activePresenceChannel?.unsubscribe()
        activeBroadcastChannel?.unsubscribe()
        
        activeWorkflowId = workflowId
        
        val presChannel = supabaseClient.realtime.channel("presence:workflow:$workflowId") {
            presence {
                key = userId
            }
        }
        val broadChannel = supabaseClient.realtime.channel("workflow:$workflowId")
        
        activePresenceChannel = presChannel
        activeBroadcastChannel = broadChannel
        
        val presenceUsers = mutableMapOf<String, ChannelPresenceUser>()
        val dragPositions = mutableMapOf<String, DragPayload>()
        
        fun triggerUpdate() {
            val usersSnapshot = presenceUsers.values.toList()
            val dragSnapshot = dragPositions.toMap()
            
            scope.launch(Dispatchers.Main) {
                val members = usersSnapshot.map {
                    UserPresenceState(
                        userId = it.userId,
                        userName = it.userName,
                        color = WorkflowShareRepository.getMemberColor(it.userId),
                        isActive = true
                    )
                }
                
                val nodes = usersSnapshot.map { p ->
                    val drag = dragSnapshot[p.userId]
                    if (drag != null) {
                        NodePresenceState(
                            userId = p.userId,
                            userName = p.userName,
                            nodeId = drag.nodeId,
                            action = NodeAction.DRAGGING,
                            color = WorkflowShareRepository.getMemberColor(p.userId),
                            cursorX = drag.x,
                            cursorY = drag.y
                        )
                    } else {
                        NodePresenceState(
                            userId = p.userId,
                            userName = p.userName,
                            nodeId = p.nodeId,
                            action = try { NodeAction.valueOf(p.action) } catch(e: Exception) { NodeAction.VIEWING },
                            color = WorkflowShareRepository.getMemberColor(p.userId),
                            cursorX = p.cursorX,
                            cursorY = p.cursorY
                        )
                    }
                }.filter { it.nodeId.isNotEmpty() }
                
                println("[RealtimeDebug] 🛠️ triggerUpdate Main thread: membersCount=${members.size}, nodesCount=${nodes.size}")
                onPresenceUpdated(members, nodes)
            }
        }
        
        scope.launch {
            try {
                println("[RealtimeDebug] 👂 Listening to presenceDataFlow on: presence:workflow:$workflowId")
                presChannel.presenceDataFlow<ChannelPresenceUser>().collect { users ->
                    println("[RealtimeDebug] 🟢 Received presenceDataFlow event: payloadSize=${users.size}")
                    presenceUsers.clear()
                    users.forEach { user ->
                        presenceUsers[user.userId] = user
                    }
                    println("[RealtimeDebug] 👤 Active presence snapshot: ${presenceUsers.values}")
                    triggerUpdate()
                }
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ Presence flow error: ${e.message}")
            }
        }
        
        scope.launch {
            try {
                println("[RealtimeDebug] 👂 Listening to broadcast event 'node_drag' on: workflow:$workflowId")
                broadChannel.broadcastFlow<JsonObject>("node_drag").collect { dragJo ->
                    val drag = json.decodeFromJsonElement<DragPayload>(dragJo)
                    println("[RealtimeDebug] 🎯 Received 'node_drag' broadcast: userId=${drag.userId}, nodeId=${drag.nodeId}, x=${drag.x}, y=${drag.y}")
                    dragPositions[drag.userId] = drag
                    triggerUpdate()
                }
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ Drag broadcast flow error: ${e.message}")
            }
        }
        
        scope.launch {
            try {
                println("[RealtimeDebug] 👂 Listening to broadcast event 'node_drag_end' on: workflow:$workflowId")
                broadChannel.broadcastFlow<JsonObject>("node_drag_end").collect { dragEndJo ->
                    val dragEnd = json.decodeFromJsonElement<DragEndPayload>(dragEndJo)
                    println("[RealtimeDebug] 🏁 Received 'node_drag_end' broadcast: userId=${dragEnd.userId}, nodeId=${dragEnd.nodeId}")
                    dragPositions.remove(dragEnd.userId)
                    triggerUpdate()
                }
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ DragEnd broadcast flow error: ${e.message}")
            }
        }
        
        val initial = ChannelPresenceUser(
            userId = userId,
            userName = userName,
            action = "VIEWING",
            cursorX = 0f,
            cursorY = 0f,
            nodeId = ""
        )
        val initialJo = json.encodeToJsonElement(initial).jsonObject
        
        scope.launch {
            try {
                presChannel.status.collect { status ->
                    println("[RealtimeDebug] Channel presence status changed to: $status")
                    if (status == RealtimeChannel.Status.SUBSCRIBED) {
                        println("[RealtimeDebug] Channel SUBSCRIBED - re-tracking presence to be sure!")
                        try {
                            presChannel.track(initialJo)
                        } catch (e: Exception) {
                            println("[RealtimeDebug] ❌ Auto-re-track failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[RealtimeDebug] Status flow collection error: ${e.message}")
            }
        }
        scope.launch {
            try {
                supabaseClient.realtime.status.collect { connectionStatus ->
                    println("[RealtimeDebug] Supabase client connection status changed to: $connectionStatus")
                    if (connectionStatus == io.github.jan.supabase.realtime.Realtime.Status.DISCONNECTED) {
                        println("[RealtimeDebug] Supabase DISCONNECTED - calling connect() to force reconnection!")
                        try {
                            supabaseClient.realtime.connect()
                        } catch (e: Exception) {
                            println("[RealtimeDebug] Force reconnect failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[RealtimeDebug] Connection status flow collection error: ${e.message}")
            }
        }
        try {
            presChannel.subscribe(blockUntilSubscribed = false)
            broadChannel.subscribe(blockUntilSubscribed = false)
            println("[RealtimeDebug] Subscribed successfully to both channels asynchronously")
            
            // Redundant immediate track execution to prevent coroutine execution delays from missing status flow emissions
            scope.launch {
                kotlinx.coroutines.delay(800)
                try {
                    presChannel.track(initialJo)
                    println("[RealtimeDebug] Redundant explicit presence track sent successfully")
                } catch (e: Exception) {
                    println("[RealtimeDebug] Redundant explicit presence track failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("[RealtimeDebug] ❌ Failed to subscribe: ${e.message}")
        }
    }

    suspend fun refreshOnlinePresence(
        userId: String,
        userName: String,
        action: NodeAction,
        nodeId: String = ""
    ) {
        val channel = activePresenceChannel ?: return
        
        if (channel.status.value != io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
            println("[RealtimeDebug] ⚠️ Presence channel is not SUBSCRIBED (${channel.status.value}). Re-subscribing...")
            try {
                channel.subscribe(blockUntilSubscribed = false)
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ Re-subscribe presence channel failed: ${e.message}")
            }
        }
        
        val broadCh = activeBroadcastChannel
        if (broadCh != null && broadCh.status.value != io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
            println("[RealtimeDebug] ⚠️ Broadcast channel is not SUBSCRIBED (${broadCh.status.value}). Re-subscribing...")
            try {
                broadCh.subscribe(blockUntilSubscribed = false)
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ Re-subscribe broadcast channel failed: ${e.message}")
            }
        }

        try {
            val pres = ChannelPresenceUser(
                userId = userId,
                userName = userName,
                action = action.name,
                nodeId = nodeId
            )
            val jo = json.encodeToJsonElement(pres).jsonObject
            channel.track(jo)
            println("[RealtimeDebug] 💓 Presence heartbeat tracked: action=$action, nodeId=$nodeId")
        } catch (e: Exception) {
            println("[RealtimeDebug] ⚠️ Error during refreshOnlinePresence: ${e.message}")
        }
    }

    suspend fun updateMyPresence(
        userId: String,
        userName: String,
        action: NodeAction,
        cursorX: Float = 0f,
        cursorY: Float = 0f,
        nodeId: String = ""
    ) {
        println("[RealtimeDebug] 📤 updateMyPresence: action=$action, nodeId=$nodeId, cursor=($cursorX, $cursorY)")
        
        // Auto-re-subscribe if the channel connection went cold
        val channel = activePresenceChannel
        if (channel != null && channel.status.value != io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
            println("[RealtimeDebug] ⚠️ Presence channel is not SUBSCRIBED (${channel.status.value}). Re-subscribing...")
            try {
                channel.subscribe(blockUntilSubscribed = false)
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ Re-subscribe presence channel failed: ${e.message}")
            }
        }
        
        val broadCh = activeBroadcastChannel
        if (broadCh != null && broadCh.status.value != io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
            println("[RealtimeDebug] ⚠️ Broadcast channel is not SUBSCRIBED (${broadCh.status.value}). Re-subscribing...")
            try {
                broadCh.subscribe(blockUntilSubscribed = false)
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ Re-subscribe broadcast channel failed: ${e.message}")
            }
        }

        try {
            if (action == NodeAction.DRAGGING) {
                val broadcastCh = activeBroadcastChannel ?: return
                val payload = DragPayload(
                    workflowId = activeWorkflowId ?: "",
                    nodeId = nodeId,
                    x = cursorX,
                    y = cursorY,
                    userId = userId
                )
                val payloadJo = json.encodeToJsonElement(payload).jsonObject
                broadcastCh.broadcast("node_drag", payloadJo)
            } else {
                val broadcastCh = activeBroadcastChannel
                if (broadcastCh != null) {
                    val endPayload = DragEndPayload(
                        workflowId = activeWorkflowId ?: "",
                        nodeId = nodeId,
                        userId = userId
                    )
                    val endPayloadJo = json.encodeToJsonElement(endPayload).jsonObject
                    broadcastCh.broadcast("node_drag_end", endPayloadJo)
                }
                
                val presChannel = activePresenceChannel ?: return
                val pres = ChannelPresenceUser(
                    userId = userId,
                    userName = userName,
                    action = action.name,
                    cursorX = cursorX,
                    cursorY = cursorY,
                    nodeId = nodeId
                )
                val jo = json.encodeToJsonElement(pres).jsonObject
                presChannel.track(jo)
            }
        } catch (e: Exception) {
            println("[RealtimeDebug] ⚠️ Error during updateMyPresence: ${e.message}")
        }
    }

    suspend fun leavePresenceChannel() {
        val pres = activePresenceChannel
        val broad = activeBroadcastChannel
        if (pres != null) {
            try { pres.unsubscribe() } catch (e: Exception) {}
            try { supabaseClient.realtime.removeChannel(pres) } catch (e: Exception) {}
        }
        if (broad != null) {
            try { broad.unsubscribe() } catch (e: Exception) {}
            try { supabaseClient.realtime.removeChannel(broad) } catch (e: Exception) {}
        }
        activePresenceChannel = null
        activeBroadcastChannel = null
        activeWorkflowId = null
    }
}

@Serializable
data class DragPayload(
    val workflowId: String,
    val nodeId: String,
    val x: Float,
    val y: Float,
    val userId: String
)

@Serializable
data class DragEndPayload(
    val workflowId: String,
    val nodeId: String,
    val userId: String
)

@Serializable
data class ChannelPresenceUser(
    val userId: String,
    val userName: String,
    val action: String = "VIEWING",
    val cursorX: Float = 0f,
    val cursorY: Float = 0f,
    val nodeId: String = ""
)
