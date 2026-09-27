package app.ak25.pocketflow.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.ak25.pocketflow.ui.utils.ImageCache
import app.ak25.pocketflow.ui.utils.toImageBitmap
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.NodeStatus
import app.ak25.pocketflow.models.NodeType
import app.ak25.pocketflow.models.PortDataType
import app.ak25.pocketflow.models.WorkflowNode
import app.ak25.pocketflow.services.NodeAction
import app.ak25.pocketflow.services.NodePresenceState

@Composable
fun NodeUI(
    node: WorkflowNode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    controller: WorkflowController,
    onRunNode: () -> Unit,
    onPortPositioned: (portId: String, isInput: Boolean, x: Float, y: Float) -> Unit,
    onPortDragStarted: (portId: String) -> Unit,
    onPortDrag: (dragAmountX: Float, dragAmountY: Float) -> Unit,
    onPortDragEnded: (portId: String) -> Unit,
    onViewMedia: (String) -> Unit,
    viewingMediaUrl: String?,
    onOpenNotes: () -> Unit = {},
    connectionHintColor: Color? = null,
    remoteHolders: List<NodePresenceState> = emptyList(),  // ← presence data
    modifier: Modifier = Modifier
) {


    var offsetX by remember { mutableStateOf(node.positionX) }
    var offsetY by remember { mutableStateOf(node.positionY) }
    var isDragging by remember { mutableStateOf(false) }
    // Track the last position we committed on drag-end so we don't snap back
    // while waiting for the cloud round-trip to update node.positionX/Y.
    var lastDragEndX by remember { mutableStateOf<Float?>(null) }
    var lastDragEndY by remember { mutableStateOf<Float?>(null) }

    // Port positions relative to the node's top-left (canvas space, drag-independent).
    val portLocalPositions = remember { mutableStateMapOf<String, Pair<Boolean, androidx.compose.ui.geometry.Offset>>() }

    fun pushPortPositions() {
        portLocalPositions.forEach { (portId, entry) ->
            onPortPositioned(portId, entry.first, entry.second.x + offsetX, entry.second.y + offsetY)
        }
    }

    LaunchedEffect(node.positionX, node.positionY) {
        if (!isDragging) {
            offsetX = node.positionX
            offsetY = node.positionY
            // Cloud position has caught up — clear the pending drag-end values.
            lastDragEndX = null
            lastDragEndY = null
            pushPortPositions()
        }
    }

    // Remote dragger position is applied inside the graphicsLayer block (fast path).
    val remoteDragger = remoteHolders.firstOrNull { it.action == NodeAction.DRAGGING }

    var lastRemoteEndX by remember(node.id) { mutableStateOf<Float?>(null) }
    var lastRemoteEndY by remember(node.id) { mutableStateOf<Float?>(null) }

    LaunchedEffect(remoteDragger) {
        if (remoteDragger != null) {
            lastRemoteEndX = remoteDragger.cursorX
            lastRemoteEndY = remoteDragger.cursorY
        }
    }

    LaunchedEffect(node.positionX, node.positionY) {
        if (!isDragging) {
            lastRemoteEndX = null
            lastRemoteEndY = null
        }
    }


    val (nodeColor, icon) = getNodeTheme(node.type)
    var nodeCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    val primaryHolder = remoteHolders.firstOrNull()
    // Border color priority: connection hint > local drag/select > remote holder > default
    val isRemoteInspecting = primaryHolder?.action == NodeAction.INSPECTING
    val isRemoteOpenNode   = primaryHolder?.action == NodeAction.OPEN_NODE
    val isRemoteRunning    = remoteHolders.any { it.action == NodeAction.RUNNING }
    val borderColor = connectionHintColor
        ?: when {
            isSelected || isDragging -> nodeColor
            isRemoteRunning          -> Color(0xFFFF9500)  // orange = someone is running
            isRemoteOpenNode         -> primaryHolder?.color ?: nodeColor  // solid = inspector open
            isRemoteInspecting       -> primaryHolder?.color?.copy(alpha = 0.6f) ?: nodeColor
            primaryHolder != null    -> primaryHolder.color
            else                     -> Color(0xFFE5E5EA)
        }
    val borderWidth = when {
        connectionHintColor != null || isSelected || isDragging -> 3.dp
        isRemoteRunning                                          -> 3.dp
        isRemoteOpenNode                                         -> 3.dp
        primaryHolder != null && !isRemoteInspecting             -> 3.dp
        primaryHolder != null && isRemoteInspecting              -> 2.dp
        else                                                     -> 1.dp
    }

    Box(
        modifier = modifier
            // GPU-only transform: reading the drag state inside the layer block
            // updates the layer without recomposing the node or re-laying out the
            // parent canvas — this is what keeps Android dragging buttery smooth.
            .graphicsLayer {
                 translationX = when {
                    !isDragging && remoteDragger != null -> remoteDragger.cursorX
                    !isDragging && lastRemoteEndX != null -> lastRemoteEndX!!
                    lastDragEndX != null -> lastDragEndX!!
                    else -> offsetX
                }
                translationY = when {
                    !isDragging && remoteDragger != null -> remoteDragger.cursorY
                    !isDragging && lastRemoteEndY != null -> lastRemoteEndY!!
                    lastDragEndY != null -> lastDragEndY!!
                    else -> offsetY
                }
            }
            .width(220.dp)
            .onGloballyPositioned { nodeCoordinates = it }
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controller.onNodeSelect(node.id)
                        onSelect()
                    },
                    onLongPress = {
                        controller.onNodeHold(node.id)
                        onLongPress()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startPos ->
                        isDragging = true
                        controller.onNodeDragStart(node.id, offsetX + startPos.x, offsetY + startPos.y)
                    },
                    onDragEnd = {
                        isDragging = false
                        // Latch the final position so the graphicsLayer keeps it
                        // while we wait for the cloud round-trip.
                        lastDragEndX = offsetX
                        lastDragEndY = offsetY
                        controller.updateNodePosition(node.id, offsetX, offsetY)
                        controller.onNodeDragEnd(node.id)
                    },
                    onDragCancel = {
                        isDragging = false
                        lastDragEndX = null
                        lastDragEndY = null
                        controller.onNodeDragEnd(node.id)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    // Keep ports (and thus edges) tracking the node per frame.
                    pushPortPositions()
                    // Broadcast live cursor position during drag (throttled in controller)
                    controller.onNodeDrag(node.id, offsetX, offsetY)
                }
            }
    ) {

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(nodeColor.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(icon, contentDescription = null, tint = nodeColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (node.type.nodeName.contains("Generation")) node.type.nodeName.replace(" Generation", "") else node.type.nodeName,
                        color = nodeColor, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val assigneeName = node.params["assignee_name"]
                    if (!assigneeName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = nodeColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "👤 $assigneeName",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = nodeColor,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isGuest = app.ak25.pocketflow.storage.LocalStorage.loadString("is_guest") == "true"
                    if (!isGuest) {
                        val noteCount = node.notes.size
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(nodeColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenNotes),
                            contentAlignment = Alignment.Center
                        ) {
                            Box {
                                Icon(AppIcons.Message, contentDescription = "Notes", tint = nodeColor, modifier = Modifier.size(16.dp))
                                if (noteCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-6).dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF007AFF))
                                            .padding(horizontal = 4.dp, vertical = 0.5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (noteCount > 9) "9+" else "$noteCount",
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val localExists = if (!node.outputLocalPath.isNullOrEmpty()) {
                        try {
                            val firstPath = node.outputLocalPath!!.split(",").firstOrNull()?.trim() ?: node.outputLocalPath!!
                            app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(firstPath) != null
                        } catch (e: Exception) { false }
                    } else false
                    val mediaUrl = if (localExists && !node.outputLocalPath.isNullOrEmpty()) {
                        node.outputLocalPath
                    } else {
                        node.outputUrl ?: (if (node.type == NodeType.UPLOADED_IMAGE) node.params["imageUri"] else null)
                    }
                    val hasOutput = !mediaUrl.isNullOrBlank()
                    if (hasOutput && node.type != NodeType.MARKETING_STOCK_IMAGE && node.type != NodeType.PRODUCT_CAMPAIGN) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(nodeColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable(onClick = { onViewMedia(mediaUrl) }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Eye, contentDescription = "View", tint = nodeColor, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (node.type != NodeType.UPLOADED_IMAGE && node.type != NodeType.NOTE) {
                        val remoteIsRunning = remoteHolders.any { it.action == NodeAction.RUNNING }
                        val runBlocked = node.status == NodeStatus.RUNNING || remoteIsRunning
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (remoteIsRunning) Color(0xFFFF9500).copy(alpha = 0.15f)
                                    else nodeColor.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { if (!runBlocked) onRunNode() },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                node.status == NodeStatus.RUNNING ->
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = nodeColor)
                                remoteIsRunning ->
                                    Text("▶", color = Color(0xFFFF9500).copy(alpha = 0.5f), fontSize = 11.sp)
                                else ->
                                    Text("▶", color = nodeColor, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            
            // Body
            Column(modifier = Modifier.background(Color.White).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                val workflow by controller.currentWorkflow.collectAsState()
                val connectedEdges = workflow?.edges?.filter { it.targetNodeId == node.id } ?: emptyList()
                val allNodes = workflow?.nodes ?: emptyList()

                fun resolveParam(portId: String, paramKey: String): String? {
                    var v = node.params[paramKey]?.takeIf { it.isNotBlank() }
                    if (v == null) {
                        val edge = connectedEdges.find { it.targetPortId == portId }
                        if (edge != null) {
                            val sourceNode = allNodes.find { it.id == edge.sourceNodeId && it.type == NodeType.TEXT_PROMPT }
                            if (sourceNode != null) {
                                v = sourceNode.params["text"]?.takeIf { it.isNotBlank() }
                            }
                        }
                    }
                    return v
                }

                val paramSummary = when (node.type) {
                    NodeType.IMAGE_GENERATION -> resolveParam("prompt", "prompt") ?: "No prompt"
                    NodeType.UPLOADED_IMAGE -> {
                        val guestLocal = app.ak25.pocketflow.storage.LocalStorage.loadString("is_guest") == "true" &&
                            !node.params["imageUri"].isNullOrBlank()
                        when {
                            node.outputUrl?.startsWith("http") == true -> "Image uploaded ✓"
                            node.params["imageUri"]?.startsWith("http") == true -> "Image uploaded ✓"
                            guestLocal -> "Image uploaded ✓"
                            node.params["imageUri"].isNullOrBlank() -> "No image"
                            else -> "Uploading…"
                        }
                    }
                    NodeType.IMAGE_TO_VIDEO -> resolveParam("prompt", "prompt") ?: (node.params["model"] ?: "veo3.1_fast")
                    NodeType.TEXT_TO_SPEECH -> resolveParam("prompt", "text") ?: "No text"
                    NodeType.TEXT_PROMPT -> node.params["text"]?.takeIf { it.isNotBlank() } ?: "Empty prompt"
                    NodeType.MODEL3D_GENERATION -> "Image to 3D Model"
                    NodeType.AD_LOCALIZATION -> "Language: ${node.params["targetLanguage"] ?: "es"}"
                    NodeType.MARKETING_STOCK_IMAGE -> "Count: ${node.params["outputCount"] ?: "4"}"
                    NodeType.PRODUCT_AD -> "Duration: ${node.params["duration"] ?: "10"}s"
                    NodeType.PRODUCT_CAMPAIGN -> node.params["prompt"]?.takeIf { it.isNotBlank() } ?: "Empty prompt"
                    NodeType.PRODUCT_SWAP -> "Res: ${node.params["resolution"] ?: "720p"}"
                    NodeType.MULTI_SHOT_VIDEO -> "Duration: ${node.params["duration"] ?: "10"}s"
                    NodeType.PRODUCT_UGC -> "Duration: ${node.params["duration"] ?: "15"}s"
                    NodeType.NOTE -> if (node.params["mode"] == "todo") "Checklist" else "Notes"
                }
                
                Text(paramSummary, color = Color(0xFFAAAAAA), fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

                // Notes node — standalone note / todo checklist
                if (node.type == NodeType.NOTE) {
                    val noteMode = node.params["mode"] ?: "note"
                    if (noteMode == "todo") {
                        val items = node.params["items"].orEmpty().split("\n").filter { it.isNotBlank() }
                        val checked = node.params["checked"].orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                        if (items.isEmpty()) {
                            Text(
                                "Tap to add checklist items",
                                color = Color(0xFFAAAAAA), fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            val checkedMutable = checked.toMutableSet()
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items.forEachIndexed { index, item ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val isNowChecked = index !in checkedMutable
                                                val updated = if (index in checkedMutable) checkedMutable - index else checkedMutable + index
                                                controller.updateNodeParams(node.id, "checked", updated.sorted().joinToString(","))
                                                controller.notifyNoteChecklistToggled(node.id, item, isNowChecked)
                                            }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(
                                                    if (index in checkedMutable) nodeColor else Color(0xFFE5E5EA),
                                                    RoundedCornerShape(4.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (index in checkedMutable) {
                                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                                    val width = size.width
                                                    val height = size.height
                                                    val path = androidx.compose.ui.graphics.Path().apply {
                                                        moveTo(width * 0.15f, height * 0.5f)
                                                        lineTo(width * 0.4f, height * 0.75f)
                                                        lineTo(width * 0.85f, height * 0.2f)
                                                    }
                                                    drawPath(
                                                        path = path,
                                                        color = Color.White,
                                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                            width = 2.dp.toPx(),
                                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            item,
                                            color = if (index in checkedMutable) Color(0xFFAAAAAA) else Color(0xFF3A3A3C),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            textDecoration = if (index in checkedMutable) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val content = node.params["content"].orEmpty()
                        if (content.isBlank()) {
                            Text(
                                "Tap to write a note",
                                color = Color(0xFFAAAAAA), fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            Text(
                                content,
                                color = Color(0xFF3A3A3C),
                                fontSize = 11.sp,
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                
                val localExists = if (!node.outputLocalPath.isNullOrEmpty()) {
                    try {
                        val firstPath = node.outputLocalPath!!.split(",").firstOrNull()?.trim() ?: node.outputLocalPath!!
                        app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(firstPath) != null
                    } catch (e: Exception) { false }
                } else false
                val mediaUrl = if (localExists && !node.outputLocalPath.isNullOrEmpty()) {
                    node.outputLocalPath
                } else {
                    node.outputUrl ?: (if (node.type == NodeType.UPLOADED_IMAGE) node.params["imageUri"] else null)
                }
                val isCompleted = node.status == NodeStatus.COMPLETED || (node.type == NodeType.UPLOADED_IMAGE && !mediaUrl.isNullOrBlank())

                if (isCompleted && !mediaUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        if (node.type == NodeType.IMAGE_GENERATION || node.type == NodeType.UPLOADED_IMAGE || node.type == NodeType.AD_LOCALIZATION) {
                            AsyncMediaPreview(
                                uri = mediaUrl,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                // UPLOADED_IMAGE reuses a deterministic URL when replaced, so
                                // refresh via the imageVersion stamped on each successful set.
                                refreshKey = if (node.type == NodeType.UPLOADED_IMAGE) node.params["imageVersion"] else null
                            )
                        } else if (node.type == NodeType.MARKETING_STOCK_IMAGE || node.type == NodeType.PRODUCT_CAMPAIGN) {
                            val urls = mediaUrl.split(",")
                            if (urls.size == 1) {
                                AsyncMediaPreview(
                                    uri = urls[0],
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable {
                                        onViewMedia(mediaUrl)
                                    }
                                )
                            } else {
                                val pagerState = androidx.compose.foundation.pager.rememberPagerState { urls.size }
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    androidx.compose.foundation.pager.HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                        AsyncMediaPreview(
                                            uri = urls[page],
                                            modifier = Modifier.fillMaxSize().clickable {
                                                onViewMedia(mediaUrl)
                                            },
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                    
                                    // Pager indicator
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        repeat(urls.size) { i ->
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        if (pagerState.currentPage == i) Color.White else Color.White.copy(alpha = 0.5f),
                                                        androidx.compose.foundation.shape.CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (node.type == NodeType.IMAGE_TO_VIDEO || node.type == NodeType.PRODUCT_AD || node.type == NodeType.PRODUCT_SWAP || node.type == NodeType.MULTI_SHOT_VIDEO || node.type == NodeType.PRODUCT_UGC) {
                            if (mediaUrl == viewingMediaUrl) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.8f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Playing in Full Screen", color = Color.White, fontSize = 12.sp)
                                }
                            } else {
                                AsyncVideoPlayer(
                                    url = mediaUrl,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp))
                                )
                            }
                        } else if (node.type == NodeType.TEXT_TO_SPEECH) {
                            var isAudioPaused by remember { mutableStateOf(true) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isAudioPaused = !isAudioPaused }
                                    .background(Color(0xFFE8F5E9))
                                    .padding(8.dp)
                            ) {
                                AsyncVideoPlayer(
                                    url = mediaUrl,
                                    modifier = Modifier.size(1.dp).alpha(0.001f),
                                    isMiniature = false,
                                    isPaused = isAudioPaused,
                                    loop = false,
                                    onEnd = { isAudioPaused = true }
                                )
                                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = if (isAudioPaused) AppIcons.Play else AppIcons.Pause,
                                            contentDescription = "Play/Pause Audio",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                    Text("Speech Output", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Text(
                                "✅ Media Ready",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Inputs
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        node.type.inputs.forEach { port ->
                            val isConnected = connectedEdges.any { it.targetPortId == port.id }
                            PortUI(
                                name = port.id,
                                color = getDataTypeColor(port.dataType),
                                isInput = true,
                                isConnected = isConnected,
                                nodeCoordinates = nodeCoordinates,
                                onPositioned = { x, y ->
                                    portLocalPositions["${node.id}_${port.id}"] = true to androidx.compose.ui.geometry.Offset(x, y)
                                    pushPortPositions()
                                },
                                onDragStarted = { onPortDragStarted("${node.id}_${port.id}") },
                                onDrag = onPortDrag,
                                onDragEnded = { onPortDragEnded("${node.id}_${port.id}") }
                            )
                        }
                    }
                    // Outputs
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                        node.type.outputs.forEach { port ->
                            val isConnected = workflow?.edges?.any { it.sourceNodeId == node.id && it.sourcePortId == port.id } ?: false
                            PortUI(
                                name = port.id,
                                color = getDataTypeColor(port.dataType),
                                isInput = false,
                                isConnected = isConnected,
                                nodeCoordinates = nodeCoordinates,
                                onPositioned = { x, y ->
                                    portLocalPositions["${node.id}_${port.id}"] = false to androidx.compose.ui.geometry.Offset(x, y)
                                    pushPortPositions()
                                },
                                onDragStarted = { onPortDragStarted("${node.id}_${port.id}") },
                                onDrag = onPortDrag,
                                onDragEnded = { onPortDragEnded("${node.id}_${port.id}") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortUI(
    name: String,
    color: Color,
    isInput: Boolean,
    isConnected: Boolean,
    nodeCoordinates: androidx.compose.ui.layout.LayoutCoordinates?,
    onPositioned: (Float, Float) -> Unit,
    onDragStarted: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnded: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isInput) {
            Text(name.capitalize(), fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
        }
        
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isConnected) color else Color.White)
                .border(2.dp, color, CircleShape)
                .onGloballyPositioned { portCoordinates ->
                    if (nodeCoordinates != null && nodeCoordinates.isAttached && portCoordinates.isAttached) {
                        try {
                            val localCenter = nodeCoordinates.localPositionOf(
                                portCoordinates, 
                                androidx.compose.ui.geometry.Offset(portCoordinates.size.width / 2f, portCoordinates.size.height / 2f)
                            )
                            onPositioned(localCenter.x, localCenter.y)
                        } catch (e: Exception) {}
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStarted() },
                        onDragEnd = { onDragEnded() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
        )

        if (isInput) {
            Text(name.capitalize(), fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

fun getDataTypeColor(type: PortDataType): Color = when(type) {
    PortDataType.IMAGE -> Color(0xFFBA68C8)
    PortDataType.VIDEO -> Color(0xFFF06292)
    PortDataType.AUDIO -> Color(0xFFFFB74D)
    PortDataType.TEXT -> Color(0xFF4DD0E1)
    PortDataType.MODEL3D -> Color(0xFF81C784)
}

@Composable
fun AsyncMediaPreview(
    uri: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    refreshKey: Any? = null
) {
    var bitmap by remember(uri, refreshKey) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(uri, refreshKey) { mutableStateOf(false) }

    LaunchedEffect(uri, refreshKey) {
        if (uri.isBlank()) return@LaunchedEffect
        val singleUri = uri.split(",").firstOrNull()?.trim() ?: uri
        isLoading = true
        val bytes = app.ak25.pocketflow.ui.utils.MediaLoader.loadBytes(singleUri)
        if (bytes != null) {
            bitmap = bytes.toImageBitmap()
        }
        isLoading = false
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(bitmap = bitmap!!, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else if (isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    } else {
        Box(modifier = modifier.background(Color(0xFFF5F5F7)), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Eye, contentDescription = null, tint = Color.Gray)
        }
    }
}
