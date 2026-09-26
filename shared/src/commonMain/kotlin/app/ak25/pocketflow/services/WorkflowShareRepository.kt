package app.ak25.pocketflow.services

import app.ak25.pocketflow.models.Workflow
import app.ak25.pocketflow.storage.LocalStorage
import app.ak25.pocketflow.services.SupabaseRepository

import androidx.compose.ui.graphics.Color
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.ak25.pocketflow.utils.getCurrentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

const val PRESENCE_TTL_MS = 15000L

/**
 * WorkflowMember — one slot in the workflows.membersJson array.
 */
@Serializable
data class WorkflowMember(
    val userId: String,
    val userName: String,
    val userEmail: String = "",
    val joinedAt: Long = 0L
)

/**
 * WorkflowShareInfo — the full share record returned from Supabase.
 */
@Serializable
data class WorkflowShareInfo(
    val workflowId: String,
    val ownerUserId: String,
    val joinCode: String,
    val workflowName: String = "Shared Workflow",
    val members: List<WorkflowMember> = emptyList()
)

/** A user currently active (presence not expired) inside a workflow. */
data class PresenceUser(val userId: String, val userName: String)

/** Actions a user can perform on a node — broadcast via Presences API. */
enum class NodeAction { VIEWING, HOLDING, DRAGGING, SELECTING, INSPECTING, TOUCHING, OPEN_NODE, RUNNING }

/**
 * Real-time state of a remote user's interaction with a specific node.
 * Rendered as a colored highlight + name label on that node in the editor.
 * cursorX/cursorY are canvas-space coordinates of the user's last touch.
 */
data class NodePresenceState(
    val userId: String,
    val userName: String,
    val nodeId: String,         // which node they're interacting with (empty = canvas touch only)
    val action: NodeAction,
    val color: Color,
    val cursorX: Float = 0f,    // canvas-space touch X
    val cursorY: Float = 0f     // canvas-space touch Y
)

/**
 * Presence entry for the header avatar row.
 * isActive = user currently has a non-expired presence entry.
 */
data class UserPresenceState(
    val userId: String,
    val userName: String,
    val color: Color,
    val isActive: Boolean,       // true = green ring (online now)
    val userEmail: String = ""
)

/** Typed result from joinByCode so the UI can show specific messages. */
sealed class JoinResult {
    data class Success(val info: WorkflowShareInfo) : JoinResult()
    object InvalidCode   : JoinResult()   // code not found in DB
    object SeatsFull     : JoinResult()   // workflow already has MAX_MEMBERS
    data class AlreadyMember(val info: WorkflowShareInfo) : JoinResult()  // caller is already in the list
    object GuestNotAllowed : JoinResult() // stored session invalid
    object NetworkError  : JoinResult()   // HTTP / serialisation failure
}

/**
 * WorkflowShareRepository — manages join-code-based workflow sharing.
 */
object WorkflowShareRepository {

    const val MAX_MEMBERS = 3
    private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous chars

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun myUserId() = LocalStorage.loadString("appwrite_user_id") ?: ""
    private fun myUserName() = LocalStorage.loadString("user_name") ?: "Unknown"
    private fun myUserEmail() = LocalStorage.loadString("user_email") ?: ""

    // ─── Join code generation ─────────────────────────────────────────────────

    fun generateJoinCode(): String = (1..5).map { CHARS.random() }.joinToString("")

    // ─── Share CRUD ───────────────────────────────────────────────────────────

    suspend fun getOrCreateShare(
        workflowId: String,
        workflowName: String = "Shared Workflow",
        workflow: Workflow? = null
    ): WorkflowShareInfo? =
        withContext(Dispatchers.IO) {
            val uid = myUserId()

            try {
                // ── Step 1: Check cache ───────────────────────────────────────
                val cached = getCachedShare(workflowId)

                // ── Step 2: Fetch workflow document directly from Supabase ────
                var existingWf: SupabaseWorkflow? = null
                try {
                    val response = supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                        .select {
                            filter {
                                eq("id", workflowId)
                            }
                        }
                    existingWf = response.decodeSingleOrNull<SupabaseWorkflow>()
                } catch (e: Exception) {
                    println("[Share] GET workflow $workflowId failed: ${e.message}")
                }

                val wfName = existingWf?.name ?: workflowName
                val existingOwner = existingWf?.ownerUserId?.takeIf { it.isNotBlank() } ?: cached?.ownerUserId?.takeIf { it.isNotBlank() } ?: uid

                // If Supabase already has a join_code on the server, return it directly
                if (existingWf != null && existingWf.joinCode.isNotBlank()) {
                    val members = try { json.decodeFromString<List<WorkflowMember>>(existingWf.membersJson) } catch (e: Exception) { emptyList() }
                    val finalMembers = if (members.isNotEmpty()) members else {
                        val me = WorkflowMember(userId = uid, userName = myUserName(), userEmail = myUserEmail(), joinedAt = now())
                        listOf(me)
                    }
                    val info = WorkflowShareInfo(
                        workflowId   = workflowId,
                        ownerUserId  = existingOwner,
                        joinCode     = existingWf.joinCode,
                        workflowName = wfName,
                        members      = finalMembers
                    )
                    cacheShare(workflowId, info)
                    return@withContext info
                }

                // ── Step 3: No valid code on server — generate code & ALWAYS save to Supabase ─
                val code = cached?.joinCode?.takeIf { it.isNotBlank() }
                    ?: workflow?.joinCode?.takeIf { it.isNotBlank() }
                    ?: ensureUniqueCode()
                val existingMembers = existingWf?.membersJson ?: cached?.let { c -> json.encodeToString(c.members) } ?: workflow?.membersJson ?: ""
                val membersList = try { json.decodeFromString<List<WorkflowMember>>(existingMembers) } catch (e: Exception) { emptyList() }
                val finalMembers = if (membersList.isNotEmpty()) membersList else {
                    val me = WorkflowMember(userId = uid, userName = myUserName(), userEmail = myUserEmail(), joinedAt = now())
                    listOf(me)
                }
                val membersEncoded = json.encodeToString(finalMembers)

                println("[Share] Persisting join_code '$code' for workflow $workflowId to Supabase...")

                if (uid.isNotEmpty()) {
                    if (existingWf != null) {
                        // Document exists in DB — update join_code and members
                        supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                            .update({
                                set("join_code", code)
                                set("owner_user_id", existingOwner)
                                set("members_json", membersEncoded)
                            }) {
                                filter {
                                    eq("id", workflowId)
                                }
                            }
                        println("[Share] ✅ Updated existing workflow $workflowId in Supabase with join_code '$code'")
                    } else if (workflow != null) {
                        val dbWf = SupabaseWorkflow(
                            id = workflowId,
                            userId = existingOwner,
                            name = workflow.name.ifEmpty { workflowName },
                            lastEdited = workflow.lastEdited.takeIf { it > 0 } ?: now(),
                            createdAtDate = workflow.createdAtDate,
                            cardColorHex = workflow.cardColorHex,
                            nodesJson = json.encodeToString(workflow.nodes.toList()),
                            edgesJson = json.encodeToString(workflow.edges.toList()),
                            joinCode = code,
                            ownerUserId = existingOwner,
                            membersJson = membersEncoded
                        )
                        supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS).upsert(dbWf)
                        println("[Share] ✅ Upserted new workflow $workflowId to Supabase with join_code '$code'")
                    } else {
                        supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                            .update({
                                set("join_code", code)
                                set("owner_user_id", existingOwner)
                                set("members_json", membersEncoded)
                            }) {
                                filter {
                                    eq("id", workflowId)
                                }
                            }
                    }
                } else {
                    println("[Share] ⚠️ Not logged in — cannot store share code to Supabase.")
                    return@withContext null
                }

                val info = WorkflowShareInfo(
                    workflowId   = workflowId,
                    ownerUserId  = existingOwner,
                    joinCode     = code,
                    workflowName = wfName,
                    members      = finalMembers
                )
                cacheShare(workflowId, info)
                return@withContext info

            } catch (e: Exception) {
                println("[Share] ❌ getOrCreateShare exception: ${e.message}")
                e.printStackTrace()
                val cached = getCachedShare(workflowId)
                if (cached != null) return@withContext cached
                null
            }
        }

    fun getCachedSharePublic(workflowId: String): WorkflowShareInfo? = getCachedShare(workflowId)

    private fun getCachedShare(workflowId: String): WorkflowShareInfo? {
        return try {
            val raw = LocalStorage.loadString("share_cache_$workflowId") ?: return null
            json.decodeFromString<WorkflowShareInfo>(raw)
        } catch (e: Exception) { null }
    }

    private fun cacheShare(workflowId: String, info: WorkflowShareInfo) {
        try {
            LocalStorage.saveString("share_cache_$workflowId", json.encodeToString(info))
        } catch (e: Exception) { /* ignore */ }
    }

    private suspend fun findShareDocByCode(code: String): SupabaseWorkflow? {
        return try {
            val normalised = code.trim().uppercase()
            println("[Share] Searching Supabase for join_code = '$normalised'")
            val response = supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                .select {
                    filter {
                        eq("join_code", normalised)
                    }
                }
            val doc = response.decodeSingleOrNull<SupabaseWorkflow>()
            println("[Share] findShareDocByCode for '$normalised' returned: ${doc?.id} (name=${doc?.name})")
            doc
        } catch (e: Exception) {
            println("[Share] ❌ findShareDocByCode exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private suspend fun addUserToShare(workflowId: String, share: WorkflowShareInfo, uid: String): WorkflowShareInfo? {
        return try {
            val updatedMembers = share.members + WorkflowMember(uid, myUserName(), myUserEmail(), now())
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                .update({
                    set("members_json", json.encodeToString(updatedMembers))
                }) {
                    filter {
                        eq("id", workflowId)
                    }
                }
            share.copy(members = updatedMembers)
        } catch (e: Exception) {
            println("[Share] addUserToShare exception: ${e.message}")
            null
        }
    }

    suspend fun removeMemberFromShare(workflowId: String, userId: String) = withContext(Dispatchers.IO) {
        try {
            if (userId.isEmpty()) return@withContext
            val share = fetchShare(workflowId) ?: return@withContext
            val leavingMember = share.members.find { it.userId == userId }
            val leavingName = leavingMember?.userName ?: myUserName().ifEmpty { "A member" }
            val updatedMembers = share.members.filterNot { it.userId == userId }
            if (updatedMembers.size == share.members.size) return@withContext
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                .update({
                    set("members_json", json.encodeToString(updatedMembers))
                }) {
                    filter {
                        eq("id", workflowId)
                    }
                }
            println("[Share] removeMemberFromShare updated workflow $workflowId")
            notifyMembersUserLeft(share, leavingName, userId)
        } catch (e: Exception) {
            println("[Share] removeMemberFromShare exception: ${e.message}")
        }
    }

    suspend fun joinByCode(code: String): JoinResult = withContext(Dispatchers.IO) {
        try {
            val uid = myUserId()
            if (uid.isEmpty()) {
                println("[Share] joinByCode — not logged in")
                return@withContext JoinResult.NetworkError
            }

            val normalised = code.trim().uppercase()

            // ── Step 1: Try secure RPC function (runs with SECURITY DEFINER in Postgres) ──
            try {
                val rpcResponse = supabaseClient.postgrest.rpc(
                    function = "join_workflow_by_code",
                    parameters = buildJsonObject {
                        put("p_code", normalised)
                        put("p_user_id", uid)
                        put("p_user_name", myUserName())
                        put("p_user_email", myUserEmail())
                    }
                )
                val rawData = rpcResponse.data
                println("[Share] joinByCode RPC raw response: $rawData")
                
                val parsedElement = try { json.parseToJsonElement(rawData) } catch (e: Exception) { null }
                val jsonResult = when (parsedElement) {
                    is JsonObject -> parsedElement
                    is JsonPrimitive -> if (parsedElement.isString) try { json.parseToJsonElement(parsedElement.content).jsonObject } catch (e: Exception) { null } else null
                    else -> null
                }
                
                val status = jsonResult?.get("status")?.jsonPrimitive?.contentOrNull
                println("[Share] joinByCode RPC parsed status: $status")
                
                when (status) {
                    "success" -> {
                        val wfDoc = json.decodeFromJsonElement<SupabaseWorkflow>(jsonResult["workflow"]!!)
                        val members = try { json.decodeFromString<List<WorkflowMember>>(wfDoc.membersJson) } catch (e: Exception) { emptyList() }
                        val info = WorkflowShareInfo(wfDoc.id, wfDoc.ownerUserId, wfDoc.joinCode, wfDoc.name, members)
                        cacheShare(info.workflowId, info)
                        notifyMembersUserJoined(info, myUserName().ifEmpty { "A new member" }, uid)
                        return@withContext JoinResult.Success(info)
                    }
                    "already_member" -> {
                        val wfDoc = json.decodeFromJsonElement<SupabaseWorkflow>(jsonResult["workflow"]!!)
                        val members = try { json.decodeFromString<List<WorkflowMember>>(wfDoc.membersJson) } catch (e: Exception) { emptyList() }
                        val info = WorkflowShareInfo(wfDoc.id, wfDoc.ownerUserId, wfDoc.joinCode, wfDoc.name, members)
                        cacheShare(info.workflowId, info)
                        return@withContext JoinResult.AlreadyMember(info)
                    }
                    "seats_full" -> return@withContext JoinResult.SeatsFull
                    "not_found" -> return@withContext JoinResult.InvalidCode
                }
            } catch (rpcEx: Exception) {
                println("[Share] RPC join_workflow_by_code failed (${rpcEx.message}), falling back to direct query...")
            }

            // ── Step 2: Fallback to direct table queries ──
            val shareDoc = findShareDocByCode(normalised)
            if (shareDoc == null) {
                println("[Share] joinByCode — code '${code.uppercase()}' not found")
                return@withContext JoinResult.InvalidCode
            }

            val workflowDocId = shareDoc.id
            val members = try { json.decodeFromString<List<WorkflowMember>>(shareDoc.membersJson) } catch (e: Exception) { emptyList() }
            val share = WorkflowShareInfo(
                workflowId = shareDoc.id,
                ownerUserId = shareDoc.ownerUserId,
                joinCode = shareDoc.joinCode,
                workflowName = shareDoc.name,
                members = members
            )

            // Step 2 — guard checks
            if (share.ownerUserId == uid) {
                println("[Share] joinByCode — caller is the owner")
                return@withContext JoinResult.AlreadyMember(share)
            }
            if (share.members.any { it.userId == uid }) {
                println("[Share] joinByCode — already a member")
                return@withContext JoinResult.AlreadyMember(share)
            }
            if (share.members.size >= MAX_MEMBERS) {
                println("[Share] joinByCode — seats full (${share.members.size}/$MAX_MEMBERS)")
                return@withContext JoinResult.SeatsFull
            }

            // Step 3 — add this user
            val updated = addUserToShare(workflowDocId, share, uid)
                ?: return@withContext JoinResult.NetworkError

            cacheShare(share.workflowId, updated)
            notifyMembersUserJoined(updated, myUserName().ifEmpty { "A new member" }, uid)
            println("[Share] joinByCode — success! joined '${share.workflowId}'")
            JoinResult.Success(updated)

        } catch (e: Exception) {
            println("[Share] joinByCode exception: ${e.message}")
            JoinResult.NetworkError
        }
    }

    /**
     * Send a OneSignal push notification to all existing workflow members (and owner)
     * notifying them that a new member has joined via join code.
     */
    fun notifyMembersUserJoined(share: WorkflowShareInfo, joinedUserName: String, joinedUserId: String) {
        val recipientUserIds = (share.members.map { it.userId } + listOf(share.ownerUserId))
            .filter { it.isNotBlank() && it != joinedUserId }
            .distinct()

        if (recipientUserIds.isEmpty()) return

        println("[OneSignal-Join] 🔔 Notifying workflow members that $joinedUserName joined workflow '${share.workflowName}'")
        println("[OneSignal-Join] Target Recipient External IDs: $recipientUserIds")

        CoroutineScope(Dispatchers.Default).launch {
            val client = io.ktor.client.HttpClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                }
            }
            try {
                val externalIdsJson = recipientUserIds.joinToString(",") { "\"$it\"" }
                val workflowTitle = share.workflowName.ifEmpty { "Shared Workflow" }
                val targetJson = """
                {
                  "app_id": "7090ae90-1a87-4702-8cfd-2694e44301d9",
                  "target_channel": "push",
                  "include_aliases": {
                    "external_id": [$externalIdsJson]
                  },
                  "headings": {"en": "$workflowTitle"},
                  "contents": {"en": "$joinedUserName joined the workflow! 🚀"},
                  "data": {
                    "workflow_id": "${share.workflowId}",
                    "type": "member_joined"
                  }
                }
                """.trimIndent()
                println("[OneSignal-Join] 📤 Sending request payload to OneSignal:")
                println(targetJson)
                val response = client.post("https://onesignal.com/api/v1/notifications") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    header("Authorization", "Key ${Env.ONESIGNAL_REST_API_KEY}")
                    setBody(targetJson)
                }
                println("[OneSignal-Join] 📥 Response Status: ${response.status} Body: ${response.bodyAsText()}")
            } catch (e: Exception) {
                println("[OneSignal-Join] ❌ Error sending join notification: ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    /**
     * Send a OneSignal push notification to all remaining workflow members (and owner)
     * notifying them that a collaborator has left the shared workflow.
     */
    fun notifyMembersUserLeft(share: WorkflowShareInfo, leftUserName: String, leftUserId: String) {
        val recipientUserIds = (share.members.map { it.userId } + listOf(share.ownerUserId))
            .filter { it.isNotBlank() && it != leftUserId }
            .distinct()

        if (recipientUserIds.isEmpty()) return

        println("[OneSignal-Leave] 🔔 Notifying workflow members that $leftUserName left workflow '${share.workflowName}'")
        println("[OneSignal-Leave] Target Recipient External IDs: $recipientUserIds")

        CoroutineScope(Dispatchers.Default).launch {
            val client = io.ktor.client.HttpClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                }
            }
            try {
                val externalIdsJson = recipientUserIds.joinToString(",") { "\"$it\"" }
                val workflowTitle = share.workflowName.ifEmpty { "Shared Workflow" }
                val targetJson = """
                {
                  "app_id": "7090ae90-1a87-4702-8cfd-2694e44301d9",
                  "target_channel": "push",
                  "include_aliases": {
                    "external_id": [$externalIdsJson]
                  },
                  "headings": {"en": "$workflowTitle"},
                  "contents": {"en": "$leftUserName left the workflow."},
                  "data": {
                    "workflow_id": "${share.workflowId}",
                    "type": "member_left"
                  }
                }
                """.trimIndent()
                println("[OneSignal-Leave] 📤 Sending request payload to OneSignal:")
                println(targetJson)
                val response = client.post("https://onesignal.com/api/v1/notifications") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    header("Authorization", "Key ${Env.ONESIGNAL_REST_API_KEY}")
                    setBody(targetJson)
                }
                println("[OneSignal-Leave] 📥 Response Status: ${response.status} Body: ${response.bodyAsText()}")
            } catch (e: Exception) {
                println("[OneSignal-Leave] ❌ Error sending leave notification: ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    suspend fun fetchShare(workflowId: String): WorkflowShareInfo? = withContext(Dispatchers.IO) {
        try {
            val response = supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                .select {
                    filter {
                        eq("id", workflowId)
                    }
                }
            val shareDoc = response.decodeSingleOrNull<SupabaseWorkflow>() ?: return@withContext null
            val members = try { json.decodeFromString<List<WorkflowMember>>(shareDoc.membersJson) } catch (e: Exception) { emptyList() }
            WorkflowShareInfo(
                workflowId = shareDoc.id,
                ownerUserId = shareDoc.ownerUserId,
                joinCode = shareDoc.joinCode,
                workflowName = shareDoc.name,
                members = members
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun upsertPresence(workflowId: String) = withContext(Dispatchers.IO) {
        try {
            val uid = myUserId().ifEmpty { return@withContext }
            val docId = "pf_${uid.take(8)}_$workflowId"
            val nowMs = now()
            val pres = SupabasePresence(
                id = docId,
                userId = uid,
                userName = myUserName(),
                workflowId = workflowId,
                updatedAt = nowMs
            )
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE).upsert(pres)
        } catch (e: Exception) {
            if (!isNetworkError(e)) println("[Presence] upsertPresence error: ${e.message}")
        }
    }

    suspend fun clearPresence(workflowId: String) = withContext(Dispatchers.IO) {
        try {
            val uid = myUserId().ifEmpty { return@withContext }
            val docId = "pf_${uid.take(8)}_$workflowId"
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE)
                .delete {
                    filter {
                        eq("id", docId)
                    }
                }
        } catch (e: Exception) {
            if (!isNetworkError(e)) println("[Presence] clearPresence error: ${e.message}")
        }
    }

    suspend fun fetchActivePresenceUsers(workflowId: String): List<PresenceUser> =
        withContext(Dispatchers.IO) {
            try {
                val share = fetchShare(workflowId) ?: return@withContext emptyList()
                val nowMs = now()
                val thirtySecondsAgo = nowMs - 30_000
                val list = supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE)
                    .select {
                        filter {
                            eq("workflow_id", workflowId)
                            gt("updated_at", thirtySecondsAgo)
                        }
                    }.decodeList<SupabasePresence>()
                val memberMap = share.members.associateBy { it.userId }
                list.map { doc ->
                    val uid = doc.userId
                    val member = memberMap[uid]
                    val userName = member?.userName ?: doc.userName
                    PresenceUser(uid, userName)
                }.distinctBy { it.userId }
            } catch (e: Exception) {
                if (!isNetworkError(e)) println("[Presence] fetchActivePresenceUsers error: ${e.message}")
                emptyList()
            }
        }

    suspend fun fetchActivePresences(workflowId: String): List<String> =
        fetchActivePresenceUsers(workflowId).map { it.userId }

    suspend fun upsertNodePresence(
        workflowId: String,
        nodeId: String?,
        action: NodeAction,
        cursorX: Float = 0f,
        cursorY: Float = 0f
    ) = withContext(Dispatchers.IO) {
        try {
            val uid  = myUserId().ifEmpty { return@withContext }
            val name = myUserName()
            val docId = "pfn_${uid.take(8)}_$workflowId"
            val nowMs = now()
            val pres = SupabasePresence(
                id = docId,
                userId = uid,
                userName = name,
                workflowId = workflowId,
                updatedAt = nowMs,
                action = action.name,
                cursorX = cursorX,
                cursorY = cursorY,
                nodeId = nodeId ?: ""
            )
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE).upsert(pres)
        } catch (e: Exception) {
            if (!isNetworkError(e)) println("[Presence] upsertNodePresence error: ${e.message}")
        }
    }

    suspend fun clearNodePresence(workflowId: String) = withContext(Dispatchers.IO) {
        try {
            val uid = myUserId().ifEmpty { return@withContext }
            val docId = "pfn_${uid.take(8)}_$workflowId"
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE)
                .delete {
                    filter {
                        eq("id", docId)
                    }
                }
        } catch (e: Exception) {
            if (!isNetworkError(e)) println("[Presence] clearNodePresence error: ${e.message}")
        }
    }

    fun userColorFor(userId: String): Color = getMemberColor(userId)

    suspend fun fetchMemberPresences(workflowId: String): List<app.ak25.pocketflow.services.UserPresenceState> {
        val activeUsers = fetchActivePresenceUsers(workflowId)
        return activeUsers.map {
            app.ak25.pocketflow.services.UserPresenceState(
                userId = it.userId,
                userName = it.userName,
                color = getMemberColor(it.userId),
                isActive = true
            )
        }
    }

    suspend fun fetchNodePresences(workflowId: String): List<NodePresenceState> = withContext(Dispatchers.IO) {
        try {
            val response = supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE)
                .select {
                    filter {
                        eq("workflow_id", workflowId)
                    }
                }
            val list = response.decodeList<SupabasePresence>()
            val threshold = now() - PRESENCE_TTL_MS
            list.filter { it.updatedAt >= threshold && it.nodeId.isNotEmpty() }.map {
                val actionEnum = try {
                    NodeAction.valueOf(it.action)
                } catch (e: Exception) {
                    NodeAction.VIEWING
                }
                NodePresenceState(
                    userId = it.userId,
                    userName = it.userName,
                    nodeId = it.nodeId,
                    action = actionEnum,
                    color = getMemberColor(it.userId),
                    cursorX = it.cursorX,
                    cursorY = it.cursorY
                )
            }
        } catch (e: Exception) {
            if (!isNetworkError(e)) println("[Presence] fetchNodePresences error: ${e.message}")
            emptyList()
        }
    }

    fun getMemberColor(userId: String): Color {
        val palette = listOf(
            Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
            Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
            Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
            Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F),
            Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFA1887F)
        )
        val idx = kotlin.math.abs(userId.hashCode()) % palette.size
        return palette[idx]
    }

    private fun now() = getCurrentTimeMillis()

    private fun isNetworkError(e: Exception): Boolean {
        val msg = e.message ?: ""
        return msg.contains("offline", ignoreCase = true)
            || msg.contains("internet", ignoreCase = true)
            || msg.contains("network", ignoreCase = true)
            || msg.contains("unreachable", ignoreCase = true)
            || msg.contains("connection", ignoreCase = true)
            || msg.contains("NSURL", ignoreCase = true)
            || msg.contains("NSPOSIXErrorDomain", ignoreCase = true)
            || msg.contains("Code=-1009", ignoreCase = true)
            || msg.contains("Code=-1004", ignoreCase = true)
    }

    private suspend fun ensureUniqueCode(): String {
        repeat(10) {
            val code = generateJoinCode()
            val existing = findShareDocByCode(code)
            if (existing == null) return code
        }
        return generateJoinCode() // fallback
    }
}
