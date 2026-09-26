package app.ak25.pocketflow.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.NodeStatus
import app.ak25.pocketflow.models.NodeType
import app.ak25.pocketflow.models.PortDefinition
import app.ak25.pocketflow.services.ExecutionEngine
import kotlinx.coroutines.launch
import kotlin.math.floor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import app.ak25.pocketflow.services.PocketFlowPurchases
import app.ak25.pocketflow.services.UserPresenceState
import app.ak25.pocketflow.utils.getCurrentTimeMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions

private fun getNodeTypeIcon(type: NodeType): ImageVector {
    return when (type) {
        NodeType.TEXT_PROMPT -> AppIcons.Text
        NodeType.IMAGE_GENERATION, NodeType.UPLOADED_IMAGE, NodeType.AD_LOCALIZATION, NodeType.MARKETING_STOCK_IMAGE, NodeType.PRODUCT_CAMPAIGN -> AppIcons.Image
        NodeType.IMAGE_TO_VIDEO, NodeType.PRODUCT_AD, NodeType.PRODUCT_SWAP, NodeType.MULTI_SHOT_VIDEO, NodeType.PRODUCT_UGC -> AppIcons.Video
        NodeType.TEXT_TO_SPEECH -> AppIcons.Audio
        NodeType.MODEL3D_GENERATION -> AppIcons.Cube3D
        NodeType.NOTE -> AppIcons.Edit
        else -> AppIcons.Play
    }
}

private sealed class RunRequest {
    data class Node(val nodeId: String) : RunRequest()
    data class Workflow(val onlyEmpty: Boolean = true) : RunRequest()
}

private data class CreditCheckState(
    val request: RunRequest,
    val actionName: String,
    val requiredCredits: Int,
    val availableCredits: Int?,
    val isLoading: Boolean = false
) {
    val remainingCredits: Int = ((availableCredits ?: 0) - requiredCredits).coerceAtLeast(0)
    val hasEnoughCredits: Boolean = availableCredits != null && availableCredits >= requiredCredits
}

data class PendingConnection(
    val sourceNodeId: String,
    val sourcePortId: String,
    val targetNodeId: String,
    val compatiblePorts: List<PortDefinition>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    controller: WorkflowController,
    engine: ExecutionEngine,
    backgroundScope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit
) {
    val workflow by controller.currentWorkflow.collectAsState()
    val nodePresences by controller.nodePresences.collectAsState()
    val memberPresences by controller.memberPresences.collectAsState()
    val incomingNote by controller.incomingNote.collectAsState()
    val myUid = (app.ak25.pocketflow.storage.LocalStorage.loadString("supabase_user_id")
        ?: app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id")).orEmpty()
    val coroutineScope = rememberCoroutineScope()

    // Auto-dismiss incoming note snackbar after a few seconds
    LaunchedEffect(incomingNote) {
        if (incomingNote != null) {
            kotlinx.coroutines.delay(4_000)
            controller.consumeIncomingNote()
        }
    }
    val isGuest = app.ak25.pocketflow.storage.LocalStorage.loadString("is_guest") == "true"
    
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val portPositions = remember { mutableStateMapOf<String, Offset>() }
    
    var draggingPortId by remember { mutableStateOf<String?>(null) }
    var dragEndPosition by remember { mutableStateOf<Offset?>(null) }
    var showNodeSelector by remember { mutableStateOf(false) }
    var selectedEdgeId by remember { mutableStateOf<String?>(null) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var longPressedNodeId by remember { mutableStateOf<String?>(null) }
    var connectingFromNodeId by remember { mutableStateOf<String?>(null) }
    var pendingConnection by remember { mutableStateOf<PendingConnection?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var viewingMediaUrl by remember { mutableStateOf<String?>(null) }
    var notesNodeId by remember { mutableStateOf<String?>(null) }
    var selectedMemberForDetails by remember { mutableStateOf<UserPresenceState?>(null) }
    var lastCanvasTouchBroadcast by remember { mutableStateOf(0L) }
    var isPortraitPreview by remember { mutableStateOf(false) }
    
    LaunchedEffect(viewingMediaUrl) {
        if (viewingMediaUrl == null) {
            isPortraitPreview = false
        }
    }
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newWorkflowName by remember(workflow?.name) { mutableStateOf(workflow?.name ?: "") }
    var creditCheckState by remember { mutableStateOf<CreditCheckState?>(null) }
    var showPurchaseCreditsSheet by remember { mutableStateOf(false) }
    var showWorkflowRunSheet by remember { mutableStateOf(false) }
    var isCheckingCredits by remember { mutableStateOf(false) }
    val virtualCurrencies by PocketFlowPurchases.virtualCurrencies.collectAsState()
    val availableCreditsLabel = PocketFlowPurchases.getAvailableCreditsLabel(virtualCurrencies)

    val blurRadius by animateDpAsState(if (viewingMediaUrl != null) 16.dp else 0.dp)

    // Handle deep link node focus and centering
    val focusedNodeEvent by controller.focusedNode.collectAsState()
    LaunchedEffect(focusedNodeEvent, workflow, viewportSize) {
        val event = focusedNodeEvent ?: return@LaunchedEffect
        val targetNode = workflow?.nodes?.find { it.id == event.nodeId }
        if (targetNode != null) {
            if (event.isNote) {
                notesNodeId = targetNode.id
            } else {
                selectedNodeId = targetNode.id
            }
            if (viewportSize.width > 0 && viewportSize.height > 0) {
                val nodeCenterX = targetNode.positionX + 110f
                val nodeCenterY = targetNode.positionY + 50f
                offset = Offset(viewportSize.width / 2f - nodeCenterX * scale, viewportSize.height / 2f - nodeCenterY * scale)
                controller.clearFocusedNode()
            }
        }
    }

    // ── Presence: broadcast MY activity while this screen is open ─────────────
    // DisposableEffect starts the presence heartbeat when the workflow is loaded
    // and clears it when the user navigates away. Skipped entirely for guests —
    // their workflows are local-only and never broadcast presence.
    val workflowId = workflow?.id
    DisposableEffect(workflowId) {
        if (workflowId == null) return@DisposableEffect onDispose {}

        // Re-trigger loadWorkflow to ensure polling loop + member seed are fresh
        controller.loadWorkflow(workflowId)

        onDispose {
            controller.unloadWorkflow()
        }
    }

    // Auto-resume any running/pending jobs when entering EditorScreen or after app minimize/restore
    LaunchedEffect(workflow?.id, workflow?.nodes) {
        val currentWf = workflow ?: return@LaunchedEffect
        currentWf.nodes.forEach { node ->
            if ((node.status == NodeStatus.RUNNING || node.status == NodeStatus.PENDING) && !engine.isRunning(node.id)) {
                val jId = node.jobId ?: node.params["jobId"]
                if (!jId.isNullOrEmpty() && node.outputUrl.isNullOrEmpty()) {
                    engine.engineScope.launch {
                        val previousBalance = PocketFlowPurchases.getAvailableCreditsBalance()
                        val expectedDeduction = PocketFlowPurchases.estimateNodeCredits(node)
                        val success = engine.runNode(node.id)
                        if (success) {
                            val deductSuccess = PocketFlowPurchases.deductCredits(expectedDeduction)
                            PocketFlowPurchases.refreshVirtualCurrenciesAfterRun(
                                previousBalance = previousBalance,
                                expectedDeduction = expectedDeduction
                            )
                        }
                    }
                }
            }
        }
    }

    fun prepareRun(request: RunRequest) {
        if (isCheckingCredits) return
        val requiredCredits = when (request) {
            is RunRequest.Node -> workflow?.nodes?.find { it.id == request.nodeId }
                ?.let { PocketFlowPurchases.estimateNodeCredits(it) } ?: 0
            is RunRequest.Workflow -> PocketFlowPurchases.estimateWorkflowCredits(workflow, onlyEmpty = request.onlyEmpty)
        }
        val actionName = when (request) {
            is RunRequest.Node -> "node run"
            is RunRequest.Workflow -> if (request.onlyEmpty) "branch-wise empty nodes run" else "full workflow run"
        }

        creditCheckState = CreditCheckState(
            request = request,
            actionName = actionName,
            requiredCredits = requiredCredits,
            availableCredits = null,
            isLoading = true
        )
        isCheckingCredits = true
        engine.engineScope.launch {

            val freshCurrencies = PocketFlowPurchases.fetchVirtualCurrencies(forceRefresh = true)
            val availableCredits = PocketFlowPurchases.getAvailableCreditsBalance(freshCurrencies)
                ?: PocketFlowPurchases.getAvailableCreditsBalance()

            creditCheckState = creditCheckState?.copy(
                availableCredits = availableCredits,
                isLoading = false
            )
            isCheckingCredits = false
        }
    }

    fun continueRun(request: RunRequest, expectedDeduction: Int) {
        engine.engineScope.launch {
            val previousBalance = PocketFlowPurchases.getAvailableCreditsBalance()
            val success = when (request) {
                is RunRequest.Node -> engine.runNode(request.nodeId)
                is RunRequest.Workflow -> engine.runWorkflow(onlyEmpty = request.onlyEmpty)
            }

            if (success) {
                // Deduct credits from RevenueCat - wait for it to complete
                val deductSuccess = PocketFlowPurchases.deductCredits(expectedDeduction)
                if (!deductSuccess) {
                    errorMessage = "Failed to deduct credits"
                }

                val currentWf = controller.currentWorkflow.value
                val wfName = currentWf?.name ?: "Unknown"
                if (request is RunRequest.Node) {
                    val node = currentWf?.nodes?.find { it.id == request.nodeId }
                    val nodeType = node?.type?.nodeName ?: "Unknown Node"
                    val prompt = node?.params?.get("prompt") ?: node?.params?.get("text") ?: ""
                    
                    app.ak25.pocketflow.storage.ActivityTracker.log(
                        type = app.ak25.pocketflow.models.ActivityType.RUN_NODE,
                        title = "Node Run Success",
                        details = "Executed '$nodeType' successfully.${if (prompt.isNotEmpty()) " Prompt used: \"$prompt\"" else ""}",
                        workflowName = wfName
                    )
                } else {
                    app.ak25.pocketflow.storage.ActivityTracker.log(
                        type = app.ak25.pocketflow.models.ActivityType.RUN_NODE,
                        title = "Workflow Run Success",
                        details = "Executed entire workflow successfully.",
                        workflowName = wfName
                    )
                }

                if (expectedDeduction > 0) {
                    app.ak25.pocketflow.storage.ActivityTracker.log(
                        type = app.ak25.pocketflow.models.ActivityType.SPEND_CREDITS,
                        title = "Credits Deducted",
                        details = "Spent $expectedDeduction credits for execution.",
                        creditsSpent = expectedDeduction,
                        workflowName = wfName
                    )
                }

                // Refresh virtual currencies
                PocketFlowPurchases.refreshVirtualCurrenciesAfterRun(
                    previousBalance = previousBalance,
                    expectedDeduction = expectedDeduction
                )
            }
            if (!success) {
                val currentWf = controller.currentWorkflow.value
                val wfName = currentWf?.name ?: "Unknown"
                val failedNode = currentWf?.nodes?.find { it.status == app.ak25.pocketflow.models.NodeStatus.FAILED }
                val errorMsg = failedNode?.errorMessage ?: "Unknown error"
                
                app.ak25.pocketflow.storage.ActivityTracker.log(
                    type = app.ak25.pocketflow.models.ActivityType.RUN_NODE,
                    title = "Node Run Failed",
                    details = "Failed executing node. Error: $errorMsg",
                    workflowName = wfName
                )

                errorMessage = if (request is RunRequest.Workflow) {
                    "Workflow failed: ${failedNode?.errorMessage ?: "Unknown error"}"
                } else {
                    val failed = controller.currentWorkflow.value?.nodes?.find { it.id == (request as RunRequest.Node).nodeId }
                    "Generation failed: ${failed?.errorMessage ?: "Unknown error"}"
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .background(Color(0xFFFAFAFA))
                .onSizeChanged { viewportSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTapGestures { tapPos ->
                    // Broadcast canvas touch position to other users (throttled)
                    val canvasPos = (tapPos - offset) / scale
                    val nowMs = getCurrentTimeMillis()
                    if (nowMs - lastCanvasTouchBroadcast >= 150) {
                        lastCanvasTouchBroadcast = nowMs
                        controller.onCanvasTouch(canvasPos.x, canvasPos.y)
                    }

                    val localTapPos = canvasPos
                    var tappedEdgeId: String? = null
                    workflow?.edges?.forEach { edge ->
                        val start = portPositions["${edge.sourceNodeId}_${edge.sourcePortId}"]
                        val end = portPositions["${edge.targetNodeId}_${edge.targetPortId}"]
                        if (start != null && end != null) {
                            if (isTapNearEdge(localTapPos, start, end)) {
                                tappedEdgeId = edge.id
                            }
                        }
                    }
                    selectedEdgeId = tappedEdgeId
                    if (tappedEdgeId == null) {
                        selectedNodeId = null
                        longPressedNodeId = null
                        connectingFromNodeId = null
                    } else {
                        selectedNodeId = null
                        longPressedNodeId = null
                        connectingFromNodeId = null
                    }
                }
            }
            .pointerInput(Unit) {
                // Broadcast drag/pan as canvas touch for live cursor (throttled)
                detectDragGestures { change, _ ->
                    val canvasPos = (change.position - offset) / scale
                    val nowMs = getCurrentTimeMillis()
                    if (nowMs - lastCanvasTouchBroadcast >= 150) {
                        lastCanvasTouchBroadcast = nowMs
                        controller.onCanvasTouch(canvasPos.x, canvasPos.y)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.1f, 5f)
                    offset += pan
                    
                    val nowMs = getCurrentTimeMillis()
                    if (nowMs - lastCanvasTouchBroadcast >= 150) {
                        lastCanvasTouchBroadcast = nowMs
                        controller.onCanvasTouch(offset.x, offset.y)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = 40.dp.toPx() * scale
            if (spacing >= 8f) {
                val startX = offset.x % spacing
                val startY = offset.y % spacing
                for (x in generateSequence(startX) { it + spacing }.takeWhile { it < size.width }) {
                    drawLine(Color(0xFFE8E8ED), Offset(x, 0f), Offset(x, size.height), 0.5f)
                }
                for (y in generateSequence(startY) { it + spacing }.takeWhile { it < size.height }) {
                    drawLine(Color(0xFFE8E8ED), Offset(0f, y), Offset(size.width, y), 0.5f)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
                transformOrigin = TransformOrigin(0f, 0f)
            }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw Edges
                workflow?.edges?.forEach { edge ->
                    val start = portPositions["${edge.sourceNodeId}_${edge.sourcePortId}"]
                    val end = portPositions["${edge.targetNodeId}_${edge.targetPortId}"]
                    if (start != null && end != null) {
                        val isSelected = edge.id == selectedEdgeId
                        val edgeColor = if (isSelected) Color(0xFFE53935) else Color(0xFF0EA5E9)
                        val edgeWidth = if (isSelected) 4.dp.toPx() else 3.dp.toPx()
                        drawBezier(start, end, edgeColor, edgeWidth)
                        drawCircle(edgeColor, edgeWidth + 2f, end)
                    }
                }
                
                if (draggingPortId != null && dragEndPosition != null) {
                    val start = portPositions[draggingPortId!!]
                    if (start != null) {
                        drawBezier(start, dragEndPosition!!, Color(0xFF0EA5E9).copy(alpha = 0.5f), 3.dp.toPx())
                    }
                }
            }
            
            // Nodes
            workflow?.nodes?.forEach { node ->
                key(node.id) {
                    val currentDraggingId = draggingPortId
                    val currentDragEnd = dragEndPosition
                    var hintColor: Color? = null
                    
                    if (currentDraggingId != null && currentDragEnd != null) {
                        val sourceNodeId = currentDraggingId.substringBefore("_")
                        if (sourceNodeId != node.id) {
                            // Check if drag position is near any port of this node
                            val threshold = 32f * scale
                            val portsOfNode = portPositions.entries.filter { it.key.substringBefore("_") == node.id }
                            val nearbyPort = portsOfNode.find { (it.value - currentDragEnd).getDistance() <= threshold }
                            
                            if (nearbyPort != null) {
                                val sourcePortId = currentDraggingId.substringAfter("_")
                                val targetPortId = nearbyPort.key.substringAfter("_")
                                
                                val sourceNode = workflow?.nodes?.find { it.id == sourceNodeId }
                                if (sourceNode != null) {
                                    val isSourceOutput = sourceNode.type.outputs.any { it.id == sourcePortId }
                                    val isTargetInput = node.type.inputs.any { it.id == targetPortId }
                                    
                                    if (isSourceOutput && isTargetInput) {
                                        val sourcePort = sourceNode.type.outputs.find { it.id == sourcePortId }
                                        val targetPort = node.type.inputs.find { it.id == targetPortId }
                                        val existingCount = workflow?.edges?.count { it.targetNodeId == node.id && it.targetPortId == targetPortId } ?: 0
                                        
                                        if (sourcePort != null && targetPort != null && 
                                            sourcePort.dataType == targetPort.dataType && 
                                            existingCount < targetPort.maxConnections) {
                                            hintColor = Color(0xFF34C759) // Green: compatible and connection allowed
                                        } else {
                                            hintColor = Color(0xFFFF3B30) // Red: incompatible or full
                                        }
                                    } else if (!isSourceOutput && !isTargetInput) {
                                        val isSourceInput = sourceNode.type.inputs.any { it.id == sourcePortId }
                                        val isTargetOutput = node.type.outputs.any { it.id == targetPortId }
                                        if (isSourceInput && isTargetOutput) {
                                            val sourcePort = sourceNode.type.inputs.find { it.id == sourcePortId }
                                            val targetPort = node.type.outputs.find { it.id == targetPortId }
                                            val existingCount = workflow?.edges?.count { it.targetNodeId == sourceNodeId && it.targetPortId == sourcePortId } ?: 0
                                            
                                            if (sourcePort != null && targetPort != null && 
                                                sourcePort.dataType == targetPort.dataType && 
                                                existingCount < sourcePort.maxConnections) {
                                                hintColor = Color(0xFF34C759)
                                            } else {
                                                hintColor = Color(0xFFFF3B30)
                                            }
                                        } else {
                                            hintColor = Color(0xFFFF3B30)
                                        }
                                    } else {
                                        hintColor = Color(0xFFFF3B30)
                                    }
                                }
                            }
                        }
                    }

                    NodeUI(
                        node = node,
                        isSelected = selectedNodeId == node.id || connectingFromNodeId == node.id,
                        onSelect = {
                            if (connectingFromNodeId != null) {
                                if (connectingFromNodeId != node.id) {
                                    val sourceNode = workflow?.nodes?.find { it.id == connectingFromNodeId }
                                    var connected = false
                                    if (sourceNode != null) {
                                        val outPort = sourceNode.type.outputs.firstOrNull()
                                        if (outPort != null) {
                                            val compatiblePorts = node.type.inputs.filter { it.dataType == outPort.dataType }
                                            if (compatiblePorts.isNotEmpty()) {
                                                if (compatiblePorts.size == 1) {
                                                    val success = controller.connectNodes(sourceNode.id, outPort.id, node.id, compatiblePorts.first().id)
                                                    if (!success) {
                                                        errorMessage = "Cannot connect: Port connection limit reached!"
                                                    }
                                                } else {
                                                    pendingConnection = PendingConnection(sourceNode.id, outPort.id, node.id, compatiblePorts)
                                                }
                                            } else {
                                                errorMessage = "Cannot connect: No matching port types!"
                                            }
                                        }
                                    }
                                    connectingFromNodeId = null
                                }
                            } else {
                                selectedNodeId = node.id
                                selectedEdgeId = null
                                longPressedNodeId = null
                            }
                        },
                        onLongPress = {
                            longPressedNodeId = node.id
                            selectedEdgeId = null
                            connectingFromNodeId = null
                        },
                        controller = controller,
                        onRunNode = {
                            controller.onNodeRunning(node.id)
                            prepareRun(RunRequest.Node(node.id))
                        },
                        onPortPositioned = { id, _, x, y -> portPositions[id] = Offset(x, y) },
                        onPortDragStarted = { id -> 
                            draggingPortId = id
                            dragEndPosition = portPositions[id]
                        },
                        onPortDrag = { dx, dy -> dragEndPosition = dragEndPosition?.plus(Offset(dx, dy)) },
                        connectionHintColor = hintColor,
                        onPortDragEnded = {
                            val dragStart = draggingPortId
                            val dragEnd = dragEndPosition
                            if (dragStart != null && dragEnd != null) {
                                // Find any port located near the drop position (within 24dp/pixels radius)
                                val threshold = 28f * scale
                                val targetPortEntry = portPositions.entries.find { entry ->
                                    val key = entry.key
                                    if (key == dragStart) return@find false
                                    // Make sure it doesn't belong to the same node
                                    val startNodeId = dragStart.substringBefore("_")
                                    val targetNodeId = key.substringBefore("_")
                                    if (startNodeId == targetNodeId) return@find false
                                    
                                    val distance = (entry.value - dragEnd).getDistance()
                                    distance <= threshold
                                }
                                
                                if (targetPortEntry != null) {
                                    val sourceNodeId = dragStart.substringBefore("_")
                                    val sourcePortId = dragStart.substringAfter("_")
                                    val targetNodeId = targetPortEntry.key.substringBefore("_")
                                    val targetPortId = targetPortEntry.key.substringAfter("_")
                                    
                                    // Try connecting source output to target input
                                    val sourceNode = workflow?.nodes?.find { it.id == sourceNodeId }
                                    val targetNode = workflow?.nodes?.find { it.id == targetNodeId }
                                    
                                    if (sourceNode != null && targetNode != null) {
                                        val isSourceOutput = sourceNode.type.outputs.any { it.id == sourcePortId }
                                        val isTargetInput = targetNode.type.inputs.any { it.id == targetPortId }
                                        
                                        if (isSourceOutput && isTargetInput) {
                                            val success = controller.connectNodes(sourceNodeId, sourcePortId, targetNodeId, targetPortId)
                                            if (!success) {
                                                errorMessage = "Cannot connect: Port connection limit reached!"
                                            }
                                        } else if (!isSourceOutput && !isTargetInput) {
                                            // Reverse drag (dragged input to output)
                                            val isSourceInput = sourceNode.type.inputs.any { it.id == sourcePortId }
                                            val isTargetOutput = targetNode.type.outputs.any { it.id == targetPortId }
                                            if (isSourceInput && isTargetOutput) {
                                                val success = controller.connectNodes(targetNodeId, targetPortId, sourceNodeId, sourcePortId)
                                                if (!success) {
                                                    errorMessage = "Cannot connect: Port connection limit reached!"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            draggingPortId = null
                            dragEndPosition = null
                        },
                        onViewMedia = { url ->
                            viewingMediaUrl = url
                        },
                        viewingMediaUrl = viewingMediaUrl,
                        onOpenNotes = { notesNodeId = node.id },
                        remoteHolders = nodePresences.filter {
                            it.nodeId == node.id && it.userId != myUid
                        }
                    )
                }
            }


            // Delete Edge Overlay
            if (selectedEdgeId != null) {
                val edge = workflow?.edges?.find { it.id == selectedEdgeId }
                if (edge != null) {
                    val start = portPositions["${edge.sourceNodeId}_${edge.sourcePortId}"]
                    val end = portPositions["${edge.targetNodeId}_${edge.targetPortId}"]
                    if (start != null && end != null) {
                        val mid = getBezierPoint(start, end, 0.5f)
                        val density = androidx.compose.ui.platform.LocalDensity.current.density
                        Box(
                            modifier = Modifier
                                .offset { 
                                    androidx.compose.ui.unit.IntOffset(
                                        (mid.x - 16 * density).toInt(), 
                                        (mid.y - 16 * density).toInt()
                                    ) 
                                }
                                .size(32.dp)
                                .shadow(8.dp, androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFFE53935), androidx.compose.foundation.shape.CircleShape)
                                .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    controller.deleteEdge(edge.id)
                                    selectedEdgeId = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Node Context Menu
            if (longPressedNodeId != null) {
                val node = workflow?.nodes?.find { it.id == longPressedNodeId }
                if (node != null) {
                    Row(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(node.positionX.toInt(), (node.positionY - 50).toInt()) }
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Connect", color = Color(0xFF007AFF), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { 
                            connectingFromNodeId = node.id
                            longPressedNodeId = null 
                        }.padding(8.dp))
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFFEEEEEE)).align(Alignment.CenterVertically))
                        Text("Delete", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { 
                            controller.deleteNode(node.id)
                            longPressedNodeId = null 
                        }.padding(8.dp))
                    }
                }
            }
        }

        // ── Remote cursor badges (all remote users with a known canvas position) ──
        val touchPresences = nodePresences.filter {
            it.cursorX != 0f || it.cursorY != 0f
        }
        if (touchPresences.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            ) {
                touchPresences.forEach { presence ->
                    val firstName = presence.userName.split(" ").first()
                    val actionIcon = when (presence.action) {
                        app.ak25.pocketflow.services.NodeAction.DRAGGING   -> "↔"
                        app.ak25.pocketflow.services.NodeAction.HOLDING    -> "✋"
                        app.ak25.pocketflow.services.NodeAction.SELECTING  -> "👆"
                        app.ak25.pocketflow.services.NodeAction.INSPECTING -> "🔍"
                        app.ak25.pocketflow.services.NodeAction.OPEN_NODE  -> "📋"
                        app.ak25.pocketflow.services.NodeAction.RUNNING    -> "▶"
                        else -> null
                    }
                    Box(
                        modifier = Modifier.offset {
                            androidx.compose.ui.unit.IntOffset(
                                presence.cursorX.toInt(),
                                presence.cursorY.toInt()
                            )
                        }
                    ) {
                        // Cursor triangle dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(presence.color, CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                        )
                        Row(
                            modifier = Modifier
                                .offset(x = 11.dp, y = (-20).dp)
                                .background(presence.color, RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 0.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstName,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Top Toolbar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = Color.White.copy(alpha = 0.9f),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", color = Color(0xFF1A1A1A), fontSize = 20.sp, fontWeight = FontWeight.Light)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                workflow?.name ?: "Untitled Workflow",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1A1A1A)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "✎",
                                color = Color(0xFFCCCCCC),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable { showRenameDialog = true }
                                    .padding(4.dp)
                            )
                        }
                        Text(
                            "${workflow?.nodes?.size ?: 0} nodes · ${workflow?.edges?.size ?: 0} edges",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isAnyNodeRunning = workflow?.nodes?.any { it.status == NodeStatus.RUNNING || it.status == NodeStatus.PENDING } == true

                    // Overall Run icon button
                    Surface(
                        onClick = {
                            if (isAnyNodeRunning) {
                                engine.cancel()
                            } else {
                                showWorkflowRunSheet = true
                            }
                        },
                        shape = CircleShape,
                        color = if (isAnyNodeRunning) Color(0xFFFF3B30).copy(alpha = 0.15f) else Color(0xFF007AFF),
                        shadowElevation = if (isAnyNodeRunning) 0.dp else 2.dp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isAnyNodeRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF3B30)
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.Play,
                                    contentDescription = "Run Workflow",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Member avatar row — single clickable group opens all-members sheet
                    // Hidden for guest users (workflows are local-only, no collaboration).
                    if (!isGuest) {
                    var showMembersSheet by remember { mutableStateOf(false) }
                    val myUid = remember {
                        app.ak25.pocketflow.storage.LocalStorage.loadString("supabase_user_id")
                            ?: app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id") ?: ""
                    }
                    
                    val allMembers = remember(workflow, memberPresences, myUid) {
                        val membersList = try {
                            kotlinx.serialization.json.Json.decodeFromString<List<app.ak25.pocketflow.services.WorkflowMember>>(workflow?.membersJson ?: "[]")
                        } catch (e: Exception) {
                            emptyList<app.ak25.pocketflow.services.WorkflowMember>()
                        }
                        membersList.map { m ->
                            val active = memberPresences.find { it.userId.equals(m.userId, ignoreCase = true) }
                            app.ak25.pocketflow.services.UserPresenceState(
                                userId = m.userId,
                                userName = m.userName,
                                color = active?.color ?: app.ak25.pocketflow.services.WorkflowShareRepository.getMemberColor(m.userId),
                                isActive = active != null,
                                userEmail = m.userEmail
                            )
                        }.sortedByDescending { it.userId.equals(myUid, ignoreCase = true) }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                controller.refreshShareInfo()
                                showMembersSheet = true
                            }
                            .height(44.dp)
                            .width((44 + (allMembers.size - 1).coerceAtLeast(0) * 28).dp)
                    ) {
                        allMembers.forEachIndexed { index, member ->
                            val initials = member.userName
                                .split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                                .take(2).joinToString("").ifEmpty { "?" }
                            val avatarBgColor = if (member.isActive) member.color else Color(0xFFBDBDBD)
                            val isMe = member.userId == myUid

                            Box(
                                modifier = Modifier
                                    .offset(x = (index * 28).dp)
                                    .size(44.dp)
                                    .zIndex((allMembers.size - index).toFloat()),
                                contentAlignment = Alignment.Center
                            ) {
                                // Green ring when online
                                if (member.isActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .border(2.5.dp, Color(0xFF34C759), CircleShape)
                                    )
                                }
                                // Avatar circle — slightly larger for current user
                                Box(
                                    modifier = Modifier
                                        .size(if (isMe) 36.dp else 34.dp)
                                        .shadow(3.dp, CircleShape)
                                        .background(avatarBgColor, CircleShape)
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        fontSize = if (isMe) 13.sp else 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.material3.IconButton(
                        onClick = {
                            val wf = workflow
                            if (wf != null) {
                                coroutineScope.launch {
                                    try {
                                        controller.ensurePresenceJoined()
                                        val uid = app.ak25.pocketflow.storage.LocalStorage.loadString("supabase_user_id")
                                            ?: app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id").orEmpty()
                                        val name = app.ak25.pocketflow.storage.LocalStorage.loadString("user_name") ?: "Me"
                                        if (uid.isNotEmpty()) {
                                            app.ak25.pocketflow.services.SupabaseRealtimeService.refreshOnlinePresence(
                                                userId = uid,
                                                userName = name,
                                                action = app.ak25.pocketflow.services.NodeAction.VIEWING
                                            )
                                            app.ak25.pocketflow.services.WorkflowShareRepository.upsertPresence(wf.id)
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Bolt,
                            contentDescription = "Sync Connection",
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (showMembersSheet) {
                    var isPinging by remember { mutableStateOf(false) }
                    var pingFeedback by remember { mutableStateOf<String?>(null) }

                    ModalBottomSheet(
                        onDismissRequest = { showMembersSheet = false },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 36.dp, top = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Members",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1C1E)
                                )

                                Surface(
                                    onClick = {
                                        if (!isPinging) {
                                            isPinging = true
                                            pingFeedback = null
                                            controller.pingWorkflowMembers(workflow?.id) { _, msg ->
                                                isPinging = false
                                                pingFeedback = msg
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF007AFF).copy(alpha = 0.10f),
                                    enabled = !isPinging
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Notification,
                                            contentDescription = "Ping Members",
                                            tint = Color(0xFF007AFF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (isPinging) "Pinging…" else "Ping All",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF007AFF)
                                        )
                                    }
                                }
                            }

                            if (pingFeedback != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF34C759).copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = pingFeedback.orEmpty(),
                                        fontSize = 12.sp,
                                        color = Color(0xFF28A745),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            allMembers.forEach { m ->
                                val mInitials = m.userName
                                    .split(" ")
                                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                                    .take(2).joinToString("").ifEmpty { "?" }
                                val mColor = if (m.isActive) m.color else Color(0xFFBDBDBD)
                                val isMeEntry = m.userId == myUid
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Avatar with green ring if online
                                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        if (m.isActive) {
                                            Box(modifier = Modifier.size(48.dp).border(2.5.dp, Color(0xFF34C759), CircleShape))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .shadow(2.dp, CircleShape)
                                                .background(mColor, CircleShape)
                                                .border(2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(mInitials, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(m.userName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C1C1E))
                                            if (isMeEntry) {
                                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF007AFF).copy(alpha = 0.12f)) {
                                                    Text("You", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007AFF), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        if (m.userEmail.isNotBlank()) {
                                            Text(m.userEmail, fontSize = 12.sp, color = Color(0xFF8E8E93))
                                        }
                                    }
                                    // Status badge
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (m.isActive) Color(0xFF34C759).copy(alpha = 0.12f) else Color(0xFF8E8E93).copy(alpha = 0.10f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(modifier = Modifier.size(7.dp).background(if (m.isActive) Color(0xFF34C759) else Color(0xFF8E8E93), CircleShape))
                                            Text(
                                                if (m.isActive) "Online" else "Offline",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (m.isActive) Color(0xFF28A745) else Color(0xFF6C757D)
                                            )
                                        }
                                    }
                                }
                                if (m != allMembers.last()) {
                                    androidx.compose.material3.HorizontalDivider(color = Color(0xFFF0F0F0))
                                }
                            }
                        }
                    }
                }
                }
                }
            }
        }
    }

    // UI Overlays (Unscaled & Unblurred)
    Box(modifier = Modifier.fillMaxSize()) {
        if (connectingFromNodeId != null) {
            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 80.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .background(Color(0xFF007AFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Select a node to connect to...", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }

        // Incoming node-note notification — shows below the header when a
        // collaborator posts a new note on a node in this workflow.
        incomingNote?.let { note ->
            val senderFirstName = note.authorName.ifEmpty { "Member" }.trim().split(" ").first()
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A84FF), RoundedCornerShape(14.dp))
                    .clickable { controller.consumeIncomingNote() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Message,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "$senderFirstName: ${note.text}",
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }


        // Floating Controls Column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(32.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Zoom controls
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { scale = (scale * 1.25f).coerceAtMost(5f) },
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                ) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.offset(y = (-1).dp))
                }
                SmallFloatingActionButton(
                    onClick = { scale = (scale / 1.25f).coerceAtLeast(0.1f) },
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Compass FAB (Small floating button to center canvas)
            SmallFloatingActionButton(
                onClick = {
                    val nodes = workflow?.nodes ?: emptyList()
                    if (nodes.isNotEmpty()) {
                        var minX = Float.MAX_VALUE
                        var maxX = Float.MIN_VALUE
                        var minY = Float.MAX_VALUE
                        var maxY = Float.MIN_VALUE
                        nodes.forEach { node ->
                            if (node.positionX < minX) minX = node.positionX
                            if (node.positionX > maxX) maxX = node.positionX
                            if (node.positionY < minY) minY = node.positionY
                            if (node.positionY > maxY) maxY = node.positionY
                        }
                        val centerNodesX = (minX + maxX + 160f) / 2f
                        val centerNodesY = (minY + maxY + 120f) / 2f
                        
                        val viewCenterX = viewportSize.width / 2f
                        val viewCenterY = viewportSize.height / 2f
                        
                        offset = Offset(viewCenterX - centerNodesX * scale, viewCenterY - centerNodesY * scale)
                    } else {
                        offset = Offset.Zero
                    }
                },
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Compass,
                    contentDescription = "Center Viewport",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Add Node FAB
            SmallFloatingActionButton(
                onClick = { showNodeSelector = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.offset(y = (-1).dp))
            }
        }

        // Node Selector BottomSheet
        if (showNodeSelector) {
            var selectedTab by remember { mutableStateOf(0) }
            ModalBottomSheet(
                onDismissRequest = { showNodeSelector = false },
                containerColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                ) {
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .background(Color(0xFFF0F0F2), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        val tabs = listOf("Nodes", "Templates")
                        val tabWidth = maxWidth / tabs.size
                        val animatedOffset by androidx.compose.animation.core.animateDpAsState(
                            targetValue = tabWidth * selectedTab,
                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        )

                        // Animated Pill Background
                        Box(
                            modifier = Modifier
                                .offset(x = animatedOffset)
                                .width(tabWidth)
                                .height(40.dp)
                                .shadow(1.dp, RoundedCornerShape(8.dp))
                                .background(Color.White, RoundedCornerShape(8.dp))
                        )

                        // Tab Texts
                        Row(modifier = Modifier.fillMaxWidth()) {
                            tabs.forEachIndexed { index, title ->
                                val isSelected = selectedTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) { selectedTab = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val textColor by androidx.compose.animation.animateColorAsState(if (isSelected) Color(0xFF1A1A1A) else Color(0xFF8E8E93))
                                    Text(
                                        title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (selectedTab == 0 || selectedTab == 1) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val displayNodes = if (selectedTab == 0) {
                                NodeType.values().filter { it != NodeType.MODEL3D_GENERATION && it != NodeType.AD_LOCALIZATION && it != NodeType.MARKETING_STOCK_IMAGE && it != NodeType.PRODUCT_AD && it != NodeType.PRODUCT_CAMPAIGN && it != NodeType.PRODUCT_SWAP && it != NodeType.MULTI_SHOT_VIDEO && it != NodeType.PRODUCT_UGC }
                            } else {
                                listOf(NodeType.AD_LOCALIZATION, NodeType.MARKETING_STOCK_IMAGE, NodeType.PRODUCT_AD, NodeType.PRODUCT_SWAP, NodeType.MULTI_SHOT_VIDEO, NodeType.PRODUCT_UGC)
                            }
                            
                            displayNodes.forEach { type ->
                                val (nodeColor, icon) = getNodeTheme(type)
                                Card(
                                    onClick = {
                                        val centerX = if (viewportSize.width > 0) (viewportSize.width / 2f - offset.x) / scale else 100f
                                        val centerY = if (viewportSize.height > 0) (viewportSize.height / 2f - offset.y) / scale else 100f
                                        // Offset the node slightly so its center (assumed 110f width) is perfectly aligned
                                        controller.addNode(type, centerX - 110f, centerY - 50f)
                                        showNodeSelector = false
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(60.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F7)),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(36.dp).background(nodeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = type.nodeName, modifier = Modifier.size(18.dp), tint = nodeColor)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    if (type.nodeName.contains("Generation")) type.nodeName.replace(" Generation", "") else type.nodeName,
                                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val costText = when (type) {
                                                    NodeType.TEXT_PROMPT, NodeType.UPLOADED_IMAGE, NodeType.NOTE -> "0c"
                                                    NodeType.IMAGE_GENERATION -> "1-60c"
                                                    NodeType.IMAGE_TO_VIDEO -> "8-60c/s"
                                                    NodeType.PRODUCT_AD -> "35-38c/s"
                                                    NodeType.PRODUCT_SWAP -> "38-40c/s"
                                                    NodeType.PRODUCT_UGC -> "33-35c/s"
                                                    NodeType.MULTI_SHOT_VIDEO -> "10-13c/s"
                                                    NodeType.AD_LOCALIZATION -> "18c"
                                                    NodeType.MARKETING_STOCK_IMAGE -> "21-80c"
                                                    NodeType.PRODUCT_CAMPAIGN -> "100c"
                                                    NodeType.MODEL3D_GENERATION -> "2c"
                                                    NodeType.TEXT_TO_SPEECH -> "1c"
                                                    else -> "1c"
                                                }
                                                Text(
                                                    text = "($costText)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = nodeColor
                                                )
                                            }
                                            Text(getNodeDescription(type), fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Text("+", fontSize = 20.sp, color = Color.Gray, fontWeight = FontWeight.Light)
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }

        // Node Notes BottomSheet (collaboration)
        if (notesNodeId != null) {
            NodeNotesBottomSheet(
                nodeId = notesNodeId!!,
                controller = controller,
                onDismissRequest = { notesNodeId = null }
            )
        }

        // Node Inspector BottomSheet
        if (selectedNodeId != null) {
            val selectedNode = workflow?.nodes?.find { it.id == selectedNodeId }
            if (selectedNode != null) {
                // Broadcast OPEN_NODE so others see a persistent border while inspector is open
                LaunchedEffect(selectedNodeId) {
                    controller.onNodeOpen(selectedNode.id)
                }
                NodeInspectorBottomSheet(
                    node = selectedNode,
                    controller = controller,
                    onDismissRequest = {
                        controller.onNodeClose()
                        selectedNodeId = null
                    },
                    onRunNode = {
                        controller.onNodeRunning(selectedNode.id)
                        prepareRun(RunRequest.Node(selectedNode.id))
                        selectedNodeId = null
                    }
                )
            }
        }


        if (showPurchaseCreditsSheet) {
            remember {
                PocketFlowPurchases.configure()
            }
            
            val paywallOptions = remember {
                PaywallOptions(
                    dismissRequest = { 
                        showPurchaseCreditsSheet = false
                        // Refresh credits after paywall closes
                        PocketFlowPurchases.refreshVirtualCurrencies(forceRefresh = true)
                    }
                )
            }
            Paywall(options = paywallOptions)
        }

        creditCheckState?.let { state ->
            ModalBottomSheet(
                onDismissRequest = {
                    creditCheckState = null
                    isCheckingCredits = false
                },
                containerColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.3f),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            state.isLoading -> "Checking credits"
                            state.hasEnoughCredits -> "Confirm credit spend"
                            else -> "Purchase credits"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            state.isLoading -> "Fetching your latest balance..."
                            state.hasEnoughCredits -> "This ${state.actionName} will use credits before it continues."
                            else -> "You need more credits to continue."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF666666)
                    )
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.isLoading) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF0EA5E9))
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Available", color = Color(0xFF666666), fontSize = 13.sp)
                                    Text("${state.availableCredits ?: 0} credits", fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Deduction", color = Color(0xFF666666), fontSize = 13.sp)
                                    Text("-${state.requiredCredits} credits", fontWeight = FontWeight.SemiBold, color = Color(0xFFE53935))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Remaining", color = Color(0xFF666666), fontSize = 13.sp)
                                    Text("${state.remainingCredits} credits", fontWeight = FontWeight.SemiBold, color = Color(0xFF34C759))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    if (state.isLoading) {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Checking...", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    } else if (state.hasEnoughCredits) {
                        Button(
                            onClick = {
                                val request = state.request
                                val deduction = state.requiredCredits
                                creditCheckState = null
                                continueRun(request, deduction)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Continue", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                creditCheckState = null
                                showPurchaseCreditsSheet = true
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Purchase credits", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                    TextButton(
                        onClick = { creditCheckState = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        }

        if (showWorkflowRunSheet) {
            val nodesNeedingRun = remember(workflow) {
                PocketFlowPurchases.getNodesNeedingRun(workflow)
            }
            val totalGenerativeNodes = remember(workflow) {
                workflow?.nodes?.filter { it.type != NodeType.TEXT_PROMPT } ?: emptyList()
            }
            val emptyCredits = remember(workflow) {
                PocketFlowPurchases.estimateWorkflowCredits(workflow, onlyEmpty = true)
            }
            val allCredits = remember(workflow) {
                PocketFlowPurchases.estimateWorkflowCredits(workflow, onlyEmpty = false)
            }
            val completedNodesCount = totalGenerativeNodes.size - nodesNeedingRun.size

            ModalBottomSheet(
                onDismissRequest = { showWorkflowRunSheet = false },
                containerColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp, top = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFF007AFF).copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Play,
                                contentDescription = null,
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "Run Workflow",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                            Text(
                                "${workflow?.name ?: "Workflow"} · Branch-wise execution",
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FB)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE9ECEF))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Empty / Pending Nodes", fontSize = 13.sp, color = Color(0xFF666666))
                                Surface(
                                    color = if (nodesNeedingRun.isNotEmpty()) Color(0xFF007AFF).copy(alpha = 0.12f) else Color(0xFF34C759).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (nodesNeedingRun.isNotEmpty()) "${nodesNeedingRun.size} to run" else "All completed",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (nodesNeedingRun.isNotEmpty()) Color(0xFF007AFF) else Color(0xFF34C759)
                                    )
                                }
                            }

                            if (completedNodesCount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Already Generated", fontSize = 13.sp, color = Color(0xFF666666))
                                    Text("$completedNodesCount cached", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF34C759))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Estimated Cost (Empty Nodes)", fontSize = 13.sp, color = Color(0xFF666666))
                                Text(
                                    if (emptyCredits > 0) "$emptyCredits credits" else "Free",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons
                    Button(
                        onClick = {
                            showWorkflowRunSheet = false
                            prepareRun(RunRequest.Workflow(onlyEmpty = true))
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(AppIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                if (nodesNeedingRun.isNotEmpty()) "Run ${nodesNeedingRun.size} Empty Node${if (nodesNeedingRun.size > 1) "s" else ""} Branch-Wise" else "Re-run Branch-Wise",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (completedNodesCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showWorkflowRunSheet = false
                                prepareRun(RunRequest.Workflow(onlyEmpty = false))
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                        ) {
                            Text("Re-run All Nodes from Beginning ($allCredits credits)", fontSize = 12.sp, color = Color(0xFF666666), fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { showWorkflowRunSheet = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancel", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        }

        if (viewingMediaUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { viewingMediaUrl = null },
                contentAlignment = Alignment.Center
            ) {
                val isVideo = viewingMediaUrl!!.contains(".mp4", ignoreCase = true) || viewingMediaUrl!!.contains(".webm", ignoreCase = true)
                val isAudio = viewingMediaUrl!!.contains(".mp3", ignoreCase = true) || viewingMediaUrl!!.contains(".wav", ignoreCase = true)
                
                if (isVideo) {
                    var isPaused by remember { mutableStateOf(false) }
                    Box(modifier = Modifier
                        .fillMaxWidth(if (isPortraitPreview) 0.55f else 0.95f)
                        .aspectRatio(if (isPortraitPreview) 9f/16f else 16f/9f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { isPaused = !isPaused }
                    ) {
                        app.ak25.pocketflow.ui.editor.AsyncVideoPlayer(
                            url = viewingMediaUrl!!,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (isPortraitPreview) Modifier.graphicsLayer(scaleX = 1.25f, scaleY = 1.25f) else Modifier),
                            isMiniature = false,
                            isPaused = isPaused
                        )
                        if (isPaused) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                                    .clip(androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = AppIcons.Play,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                } else if (isAudio) {
                    var isPaused by remember { mutableStateOf(true) }
                    Box(modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFF5F5F7))
                        .padding(32.dp)
                    ) {
                        if (!isPaused) {
                            app.ak25.pocketflow.ui.editor.AsyncVideoPlayer(
                                url = viewingMediaUrl!!,
                                modifier = Modifier.size(0.dp),
                                isMiniature = false,
                                isPaused = isPaused,
                                loop = false,
                                onEnd = { isPaused = true }
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0xFFE8F5E9), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = AppIcons.Audio,
                                    contentDescription = "Audio",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            Text("Speech Output", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF1A1A1A))
                            Spacer(Modifier.height(8.dp))
                            Text(if (isPaused) "Paused" else "Playing audio...", color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.height(32.dp))
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable { isPaused = !isPaused },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = if (isPaused) AppIcons.Play else AppIcons.Pause,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp).offset(x = if (isPaused) 2.dp else 0.dp)
                                )
                            }
                        }
                    }
                } else {
                    val urls = viewingMediaUrl!!.split(",")
                    if (urls.size == 1) {
                        AsyncMediaPreview(
                            uri = urls[0],
                            modifier = Modifier.fillMaxWidth().padding(32.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } else {
                        val pagerState = androidx.compose.foundation.pager.rememberPagerState { urls.size }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
                            ) { page ->
                                AsyncMediaPreview(
                                    uri = urls[page],
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                            
                            // Pager indicator
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 64.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(urls.size) { i ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (pagerState.currentPage == i) 10.dp else 8.dp)
                                            .background(
                                                if (pagerState.currentPage == i) Color.White else Color.White.copy(alpha = 0.5f),
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 32.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    if (isVideo) {
                        androidx.compose.material3.IconButton(
                            onClick = { isPortraitPreview = !isPortraitPreview },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Text(
                                text = if (isPortraitPreview) "9:16" else "16:9",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    androidx.compose.material3.IconButton(
                        onClick = { 
                            val urls = viewingMediaUrl!!.split(",")
                            val targetUrl = urls.first()
                            
                            coroutineScope.launch {
                                var path = targetUrl
                                val workflowNode = workflow?.nodes?.find { it.outputUrl == targetUrl }
                                if (workflowNode?.outputLocalPath != null) {
                                    path = workflowNode.outputLocalPath!!
                                }
                                
                                val isRemote = path.startsWith("http://") || path.startsWith("https://")
                                val localPath = if (isRemote) {
                                    errorMessage = "Downloading..."
                                    try {
                                        val client = HttpClient()
                                        val response = client.get(path)
                                        val bytes = response.readBytes()
                                        client.close()
                                        val ext = if (path.contains(".mp4", ignoreCase = true) || path.contains(".webm", ignoreCase = true)) "mp4" else if (path.contains(".mp3", ignoreCase = true) || path.contains(".wav", ignoreCase = true)) "mp3" else "png"
                                        app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, ext)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    path
                                }

                                if (localPath != null) {
                                    val saved = app.ak25.pocketflow.storage.LocalStorage.exportMediaToGallery(localPath)
                                    if (saved) {
                                        errorMessage = "Saved successfully!"
                                    } else {
                                        errorMessage = "Failed to export media."
                                    }
                                } else {
                                    errorMessage = "Failed to download media."
                                }
                            }
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = AppIcons.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    androidx.compose.material3.IconButton(
                        onClick = { 
                            val urls = viewingMediaUrl!!.split(",")
                            val targetUrl = urls.first()
                            
                            coroutineScope.launch {
                                var path = targetUrl
                                val workflowNode = workflow?.nodes?.find { it.outputUrl == targetUrl }
                                if (workflowNode?.outputLocalPath != null) {
                                    path = workflowNode.outputLocalPath!!
                                }
                                
                                val isRemote = path.startsWith("http://") || path.startsWith("https://")
                                val localPath = if (isRemote) {
                                    errorMessage = "Downloading for share..."
                                    try {
                                        val client = HttpClient()
                                        val response = client.get(path)
                                        val bytes = response.readBytes()
                                        client.close()
                                        val ext = if (path.contains(".mp4", ignoreCase = true) || path.contains(".webm", ignoreCase = true)) "mp4" else if (path.contains(".mp3", ignoreCase = true) || path.contains(".wav", ignoreCase = true)) "mp3" else "png"
                                        app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, ext)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    path
                                }

                                if (localPath != null) {
                                    val shared = app.ak25.pocketflow.storage.LocalStorage.shareMedia(localPath)
                                    if (!shared) {
                                        errorMessage = "Unable to share media."
                                    }
                                } else {
                                    errorMessage = "Failed to download media for share."
                                }
                            }
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = AppIcons.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    androidx.compose.material3.IconButton(
                        onClick = { viewingMediaUrl = null },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                }
            }
        }
        
        // Pending Connection Dialog
        if (pendingConnection != null) {
            val conn = pendingConnection!!
            val targetNode = workflow?.nodes?.find { it.id == conn.targetNodeId }
            AlertDialog(
                onDismissRequest = { pendingConnection = null },
                title = { Text("Select Target Port", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Where do you want to connect this image?", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        conn.compatiblePorts.forEach { port ->
                            Button(
                                onClick = {
                                    val success = controller.connectNodes(conn.sourceNodeId, conn.sourcePortId, conn.targetNodeId, port.id)
                                    if (!success) {
                                        errorMessage = "Cannot connect: Port connection limit reached!"
                                    }
                                    pendingConnection = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(port.id, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { pendingConnection = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color.White
            )
        }

        if (showRenameDialog) {
            ModalBottomSheet(
                onDismissRequest = { showRenameDialog = false },
                containerColor = Color.Transparent,
                dragHandle = null,
                scrimColor = Color.Black.copy(alpha = 0.3f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Rename Workflow",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = newWorkflowName,
                            onValueChange = { if (it.length <= 20) newWorkflowName = it },
                            placeholder = { Text("Rename your workflow...", color = Color.Gray) },
                            supportingText = {
                                Text(
                                    "${newWorkflowName.length}/20",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    fontSize = 11.sp,
                                    color = if (newWorkflowName.length >= 20) Color(0xFFFF3B30) else Color.Gray
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1A1A1A),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedTextColor = Color(0xFF1A1A1A),
                                unfocusedTextColor = Color(0xFF1A1A1A),
                                cursorColor = Color(0xFF1A1A1A)
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (newWorkflowName.isNotBlank() && workflow != null) {
                                    controller.renameWorkflow(workflow!!.id, newWorkflowName.trim().take(20))
                                }
                                showRenameDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(2500)
                errorMessage = null
            }
            val isSuccessMsg = errorMessage?.contains("successfully", ignoreCase = true) == true
            val alertBgColor = if (isSuccessMsg) Color(0xFF34C759) else Color(0xFFE53935)
            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 80.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .background(alertBgColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(errorMessage!!, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBezier(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val path = Path()
    path.moveTo(start.x, start.y)
    val distance = kotlin.math.abs(end.x - start.x)
    val controlPoint1 = Offset(start.x + distance / 2, start.y)
    val controlPoint2 = Offset(end.x - distance / 2, end.y)
    path.cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, end.x, end.y)
    drawPath(path, color = color, style = Stroke(width = strokeWidth))
}

fun getBezierPoint(start: Offset, end: Offset, t: Float): Offset {
    val dx = kotlin.math.abs(end.x - start.x) * 0.5f
    val p0 = start
    val p1 = Offset(start.x + dx, start.y)
    val p2 = Offset(end.x - dx, end.y)
    val p3 = end
    
    val u = 1 - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t
    
    return p0 * uuu + p1 * (3 * uu * t) + p2 * (3 * u * tt) + p3 * ttt
}

// distanceToSegment and isTapNearEdge are defined in EditorUtils.kt

// getNodeTheme and getNodeDescription are defined in EditorUtils.kt
