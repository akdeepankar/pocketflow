package app.ak25.pocketflow.domain

import app.ak25.pocketflow.models.*
import app.ak25.pocketflow.services.SupabaseRepository
import app.ak25.pocketflow.services.SupabaseRealtimeService
import app.ak25.pocketflow.services.WorkflowShareRepository
import app.ak25.pocketflow.services.NodeAction
import app.ak25.pocketflow.services.UserPresenceState
import app.ak25.pocketflow.utils.IdGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import app.ak25.pocketflow.utils.getCurrentTimeMillis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import app.ak25.pocketflow.services.Env

/**
 * WorkflowController — single source of truth for workflow state.
 *
 * Storage strategy:
 *  • Every mutation writes to LocalStorage immediately (fast, offline-safe).
 *  • Every mutation also fires an async Appwrite upsert (cloud sync).
 *  • On init: loads from Appwrite first (latest data), falls back to LocalStorage.
 *  • AppwriteRealtimeService pushes external changes back into _workflows (collaboration).
 */
class WorkflowController {

    private val _workflows = MutableStateFlow<List<Workflow>>(emptyList())
    val workflows: StateFlow<List<Workflow>> = _workflows.asStateFlow()

    private val _currentWorkflow = MutableStateFlow<Workflow?>(null)
    val currentWorkflow: StateFlow<Workflow?> = _currentWorkflow.asStateFlow()

    /** Active collaborators on the current workflow (from Realtime presence). */
    private val _collaborators = MutableStateFlow<List<String>>(emptyList())
    val collaborators: StateFlow<List<String>> = _collaborators.asStateFlow()

    /**
     * Real-time node-level presence — who is holding/dragging which node.
     * Keyed by userId. Updated by polling Appwrite Presences every 3s.
     */
    private val _nodePresences = MutableStateFlow<List<app.ak25.pocketflow.services.NodePresenceState>>(emptyList())
    val nodePresences: StateFlow<List<app.ak25.pocketflow.services.NodePresenceState>> = _nodePresences.asStateFlow()

    // ─── Incoming note notifications (collaboration) ─────────────────────────
    // Set when a NEW node note from another member arrives for the currently
    // open workflow. Consumed by the editor UI to show a transient snackbar.
    private val _incomingNote = MutableStateFlow<app.ak25.pocketflow.models.NodeNote?>(null)
    val incomingNote: StateFlow<app.ak25.pocketflow.models.NodeNote?> = _incomingNote.asStateFlow()

    private val seenNoteKeys = mutableSetOf<String>()   // "workflowId|noteId" — already seen
    private var seenNotesWorkflowId: String? = null

    /**
     * Member presence list for the editor header avatar row.
     * Updated every 5s. Each entry has isActive flag for green ring.
     */
    private val _memberPresences = MutableStateFlow<List<UserPresenceState>>(emptyList())
    val memberPresences: StateFlow<List<UserPresenceState>> = _memberPresences.asStateFlow()

    data class FocusedNodeEvent(
        val nodeId: String,
        val isNote: Boolean = false
    )
    private val _focusedNode = MutableStateFlow<FocusedNodeEvent?>(null)
    val focusedNode: StateFlow<FocusedNodeEvent?> = _focusedNode.asStateFlow()

    fun focusNode(nodeId: String, isNote: Boolean = false) {
        _focusedNode.value = FocusedNodeEvent(nodeId, isNote)
    }

    fun clearFocusedNode() {
        _focusedNode.value = null
    }

    private var currentLocalAction = NodeAction.VIEWING
    private var currentLocalNodeId = ""

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val store = app.ak25.pocketflow.storage.LocalStorage

    /** Guest users keep workflows local-only (no Appwrite sync). */
    private val isGuest: Boolean
        get() = app.ak25.pocketflow.storage.LocalStorage.loadString("is_guest") == "true"

    // Debounce job for position/drag updates — avoids spamming Appwrite on every frame
    private var positionSyncJob: Job? = null
    // Polling job for node-level presences
    private var presencePollJob: Job? = null

    init {
        // 1. Load from LocalStorage immediately (fast, offline-safe)
        loadFromLocal()

        // 2. Then refresh from Appwrite in the background (skipped for guests — local-only)
        scope.launch {
            refreshFromCloud()
        }

        // 3. Start Realtime listener for collaboration (not applicable for guests)
        if (!isGuest) {
            SupabaseRealtimeService.connect()
        }
        scope.launch {
            SupabaseRealtimeService.events.collect { event ->
                handleRealtimeEvent(event)
            }
        }
    }

    // ─── Initialisation helpers ───────────────────────────────────────────────

    private fun loadFromLocal() {
        try {
            val jsonStr = app.ak25.pocketflow.storage.LocalStorage.loadString("workflows")
            if (jsonStr != null) {
                _workflows.value = json.decodeFromString<List<Workflow>>(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Call this after login to pull workflows from Appwrite and push any local-only ones up. */
    fun syncWithCloud() {
        scope.launch { refreshFromCloud() }
    }

    /**
     * Wipe the local workflow cache (in-memory + persisted). Called on sign-out
     * so a subsequent guest session never sees the previous user's workflows.
     * The user's workflows remain in Appwrite and are re-pulled on next login.
     */
    fun clearLocalWorkflows() {
        _workflows.value = emptyList()
        _currentWorkflow.value = null
        saveToLocal(emptyList())
        println("[WorkflowController] Cleared local workflows (sign-out)")
    }

    private suspend fun refreshFromCloud() {
        // Guests keep workflows local-only — never pull from or push to Appwrite.
        if (isGuest) return

        // Retry the fetch briefly: right after login the user's JWT/session may
        // still be persisting, and fetchWorkflows returns null without it.
        var cloudWorkflows: List<Workflow>? = null
        repeat(6) { attempt ->
            cloudWorkflows = SupabaseRepository.fetchWorkflows()
            if (cloudWorkflows != null) return@repeat
            println("[WorkflowController] Cloud fetch failed (attempt ${attempt + 1}) — retrying in 1s...")
            delay(1_000)
        }
        val cloud = cloudWorkflows ?: return

        if (cloud.isEmpty() && _workflows.value.isNotEmpty()) {
            // Cloud is empty but local has data — push local to cloud
            _workflows.value.forEach { wf ->
                scope.launch { pushToCloud(wf) }
            }
            return
        }
        // Merge: cloud wins if lastEdited is newer
        val cloudIds = cloud.map { it.id }.toSet()
        val localMap = _workflows.value.associateBy { it.id }.toMutableMap()
        cloud.forEach { cloudWf ->
            val local = localMap[cloudWf.id]
            if (local == null || cloudWf.lastEdited >= local.lastEdited) {
                localMap[cloudWf.id] = cloudWf
            }
        }
        val merged = localMap.values.toList()
        _workflows.value = merged
        // Persist merged result locally
        saveToLocal(merged)
        // Push any local-only workflows that aren't in cloud yet (includes
        // workflows created during a guest session) — owned by the logged-in user.
        merged.filter { it.id !in cloudIds }.forEach { wf ->
            scope.launch {
                println("[WorkflowController] Pushing local-only workflow to cloud: ${wf.id} ('${wf.name}')")
                pushToCloud(wf)
            }
        }
    }

    private suspend fun refreshCurrentWorkflowFromCloud() {
        val current = _currentWorkflow.value ?: return
        if (isGuest) return
        val server = SupabaseRepository.fetchWorkflowById(current.id) ?: return
        val local = _currentWorkflow.value ?: return
        // Only apply when the server copy is strictly newer — this prevents a stale
        // poll read from clobbering edits made locally in the last few seconds.
        if (server.lastEdited > local.lastEdited || server.membersJson != local.membersJson) {
            val list = _workflows.value.toMutableList()
            val idx = list.indexOfFirst { it.id == server.id }
            if (idx != -1) list[idx] = server else list.add(server)
            _workflows.value = list
            saveToLocal(list)
            _currentWorkflow.value = server
            detectNewNotes(server)
            println("[WC] Applied cloud workflow refresh: ${server.id} (lastEdited=${server.lastEdited})")
        }
    }

    /**
     * Upsert a workflow to Appwrite owned by the current user.
     * Waits until the logged-in user's credentials are persisted (they may not
     * be available the instant after login), then pushes with their userId.
     */
    private suspend fun pushToCloud(wf: Workflow) {
        val store = app.ak25.pocketflow.storage.LocalStorage
        repeat(6) { attempt ->
            val uid = store.loadString("appwrite_user_id").orEmpty()
            val jwt = store.loadString("appwrite_jwt").orEmpty()
            val session = store.loadString("appwrite_session_id").orEmpty()
            if (uid.isNotEmpty() && (jwt.isNotEmpty() || session.isNotEmpty())) {
                SupabaseRepository.upsertWorkflow(wf.copy(ownerUserId = uid))
                return
            }
            if (attempt == 5) {
                // Last best-effort attempt — upsertWorkflow re-checks auth and skips if missing.
                SupabaseRepository.upsertWorkflow(wf.copy(ownerUserId = uid))
                return
            }
            delay(1_500)
        }
    }

    /** Called by the editor to clear the last shown incoming-note notification. */
    fun consumeIncomingNote() {
        _incomingNote.value = null
    }

    /**
     * Diff the notes in an incoming workflow copy and emit a notification for any
     * note we haven't seen yet from another member. Seeded on first sight of a
     * workflow so pre-existing notes never trigger a notification.
     */
    private fun detectNewNotes(workflow: Workflow) {
        if (seenNotesWorkflowId != workflow.id) {
            seenNotesWorkflowId = workflow.id
            seenNoteKeys.clear()
            workflow.nodes.forEach { node -> node.notes.forEach { seenNoteKeys.add("${workflow.id}|${it.id}") } }
            return
        }
        val myUid = app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id").orEmpty()
        workflow.nodes.forEach { node ->
            node.notes.forEach { note ->
                val key = "${workflow.id}|${note.id}"
                if (seenNoteKeys.add(key) && note.authorUserId.isNotEmpty() && note.authorUserId != myUid) {
                    _incomingNote.value = note
                }
            }
        }
    }

    private fun handleRealtimeEvent(event: SupabaseRealtimeService.RealtimeEvent) {
        println("[WorkflowController] Received realtime event: $event")
        when (event) {
            is SupabaseRealtimeService.RealtimeEvent.WorkflowUpserted -> {
                val current = _workflows.value.toMutableList()
                val idx = current.indexOfFirst { it.id == event.workflow.id }
                if (idx != -1) {
                    if (event.workflow.lastEdited >= current[idx].lastEdited) {
                        current[idx] = event.workflow
                        _workflows.value = current
                        saveToLocal(current)
                        if (_currentWorkflow.value?.id == event.workflow.id) {
                            _currentWorkflow.value = event.workflow
                            detectNewNotes(event.workflow)
                        }
                    }
                } else {
                    current.add(event.workflow)
                    _workflows.value = current
                    saveToLocal(current)
                }
            }
            is SupabaseRealtimeService.RealtimeEvent.WorkflowDeleted -> {
                val current = _workflows.value.filter { it.id != event.workflowId }
                _workflows.value = current
                saveToLocal(current)
                if (_currentWorkflow.value?.id == event.workflowId) {
                    _currentWorkflow.value = null
                }
            }
            // Legacy presence event handlers removed to prevent overwriting WebSocket realtime state.
            else -> {}
        }
    }

    // ─── Core persistence ─────────────────────────────────────────────────────

    private fun updateCurrent(workflow: Workflow) {
        val now = getCurrentTimeMillis()
        val updated = workflow.copy(lastEdited = now)
        _currentWorkflow.value = updated
        syncAndSave()
    }

    private fun syncAndSave() {
        val current = _currentWorkflow.value ?: return
        val list = _workflows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx != -1) list[idx] = current else list.add(current)
        _workflows.value = list
        saveToLocal(list)
        // Immediate cloud sync (for add/delete/connect — not position drags)
        if (!isGuest) {
            scope.launch { SupabaseRepository.upsertWorkflow(current) }
        }
    }

    /**
     * Debounced cloud sync — used for position updates during drag.
     * Waits 600ms after the last call before writing to Appwrite,
     * so dragging a node doesn't spam hundreds of API calls.
     */
    private fun syncLocalAndDebouncedCloud() {
        val current = _currentWorkflow.value ?: return
        val list = _workflows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx != -1) list[idx] = current else list.add(current)
        _workflows.value = list
        saveToLocal(list)
        // Cancel previous pending sync and schedule a fresh one
        positionSyncJob?.cancel()
        positionSyncJob = scope.launch {
            delay(600)
            if (!isGuest) {
                SupabaseRepository.upsertWorkflow(current)
            }
        }
    }

    private fun saveToLocal(list: List<Workflow>) {
        try {
            app.ak25.pocketflow.storage.LocalStorage.saveString(
                "workflows",
                json.encodeToString(list)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── Public API (unchanged surface, now also syncs to Appwrite) ───────────

    fun createWorkflow(name: String) {
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val userName = store.loadString("user_name").orEmpty()
        val email = store.loadString("user_email").orEmpty()
        val code = app.ak25.pocketflow.services.WorkflowShareRepository.generateJoinCode()
        
        val me = app.ak25.pocketflow.services.WorkflowMember(
            userId = uid,
            userName = userName,
            userEmail = email,
            joinedAt = getCurrentTimeMillis()
        )
        val initialMembersEncoded = json.encodeToString(listOf(me))

        val newWorkflow = Workflow(
            id = IdGenerator.generate(),
            name = name,
            lastEdited = getCurrentTimeMillis(),
            createdAtDate = app.ak25.pocketflow.utils.getCurrentDate(),
            joinCode = code,
            ownerUserId = uid,
            membersJson = initialMembersEncoded
        )
        // Add to list FIRST so syncAndSave sees it
        val updated = _workflows.value + newWorkflow
        _workflows.value = updated
        saveToLocal(updated)
        _currentWorkflow.value = newWorkflow
        // Explicit cloud write — don't rely solely on syncAndSave timing
        if (!isGuest) {
            scope.launch {
                println("[WorkflowController] Creating workflow in Supabase: ${newWorkflow.id} ('${newWorkflow.name}')")
                SupabaseRepository.upsertWorkflow(newWorkflow)
            }
        }
        app.ak25.pocketflow.storage.ActivityTracker.log(
            type = ActivityType.CREATE_WORKFLOW,
            title = "Workflow Created",
            details = "Created workflow named '$name'",
            workflowName = name
        )
    }

    fun loadWorkflow(workflowId: String) {
        val existing = _workflows.value.find { it.id == workflowId }
        if (existing != null) {
            _currentWorkflow.value = existing
        } else if (!isGuest) {
            scope.launch {
                val server = SupabaseRepository.fetchWorkflowById(workflowId)
                if (server != null) {
                    val list = _workflows.value.toMutableList()
                    val idx = list.indexOfFirst { it.id == server.id }
                    if (idx != -1) list[idx] = server else list.add(server)
                    _workflows.value = list
                    saveToLocal(list)
                    _currentWorkflow.value = server
                }
            }
        }
        // Reset note-notification state for the newly opened workflow
        _incomingNote.value = null
        seenNotesWorkflowId = null
        seenNoteKeys.clear()

        // Guests keep workflows local-only — no presence upserts, fetches or polling.
        if (isGuest) return

        // ── Immediately seed MY OWN avatar (zero-network, instant) ───────────
        val myUid  = app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id") ?: ""
        val myName = app.ak25.pocketflow.storage.LocalStorage.loadString("user_name") ?: "Me"
        if (myUid.isNotEmpty() && _memberPresences.value.none { it.userId == myUid }) {
            _memberPresences.value = listOf(
                UserPresenceState(
                    userId   = myUid,
                    userName = myName,
                    color    = WorkflowShareRepository.userColorFor(myUid),
                    isActive = true
                )
            )
        }

        // ── Cancel previous loop before starting fresh ────────────────────────
        presencePollJob?.cancel()

        presencePollJob = scope.launch {
            try {
                SupabaseRealtimeService.joinPresenceChannel(workflowId, myUid, myName) { members, nodes ->
                    _memberPresences.value = members
                    _nodePresences.value = nodes
                }
            } catch (e: Exception) {
                println("[WC] failed to join presence channel: ${e.message}")
            }

            // Fallback sync loop for workflow DB edits + Realtime Presence Heartbeat
            while (true) {
                delay(10_000)
                try { 
                    refreshCurrentWorkflowFromCloud() 
                    if (myUid.isNotEmpty()) {
                        SupabaseRealtimeService.refreshOnlinePresence(
                            userId = myUid,
                            userName = myName,
                            action = currentLocalAction,
                            nodeId = currentLocalNodeId
                        )
                        // Also update database presence table for home list polling
                        WorkflowShareRepository.upsertPresence(workflowId)
                    }
                }
                catch (e: Exception) { /* silent */ }
            }
        }
    }

    fun refreshShareInfo() {
        val wfId = _currentWorkflow.value?.id ?: return
        if (isGuest) return
        scope.launch {
            try {
                println("[WorkflowController] Hard refreshing workflow/presence info for $wfId")
                val real = SupabaseRepository.fetchWorkflowById(wfId)
                if (real != null) {
                    val updated = _workflows.value.map { if (it.id == wfId) real else it }
                    _workflows.value = updated
                    saveToLocal(updated)
                    if (_currentWorkflow.value?.id == wfId) {
                        _currentWorkflow.value = real
                    }
                }
            } catch (e: Exception) {
                println("[WorkflowController] Hard refresh failed: ${e.message}")
            }
        }
    }

    /**
     * Called after a user successfully joins a shared workflow via invite code.
     *
     * Strategy:
     * 1. Insert a placeholder immediately → user sees the card at once.
     * 2. Fetch the real workflow from Appwrite in background.
     * 3. Replace placeholder with real data (name, nodes, etc.).
     */
    fun addSharedWorkflow(workflowId: String, workflowName: String = "Shared Workflow") {
        // If already present, nothing to do
        if (_workflows.value.any { it.id == workflowId }) {
            println("[WorkflowController] addSharedWorkflow: already have $workflowId")
            return
        }

        // Step 1 — insert placeholder immediately so card shows on dashboard
        val placeholder = Workflow(
            id = workflowId,
            name = workflowName,
            lastEdited = getCurrentTimeMillis(),
            createdAtDate = app.ak25.pocketflow.utils.getCurrentDate()
        )
        val withPlaceholder = _workflows.value + placeholder
        _workflows.value = withPlaceholder
        saveToLocal(withPlaceholder)
        println("[WorkflowController] addSharedWorkflow: placeholder added for $workflowId")

        // Step 2 — fetch real workflow in background and replace placeholder
        scope.launch {
            println("[WorkflowController] addSharedWorkflow: fetching real data for $workflowId")
            val real = SupabaseRepository.fetchWorkflowById(workflowId)
            if (real != null) {
                val updated = _workflows.value.map { if (it.id == workflowId) real else it }
                _workflows.value = updated
                saveToLocal(updated)
                println("[WorkflowController] addSharedWorkflow: replaced placeholder with '${real.name}'")
            } else {
                println("[WorkflowController] addSharedWorkflow: fetch failed, placeholder stays")
            }
        }
    }

    fun unloadWorkflow() {
        val id = _currentWorkflow.value?.id ?: return
        _currentWorkflow.value = null
        _collaborators.value = emptyList()
        _nodePresences.value = emptyList()
        _memberPresences.value = emptyList()
        currentLocalAction = NodeAction.VIEWING
        currentLocalNodeId = ""
        presencePollJob?.cancel()
        presencePollJob = null
        scope.launch {
            SupabaseRealtimeService.leavePresenceChannel()
            if (!isGuest) {
                WorkflowShareRepository.clearPresence(id)
            }
        }
    }

    fun ensurePresenceJoined() {
        if (isGuest) return
        val wf = _currentWorkflow.value ?: return
        val myUid = store.loadString("appwrite_user_id").orEmpty()
        val myName = store.loadString("user_name") ?: "Me"
        if (myUid.isEmpty()) return

        if (!SupabaseRealtimeService.isPresenceSubscribed()) {
            println("[WorkflowController] ⚠️ Presence channel not SUBSCRIBED. Rejoining...")
            presencePollJob?.cancel()
            presencePollJob = scope.launch {
                try {
                    SupabaseRealtimeService.joinPresenceChannel(wf.id, myUid, myName) { members, nodes ->
                        _memberPresences.value = members
                        _nodePresences.value = nodes
                    }
                } catch (e: Exception) {
                    println("[WC] failed to rejoin presence channel: ${e.message}")
                }

                while (true) {
                    delay(10_000)
                    try {
                        refreshCurrentWorkflowFromCloud()
                        if (myUid.isNotEmpty()) {
                            SupabaseRealtimeService.refreshOnlinePresence(
                                userId = myUid,
                                userName = myName,
                                action = currentLocalAction,
                                nodeId = currentLocalNodeId
                            )
                            WorkflowShareRepository.upsertPresence(wf.id)
                        }
                    } catch (e: Exception) { /* silent */ }
                }
            }
        }
    }

    // ─── Node presence broadcast helpers ─────────────────────────────────────

    /** Call when the user touches the canvas (not a node). Broadcasts cursor position. */
    fun onCanvasTouch(x: Float, y: Float) {
        if (isGuest) return
        ensurePresenceJoined()
        currentLocalAction = NodeAction.TOUCHING
        currentLocalNodeId = ""
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.TOUCHING,
                cursorX = x,
                cursorY = y,
                nodeId = ""
            )
        }
    }

    /** Call when the user opens the node inspector for a node. */
    fun onNodeInspect(nodeId: String) {
        if (isGuest) return
        ensurePresenceJoined()
        currentLocalAction = NodeAction.INSPECTING
        currentLocalNodeId = nodeId
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.INSPECTING,
                nodeId = nodeId
            )
        }
    }

    /** Call when the node inspector bottom sheet is opened — keeps border visible until closed. */
    fun onNodeOpen(nodeId: String) {
        if (isGuest) return
        ensurePresenceJoined()
        currentLocalAction = NodeAction.OPEN_NODE
        currentLocalNodeId = nodeId
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.OPEN_NODE,
                nodeId = nodeId
            )
        }
    }

    /** Call when the node inspector bottom sheet is dismissed — clears the persistent border. */
    fun onNodeClose() {
        if (isGuest) return
        currentLocalAction = NodeAction.VIEWING
        currentLocalNodeId = ""
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.VIEWING,
                nodeId = ""
            )
        }
    }

    /** Call when this user starts running a node — disables run button for other users. */
    fun onNodeRunning(nodeId: String) {
        if (isGuest) return
        ensurePresenceJoined()
        currentLocalAction = NodeAction.RUNNING
        currentLocalNodeId = nodeId
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.RUNNING,
                nodeId = nodeId
            )
        }
    }

    /** Call when the user starts dragging a node. */
    fun onNodeDragStart(nodeId: String, cursorX: Float = 0f, cursorY: Float = 0f) {
        if (isGuest) return
        ensurePresenceJoined()
        currentLocalAction = NodeAction.DRAGGING
        currentLocalNodeId = nodeId
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.DRAGGING,
                cursorX = cursorX,
                cursorY = cursorY,
                nodeId = nodeId
            )
        }
    }

    private var lastNodeDragBroadcast = 0L
    /** Call during node drag to broadcast cursor position (throttled to ~8 fps). */
    fun onNodeDrag(nodeId: String, cursorX: Float, cursorY: Float) {
        if (isGuest) return
        currentLocalAction = NodeAction.DRAGGING
        currentLocalNodeId = nodeId
        val now = getCurrentTimeMillis()
        if (now - lastNodeDragBroadcast < 120) return
        lastNodeDragBroadcast = now
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.DRAGGING,
                cursorX = cursorX,
                cursorY = cursorY,
                nodeId = nodeId
            )
        }
    }

    /** Call when the user releases / finishes dragging a node. */
    fun onNodeDragEnd(nodeId: String) {
        if (isGuest) return
        currentLocalAction = NodeAction.VIEWING
        currentLocalNodeId = nodeId
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.VIEWING,
                nodeId = nodeId
            )
        }
    }

    /** Call on long-press hold of a node. */
    fun onNodeHold(nodeId: String) {
        if (isGuest) return
        ensurePresenceJoined()
        currentLocalAction = NodeAction.HOLDING
        currentLocalNodeId = nodeId
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.HOLDING,
                nodeId = nodeId
            )
        }
    }

    /** Call when a node is tapped/selected. */
    fun onNodeSelect(nodeId: String) {
        if (isGuest) return
        ensurePresenceJoined()
        currentLocalAction = NodeAction.SELECTING
        currentLocalNodeId = nodeId
        val uid = store.loadString("appwrite_user_id").orEmpty()
        val name = store.loadString("user_name").orEmpty()
        scope.launch {
            SupabaseRealtimeService.updateMyPresence(
                userId = uid,
                userName = name,
                action = NodeAction.SELECTING,
                nodeId = nodeId
            )
        }
    }

    fun togglePinWorkflow(workflowId: String) {
        val list = _workflows.value.map {
            if (it.id == workflowId) {
                val nextPinState = !it.isPinned
                store.saveString("pinned_workflow_$workflowId", nextPinState.toString())
                it.copy(isPinned = nextPinState)
            } else it
        }
        _workflows.value = list
        val current = _currentWorkflow.value
        if (current?.id == workflowId) {
            _currentWorkflow.value = current.copy(isPinned = !current.isPinned)
        }
        saveToLocal(list)
    }

    fun renameWorkflow(workflowId: String, newName: String) {
        val oldName = _workflows.value.find { it.id == workflowId }?.name ?: "Unknown"
        val list = _workflows.value.map {
            if (it.id == workflowId) it.copy(name = newName) else it
        }
        _workflows.value = list
        val current = _currentWorkflow.value
        if (current?.id == workflowId) _currentWorkflow.value = current.copy(name = newName)
        saveToLocal(list)
        val target = list.find { it.id == workflowId } ?: return
        if (!isGuest) {
            scope.launch { SupabaseRepository.upsertWorkflow(target) }
        }
        app.ak25.pocketflow.storage.ActivityTracker.log(
            type = ActivityType.RENAME_WORKFLOW,
            title = "Workflow Renamed",
            details = "Renamed workflow '$oldName' to '$newName'",
            workflowName = newName
        )
    }

    fun updateWorkflowColor(workflowId: String, colorHex: String) {
        val list = _workflows.value.map {
            if (it.id == workflowId) it.copy(cardColorHex = colorHex) else it
        }
        _workflows.value = list
        val current = _currentWorkflow.value
        if (current?.id == workflowId) _currentWorkflow.value = current.copy(cardColorHex = colorHex)
        saveToLocal(list)
        val target = list.find { it.id == workflowId } ?: return
        if (!isGuest) {
            scope.launch { SupabaseRepository.upsertWorkflow(target) }
        }
    }

    fun addNode(type: NodeType, x: Float, y: Float) {
        val workflow = _currentWorkflow.value ?: return
        val newNode = WorkflowNode(
            id = IdGenerator.generate(),
            type = type,
            positionX = x,
            positionY = y,
            params = type.defaultParams.toMutableMap()
        )
        val updatedNodes = workflow.nodes.toMutableList().apply { add(newNode) }
        updateCurrent(workflow.copy(nodes = updatedNodes))
        app.ak25.pocketflow.storage.ActivityTracker.log(
            type = ActivityType.ADD_NODE,
            title = "Node Added",
            details = "Added node of type '${type.nodeName}'",
            workflowName = workflow.name
        )
    }

    fun updateNodePosition(nodeId: String, x: Float, y: Float) {
        val workflow = _currentWorkflow.value ?: return
        val updatedNodes = workflow.nodes.map {
            if (it.id == nodeId) it.copy(positionX = x, positionY = y) else it
        }.toMutableList()
        // Use debounced sync — dragging fires this many times per second
        val now = getCurrentTimeMillis()
        _currentWorkflow.value = workflow.copy(nodes = updatedNodes, lastEdited = now)
        syncLocalAndDebouncedCloud()
    }

    // ─── Node notes (collaboration) ──────────────────────────────────────────

    /**
     * Add a collaboration note to a node. Records the current member's name so
     * the author can be shown next to the message. Persists + syncs to Appwrite.
     */
    fun addNodeNote(nodeId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val store = app.ak25.pocketflow.storage.LocalStorage
        val myUserId = store.loadString("appwrite_user_id").orEmpty()
        val myUserName = store.loadString("user_name").orEmpty().ifEmpty { "Member" }
        val note = app.ak25.pocketflow.models.NodeNote(
            id = IdGenerator.generate(),
            authorUserId = myUserId,
            authorName = myUserName,
            text = trimmed,
            createdAt = getCurrentTimeMillis()
        )
        patchNodeNotes(nodeId) { it + note }
        sendNodeNoteNotification(nodeId, myUserId, myUserName, trimmed)
    }

    private fun sendNodeNoteNotification(
        nodeId: String,
        authorUserId: String,
        authorName: String,
        noteText: String
    ) {
        val targetWorkflow = _currentWorkflow.value ?: _workflows.value.find { wf -> wf.nodes.any { it.id == nodeId } } ?: return
        val targetNode = targetWorkflow.nodes.find { it.id == nodeId }
        val workflowName = targetWorkflow.name.ifEmpty { "Workflow" }
        val nodeTypeName = targetNode?.type?.name ?: ""
        val friendlyName = when (nodeTypeName) {
            "IMAGE_GENERATION" -> "Image"
            "VIDEO_GENERATION" -> "Video"
            "TEXT_TO_SPEECH" -> "Audio"
            "AUDIO_GENERATION" -> "Audio"
            "TEXT_GENERATION" -> "Text"
            "MODEL3D_GENERATION" -> "3D Model"
            else -> targetNode?.type?.nodeName ?: "Node"
        }

        // Collect all distinct member user IDs in the workflow (excluding the note author)
        val memberIds = try {
            val parsed = json.decodeFromString<List<app.ak25.pocketflow.services.WorkflowMember>>(targetWorkflow.membersJson)
            parsed.map { it.userId }
        } catch (e: Exception) {
            emptyList()
        }

        val cachedMembers = WorkflowShareRepository.getCachedSharePublic(targetWorkflow.id)?.members?.map { it.userId } ?: emptyList()

        val recipientUserIds = (memberIds + cachedMembers + listOf(targetWorkflow.ownerUserId))
            .filter { it.isNotBlank() && it != authorUserId }
            .distinct()

        println("""
        [OneSignal-NodeNote] ══════════════════════════════════════════════════
        [OneSignal-NodeNote] 🚀 Sending Node Note Notification
        [OneSignal-NodeNote] Author: $authorName (uid='$authorUserId')
        [OneSignal-NodeNote] Workflow: '$workflowName' (${targetWorkflow.id})
        [OneSignal-NodeNote] Owner User ID: '${targetWorkflow.ownerUserId}'
        [OneSignal-NodeNote] Raw membersJson: ${targetWorkflow.membersJson}
        [OneSignal-NodeNote] Cached members: $cachedMembers
        [OneSignal-NodeNote] Target Recipient External IDs: $recipientUserIds
        [OneSignal-NodeNote] ══════════════════════════════════════════════════
        """.trimIndent())

        if (recipientUserIds.isEmpty()) {
            println("[OneSignal-NodeNote] ⚠️ No other workflow members to notify (recipient list is empty).")
            return
        }

        scope.launch(Dispatchers.Default) {
            val client = io.ktor.client.HttpClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                }
            }
            try {
                val externalIdsJson = recipientUserIds.joinToString(",") { "\"$it\"" }
                val targetJson = """
                {
                  "app_id": "7090ae90-1a87-4702-8cfd-2694e44301d9",
                  "target_channel": "push",
                  "include_aliases": {
                    "external_id": [$externalIdsJson]
                  },
                  "headings": {"en": "New message on $friendlyName ($workflowName)"},
                  "contents": {"en": "$authorName: $noteText"},
                  "data": {
                    "workflow_id": "${targetWorkflow.id}",
                    "node_id": "$nodeId",
                    "type": "node_note"
                  }
                }
                """.trimIndent()
                val response = client.post("https://onesignal.com/api/v1/notifications") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    header("Authorization", "Key ${Env.ONESIGNAL_REST_API_KEY}")
                    setBody(targetJson)
                }
                val body = response.bodyAsText()
                println("""
                [OneSignal-NodeNote] 📥 Response Status: ${response.status}
                [OneSignal-NodeNote] 📥 Response Body: $body
                """.trimIndent())
                if (body.contains("All included players are not subscribed")) {
                    println("[OneSignal-NodeNote] ⚠️ Diagnostics: OneSignal found NO active push subscriptions for external_ids: $recipientUserIds. Ensure those users opened the app on a physical device, allowed notifications, and logged in.")
                }
            } catch (e: Exception) {
                println("[OneSignal-NodeNote] ❌ Error sending notification: ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    /**
     * Send a OneSignal push notification ping to all members of the workflow (or a specific member).
     * Only targets workflow members via their OneSignal external_id alias.
     */
    fun pingWorkflowMembers(
        workflowId: String? = null,
        specificUserId: String? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val targetWorkflow = if (!workflowId.isNullOrEmpty()) {
            _workflows.value.find { it.id == workflowId } ?: _currentWorkflow.value
        } else {
            _currentWorkflow.value
        }

        if (targetWorkflow == null) {
            println("[OneSignal-Ping] ❌ No target workflow found.")
            onComplete?.invoke(false, "No workflow selected")
            return
        }

        val store = app.ak25.pocketflow.storage.LocalStorage
        val myUserId = store.loadString("appwrite_user_id").orEmpty()
        val myUserName = store.loadString("user_name").orEmpty().ifEmpty { "A collaborator" }
        val workflowName = targetWorkflow.name.ifEmpty { "Shared Workflow" }

        val recipientUserIds = if (!specificUserId.isNullOrEmpty()) {
            listOf(specificUserId).filter { it != myUserId }
        } else {
            val memberIds = try {
                val parsed = json.decodeFromString<List<app.ak25.pocketflow.services.WorkflowMember>>(targetWorkflow.membersJson)
                parsed.map { it.userId }
            } catch (e: Exception) {
                emptyList()
            }
            val cachedMembers = WorkflowShareRepository.getCachedSharePublic(targetWorkflow.id)?.members?.map { it.userId } ?: emptyList()
            (memberIds + cachedMembers + listOf(targetWorkflow.ownerUserId))
                .filter { it.isNotBlank() && it != myUserId }
                .distinct()
        }

        println("""
        [OneSignal-Ping] ══════════════════════════════════════════════════
        [OneSignal-Ping] 🔔 Initiating Workflow Ping
        [OneSignal-Ping] Sender: $myUserName (uid='$myUserId')
        [OneSignal-Ping] Workflow: '$workflowName' (${targetWorkflow.id})
        [OneSignal-Ping] Owner User ID: '${targetWorkflow.ownerUserId}'
        [OneSignal-Ping] Raw membersJson: ${targetWorkflow.membersJson}
        [OneSignal-Ping] Target Recipient External IDs: $recipientUserIds
        [OneSignal-Ping] ══════════════════════════════════════════════════
        """.trimIndent())

        if (recipientUserIds.isEmpty()) {
            println("[OneSignal-Ping] ⚠️ No other members to ping in this workflow.")
            onComplete?.invoke(false, "No other members to ping in this workflow.")
            return
        }

        scope.launch(Dispatchers.Default) {
            val client = io.ktor.client.HttpClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                }
            }
            try {
                val externalIdsJson = recipientUserIds.joinToString(",") { "\"$it\"" }
                val targetJson = """
                {
                  "app_id": "7090ae90-1a87-4702-8cfd-2694e44301d9",
                  "target_channel": "push",
                  "include_aliases": {
                    "external_id": [$externalIdsJson]
                  },
                  "headings": {"en": "Workflow Ping 🔔"},
                  "contents": {"en": "$myUserName pinged everyone in '$workflowName'!"},
                  "data": {
                    "workflow_id": "${targetWorkflow.id}",
                    "type": "ping"
                  }
                }
                """.trimIndent()
                println("[OneSignal-Ping] 📤 Sending request payload to OneSignal:")
                println(targetJson)
                val response = client.post("https://onesignal.com/api/v1/notifications") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    header("Authorization", "Key ${Env.ONESIGNAL_REST_API_KEY}")
                    setBody(targetJson)
                }
                val isSuccess = response.status.value in 200..299
                val body = response.bodyAsText()
                println("""
                [OneSignal-Ping] 📥 Response Status: ${response.status}
                [OneSignal-Ping] 📥 Response Body: $body
                """.trimIndent())
                if (body.contains("All included players are not subscribed")) {
                    println("[OneSignal-Ping] ⚠️ Diagnostics: OneSignal found NO active push subscriptions for external_ids: $recipientUserIds. Ensure those users opened the app on a physical device, allowed notifications, and logged in.")
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(isSuccess, if (isSuccess) "Ping sent to ${recipientUserIds.size} member(s)!" else "Failed to send ping")
                }
            } catch (e: Exception) {
                println("[OneSignal-Ping] ❌ Error sending ping: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "Failed to send ping")
                }
            } finally {
                client.close()
            }
        }
    }

    /**
     * Send a OneSignal push notification to collaborators when a generation completes.
     */
    fun notifyCollaboratorsGenerationCompleted(
        workflowId: String,
        nodeId: String,
        nodeTypeName: String
    ) {
        val targetWorkflow = _workflows.value.find { it.id == workflowId } ?: _currentWorkflow.value ?: return
        val store = app.ak25.pocketflow.storage.LocalStorage
        val myUserId = store.loadString("appwrite_user_id").orEmpty()
        val myUserName = store.loadString("user_name").orEmpty().ifEmpty { "A collaborator" }
        val workflowName = targetWorkflow.name.ifEmpty { "Shared Workflow" }

        val friendlyName = when (nodeTypeName) {
            "IMAGE_GENERATION" -> "Image"
            "VIDEO_GENERATION" -> "Video"
            "TEXT_TO_SPEECH" -> "Audio"
            "AUDIO_GENERATION" -> "Audio"
            "TEXT_GENERATION" -> "Text"
            "MODEL3D_GENERATION" -> "3D Model"
            "IMAGE_TO_VIDEO" -> "Video"
            "AD_LOCALIZATION" -> "Ad Localization"
            "MARKETING_STOCK_IMAGE" -> "Marketing Stock Image"
            "PRODUCT_AD" -> "Product Ad"
            "PRODUCT_CAMPAIGN" -> "Product Campaign"
            "PRODUCT_SWAP" -> "Product Swap"
            "MULTI_SHOT_VIDEO" -> "Multi-Shot Video"
            "PRODUCT_UGC" -> "Product UGC"
            else -> nodeTypeName.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        val memberIds = try {
            val parsed = json.decodeFromString<List<app.ak25.pocketflow.services.WorkflowMember>>(targetWorkflow.membersJson)
            parsed.map { it.userId }
        } catch (e: Exception) {
            emptyList()
        }

        val cachedMembers = WorkflowShareRepository.getCachedSharePublic(targetWorkflow.id)?.members?.map { it.userId } ?: emptyList()

        val recipientUserIds = (memberIds + cachedMembers + listOf(targetWorkflow.ownerUserId))
            .filter { it.isNotBlank() && it != myUserId }
            .distinct()

        if (recipientUserIds.isEmpty()) return

        println("""
        [OneSignal-Generation] ══════════════════════════════════════════════════
        [OneSignal-Generation] 🚀 Sending Collaborator Generation Notification
        [OneSignal-Generation] User: $myUserName (uid='$myUserId')
        [OneSignal-Generation] Workflow: '$workflowName' (${targetWorkflow.id})
        [OneSignal-Generation] Node: $friendlyName ($nodeId)
        [OneSignal-Generation] Target Recipient External IDs: $recipientUserIds
        [OneSignal-Generation] ══════════════════════════════════════════════════
        """.trimIndent())

        scope.launch(Dispatchers.Default) {
            val client = io.ktor.client.HttpClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                }
            }
            try {
                val externalIdsJson = recipientUserIds.joinToString(",") { "\"$it\"" }
                val targetJson = """
                {
                  "app_id": "7090ae90-1a87-4702-8cfd-2694e44301d9",
                  "target_channel": "push",
                  "include_aliases": {
                    "external_id": [$externalIdsJson]
                  },
                  "headings": {"en": "Generation Completed ($workflowName)"},
                  "contents": {"en": "$myUserName generated $friendlyName in '$workflowName'"},
                  "data": {
                    "workflow_id": "${targetWorkflow.id}",
                    "node_id": "$nodeId",
                    "type": "generation_complete"
                  }
                }
                """.trimIndent()
                val response = client.post("https://onesignal.com/api/v1/notifications") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    header("Authorization", "Key ${Env.ONESIGNAL_REST_API_KEY}")
                    setBody(targetJson)
                }
                println("[OneSignal-Generation] 📥 Response Status: ${response.status} Body: ${response.bodyAsText()}")
            } catch (e: Exception) {
                println("[OneSignal-Generation] ❌ Error sending generation notification: ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    /** Remove a note by id (only the author is allowed to remove their own). */
    fun removeNodeNote(nodeId: String, noteId: String) {
        patchNodeNotes(nodeId) { list -> list.filter { it.id != noteId } }
    }

    private fun patchNodeNotes(
        nodeId: String,
        transform: (List<app.ak25.pocketflow.models.NodeNote>) -> List<app.ak25.pocketflow.models.NodeNote>
    ) {
        val current = _currentWorkflow.value
        if (current != null && current.nodes.any { it.id == nodeId }) {
            val updatedNodes = current.nodes.map {
                if (it.id == nodeId) it.copy(notes = transform(it.notes).toMutableList()) else it
            }.toMutableList()
            updateCurrent(current.copy(nodes = updatedNodes))
        } else {
            val updatedList = _workflows.value.map { wf ->
                if (wf.nodes.any { it.id == nodeId }) {
                    wf.copy(nodes = wf.nodes.map {
                        if (it.id == nodeId) it.copy(notes = transform(it.notes).toMutableList()) else it
                    }.toMutableList())
                } else wf
            }
            _workflows.value = updatedList
            saveToLocal(updatedList)
            val target = updatedList.find { wf -> wf.nodes.any { it.id == nodeId } } ?: return
            if (!isGuest) {
                scope.launch { SupabaseRepository.upsertWorkflow(target) }
            }
        }
    }

    fun updateNodeParams(nodeId: String, key: String, value: String) {
        val current = _currentWorkflow.value
        if (current != null && current.nodes.any { it.id == nodeId }) {
            val updatedNodes = current.nodes.map {
                if (it.id == nodeId) {
                    it.copy(params = it.params.toMutableMap().apply { put(key, value) })
                } else it
            }.toMutableList()
            updateCurrent(current.copy(nodes = updatedNodes))
        } else {
            val updatedList = _workflows.value.map { wf ->
                if (wf.nodes.any { it.id == nodeId }) {
                    wf.copy(nodes = wf.nodes.map {
                        if (it.id == nodeId) it.copy(params = it.params.toMutableMap().apply { put(key, value) })
                        else it
                    }.toMutableList())
                } else wf
            }
            _workflows.value = updatedList
            saveToLocal(updatedList)
            val target = updatedList.find { wf -> wf.nodes.any { it.id == nodeId } } ?: return
            if (!isGuest) {
                scope.launch { SupabaseRepository.upsertWorkflow(target) }
            }
        }
    }

    fun deleteNode(nodeId: String) {
        val workflow = _currentWorkflow.value ?: return
        val node = workflow.nodes.find { it.id == nodeId }
        // Delete uploaded image from storage if this is an UPLOADED_IMAGE node
        if (node?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE && !isGuest) {
            scope.launch { app.ak25.pocketflow.services.SupabaseRepository.deleteImageFile(nodeId) }
        }
        updateCurrent(workflow.copy(
            nodes = workflow.nodes.filter { it.id != nodeId }.toMutableList(),
            edges = workflow.edges.filter { it.sourceNodeId != nodeId && it.targetNodeId != nodeId }.toMutableList()
        ))
    }

    fun connectNodes(
        sourceNodeId: String, sourcePortId: String,
        targetNodeId: String, targetPortId: String
    ): Boolean {
        val workflow = _currentWorkflow.value ?: return false
        val sourceNode = workflow.nodes.find { it.id == sourceNodeId } ?: return false
        val targetNode = workflow.nodes.find { it.id == targetNodeId } ?: return false
        val sourcePort = sourceNode.type.outputs.find { it.id == sourcePortId } ?: return false
        val targetPort = targetNode.type.inputs.find { it.id == targetPortId } ?: return false
        if (sourcePort.dataType != targetPort.dataType) return false

        val existingCount = workflow.edges.count { it.targetNodeId == targetNodeId && it.targetPortId == targetPortId }
        if (existingCount >= targetPort.maxConnections) return false

        val newEdge = WorkflowEdge(
            id = IdGenerator.generate(),
            sourceNodeId = sourceNodeId, sourcePortId = sourcePortId,
            targetNodeId = targetNodeId, targetPortId = targetPortId
        )
        updateCurrent(workflow.copy(edges = (workflow.edges + newEdge).toMutableList()))
        return true
    }

    fun deleteEdge(edgeId: String) {
        val workflow = _currentWorkflow.value ?: return
        updateCurrent(workflow.copy(edges = workflow.edges.filter { it.id != edgeId }.toMutableList()))
    }

    fun clearCanvas() {
        val workflow = _currentWorkflow.value ?: return
        updateCurrent(workflow.copy(nodes = mutableListOf(), edges = mutableListOf()))
    }

    /**
     * Leave a shared workflow. Removes it from this user's dashboard locally and
     * removes the current user from the workflow's membersJson on Appwrite.
     * Only meant for non-owner members (the owner uses deleteWorkflow).
     */
    fun leaveWorkflow(workflowId: String) {
        val name = _workflows.value.find { it.id == workflowId }?.name ?: "Shared Workflow"
        _workflows.update { it.filter { wf -> wf.id != workflowId } }
        if (_currentWorkflow.value?.id == workflowId) _currentWorkflow.value = null
        saveToLocal(_workflows.value)
        if (!isGuest) {
            val uid = app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id").orEmpty()
            scope.launch {
                app.ak25.pocketflow.services.WorkflowShareRepository.removeMemberFromShare(workflowId, uid)
            }
        }
        app.ak25.pocketflow.storage.ActivityTracker.log(
            type = ActivityType.LEAVE_WORKFLOW,
            title = "Left Workflow",
            details = "Left shared workflow '$name'",
            workflowName = name
        )
    }

    fun deleteWorkflow(workflowId: String) {
        val name = _workflows.value.find { it.id == workflowId }?.name ?: "Unknown"
        // Delete all uploaded images for UPLOADED_IMAGE nodes in this workflow
        val uploadedImageNodeIds = _workflows.value
            .find { it.id == workflowId }?.nodes
            ?.filter { it.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE }
            ?.map { it.id } ?: emptyList()
        _workflows.update { it.filter { wf -> wf.id != workflowId } }
        if (_currentWorkflow.value?.id == workflowId) _currentWorkflow.value = null
        saveToLocal(_workflows.value)
        if (!isGuest) {
            scope.launch {
                uploadedImageNodeIds.forEach { nodeId ->
                    app.ak25.pocketflow.services.SupabaseRepository.deleteImageFile(nodeId)
                }
                SupabaseRepository.deleteWorkflow(workflowId)
            }
        }
        app.ak25.pocketflow.storage.ActivityTracker.log(
            type = ActivityType.DELETE_WORKFLOW,
            title = "Workflow Deleted",
            details = "Deleted workflow '$name'",
            workflowName = name
        )
    }

    fun updateNodeStatus(
        nodeId: String,
        status: NodeStatus,
        message: String? = null,
        outputUrl: String? = null,
        localPath: String? = null,
        jobId: String? = null          // Runway task ID — persist immediately on submission
    ) {
        fun patchNode(node: WorkflowNode) = node.copy(
            status = status,
            errorMessage = message,
            outputUrl = outputUrl ?: node.outputUrl,
            outputLocalPath = localPath ?: node.outputLocalPath,
            jobId = jobId ?: node.jobId  // Keep existing jobId if not overriding
        )

        val current = _currentWorkflow.value
        if (current != null && current.nodes.any { it.id == nodeId }) {
            // Node lives in the active workflow — updateCurrent triggers syncAndSave → Appwrite
            updateCurrent(current.copy(nodes = current.nodes.map {
                if (it.id == nodeId) patchNode(it) else it
            }.toMutableList()))
        } else {
            // Node lives in a background workflow (or _currentWorkflow was momentarily null)
            val updatedList = _workflows.value.map { wf ->
                if (wf.nodes.any { it.id == nodeId }) {
                    val patched = wf.copy(nodes = wf.nodes.map { if (it.id == nodeId) patchNode(it) else it }.toMutableList())
                    if (_currentWorkflow.value?.id == wf.id) {
                        _currentWorkflow.value = patched
                    }
                    patched
                } else wf
            }
            _workflows.value = updatedList
            saveToLocal(updatedList)
            val target = updatedList.find { wf -> wf.nodes.any { it.id == nodeId } } ?: return
            if (!isGuest) {
                scope.launch { SupabaseRepository.upsertWorkflow(target) }
            }
        }
    }
}
