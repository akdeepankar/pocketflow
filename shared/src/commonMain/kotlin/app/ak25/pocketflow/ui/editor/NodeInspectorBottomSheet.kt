package app.ak25.pocketflow.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.NodeStatus
import app.ak25.pocketflow.models.NodeType
import app.ak25.pocketflow.models.WorkflowNode
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import app.ak25.pocketflow.ui.utils.ImageCache
import app.ak25.pocketflow.ui.utils.toImageBitmap
import androidx.compose.ui.draw.clip
import app.ak25.pocketflow.services.SupabaseRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeInspectorBottomSheet(
    node: WorkflowNode,
    controller: WorkflowController,
    onDismissRequest: () -> Unit,
    onRunNode: () -> Unit
) {
    val (nodeColor, icon) = getNodeTheme(node.type)
    val nodeLabel = if (node.type.nodeName.contains("Generation")) node.type.nodeName.replace(" Generation", "") else node.type.nodeName
    val coroutineScope = rememberCoroutineScope()
    
    var isUploadingImage by remember { mutableStateOf(false) }
    val singleImagePicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = coroutineScope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let { bytes ->
                ImageCache.put(node.id, bytes)
                controller.updateNodeParams(node.id, "imageUri", "cache://${node.id}")
                val isGuest = app.ak25.pocketflow.storage.LocalStorage.loadString("is_guest") == "true"
                if (isGuest) {
                    // Guests: keep the image local to the device — never upload to Supabase.
                    coroutineScope.launch {
                        val localPath = app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, "jpg")
                        if (localPath.isNotEmpty()) {
                            ImageCache.put(localPath, bytes)
                            controller.updateNodeParams(node.id, "imageUri", localPath)
                        }
                        controller.updateNodeStatus(
                            node.id,
                            app.ak25.pocketflow.models.NodeStatus.COMPLETED,
                            localPath = localPath
                        )
                        // Bump version so node-card previews refresh (see AsyncMediaPreview refreshKey)
                        controller.updateNodeParams(
                            node.id,
                            "imageVersion",
                            app.ak25.pocketflow.utils.getCurrentTimeMillis().toString()
                        )
                    }
                } else {
                    // Upload to Supabase Storage in background
                    isUploadingImage = true
                    coroutineScope.launch {
                        val url = SupabaseRepository.uploadImageFile(node.id, bytes)
                        isUploadingImage = false
                        if (url != null) {
                            ImageCache.put(url, bytes)
                            controller.updateNodeParams(node.id, "imageUri", url)
                            controller.updateNodeStatus(node.id, app.ak25.pocketflow.models.NodeStatus.COMPLETED, outputUrl = url)
                            // Bump version so node-card previews refresh (see AsyncMediaPreview refreshKey)
                            controller.updateNodeParams(
                                node.id,
                                "imageVersion",
                                app.ak25.pocketflow.utils.getCurrentTimeMillis().toString()
                            )
                        }
                    }
                }
            }
        }
    )

    val workflow by controller.currentWorkflow.collectAsState()
    val connectedEdges = workflow?.edges?.filter { it.targetNodeId == node.id } ?: emptyList()

    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    focusManager.clearFocus()
                }
                .padding(bottom = 32.dp)
        ) {
            // Header
            Column(modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 16.dp)) {
                CenterAlignedDragHandle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(nodeColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = nodeColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nodeLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        Text("Configure parameters", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                    }
                    val (statusColor, statusLabel) = when (node.status) {
                        NodeStatus.IDLE -> Color(0xFFAAAAAA) to "Idle"
                        NodeStatus.PENDING -> Color(0xFFFFA000) to "Pending"
                        NodeStatus.RUNNING -> Color(0xFF007AFF) to "Running"
                        NodeStatus.COMPLETED -> Color(0xFF34C759) to "Done"
                        NodeStatus.FAILED -> Color(0xFFE53935) to "Failed"
                    }
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                }
                
                val nodesWithoutPillBadge = listOf(NodeType.IMAGE_TO_VIDEO, NodeType.AD_LOCALIZATION, NodeType.MARKETING_STOCK_IMAGE, NodeType.PRODUCT_AD, NodeType.PRODUCT_CAMPAIGN, NodeType.PRODUCT_SWAP, NodeType.MULTI_SHOT_VIDEO, NodeType.PRODUCT_UGC)
                if (connectedEdges.isNotEmpty() && node.type !in nodesWithoutPillBadge) {
                    Spacer(modifier = Modifier.height(12.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        connectedEdges.forEach { edge ->
                            val sourceNode = workflow?.nodes?.find { it.id == edge.sourceNodeId }
                            if (sourceNode != null) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE8F0FE), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("🔗 ${sourceNode.type.nodeName}", fontSize = 10.sp, color = Color(0xFF1967D2), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // Fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                when (node.type) {
                    NodeType.IMAGE_GENERATION -> {
                        val refEdges = connectedEdges.filter { it.targetPortId == "reference" }
                        refEdges.forEachIndexed { index, edge ->
                            val src = workflow?.nodes?.find { it.id == edge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Reference Image ${index + 1}")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(edge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        
                        Label("Prompt")
                        val promptEdge = connectedEdges.find { it.targetPortId == "prompt" }
                        val promptSrc = promptEdge?.let { e -> workflow?.nodes?.find { it.id == e.sourceNodeId } }
                        if (promptSrc != null && promptSrc.type == app.ak25.pocketflow.models.NodeType.TEXT_PROMPT) {
                            LinkedTextPrompt(promptSrc.type.nodeName, promptSrc.params["text"] ?: "") {
                                controller.deleteEdge(promptEdge.id)
                            }
                        } else {
                            ConfigTextField(
                                value = node.params["prompt"] ?: "",
                                onValueChange = { controller.updateNodeParams(node.id, "prompt", it) },
                                hint = "Describe the image..."
                            )
                        }
                        val selectedModel = node.params["model"] ?: "gemini_image3_pro"
                        Spacer(modifier = Modifier.height(14.dp))
                        Label("Model")
                        ConfigChips(
                            options = listOf("gemini_image3_pro" to "Gemini 3 Pro", "gpt_image_2" to "GPT Image 2"),
                            selected = selectedModel,
                            onSelect = { controller.updateNodeParams(node.id, "model", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Label("Aspect Ratio")
                        ConfigChips(
                            options = listOf("16:9" to "16:9", "9:16" to "9:16", "1:1" to "1:1"),
                            selected = node.params["aspectRatio"] ?: "16:9",
                            onSelect = { controller.updateNodeParams(node.id, "aspectRatio", it) },
                            themeColor = nodeColor
                        )
                        if (selectedModel == "gemini_image3_pro") {
                            Spacer(modifier = Modifier.height(14.dp))
                            Label("Resolution")
                            ConfigChips(
                                options = listOf("1K" to "1K", "2K" to "2K", "4K" to "4K"),
                                selected = node.params["resolution"] ?: "1K",
                                onSelect = { controller.updateNodeParams(node.id, "resolution", it) },
                                themeColor = nodeColor
                            )
                        } else if (selectedModel == "gpt_image_2") {
                            Spacer(modifier = Modifier.height(14.dp))
                            Label("Quality")
                            ConfigChips(
                                options = listOf("low" to "Fast", "medium" to "Balanced", "high" to "Best"),
                                selected = node.params["quality"] ?: "medium",
                                onSelect = { controller.updateNodeParams(node.id, "quality", it) },
                                themeColor = nodeColor
                            )
                        }
                    }
                    NodeType.TEXT_PROMPT -> {
                        Label("Prompt")
                        ConfigTextField(
                            value = node.params["text"] ?: "",
                            onValueChange = {
                                controller.updateNodeParams(node.id, "text", it)
                                controller.notifyMentionedMembers(nodeId = node.id, text = it, contextSource = "Prompt")
                            },
                            hint = "Enter prompt text... (use @name to mention)"
                        )
                    }
                    NodeType.IMAGE_TO_VIDEO -> {
                        val imgEdges = connectedEdges.filter { it.targetPortId == "image" }
                        imgEdges.forEachIndexed { idx, edge ->
                            val src = workflow?.nodes?.find { it.id == edge.sourceNodeId }
                            val url = src?.outputUrl ?: src?.outputLocalPath ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label(if (idx == 0) "First Frame" else "Last Frame")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(edge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        
                        Label("Prompt")
                        val promptEdge = connectedEdges.find { it.targetPortId == "prompt" }
                        val promptSrc = promptEdge?.let { e -> workflow?.nodes?.find { it.id == e.sourceNodeId } }
                        if (promptSrc != null && promptSrc.type == app.ak25.pocketflow.models.NodeType.TEXT_PROMPT) {
                            LinkedTextPrompt(promptSrc.type.nodeName, promptSrc.params["text"] ?: "") {
                                controller.deleteEdge(promptEdge.id)
                            }
                        } else {
                            ConfigTextField(
                                value = node.params["prompt"] ?: "",
                                onValueChange = { controller.updateNodeParams(node.id, "prompt", it) },
                                hint = "Describe the motion..."
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Label("Model")
                        val videoModels = app.ak25.pocketflow.services.RunwayModels.videoModels
                        ConfigChips(
                            options = videoModels.map { it.key to it.label },
                            selected = node.params["model"] ?: "veo3.1_fast",
                            onSelect = { controller.updateNodeParams(node.id, "model", it) },
                            themeColor = nodeColor
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        val currentModelKey = node.params["model"] ?: "veo3.1_fast"
                        val currentModel = app.ak25.pocketflow.services.RunwayModels.getVideoModel(currentModelKey)
                        
                        Label("Duration")
                        val durations = app.ak25.pocketflow.services.RunwayModels.getModelDurations(currentModel)
                        val currentDurationStr = node.params["duration"] ?: durations.first().toString()
                        val clampedDuration = app.ak25.pocketflow.services.RunwayModels.clampDuration(currentModel, currentDurationStr.toIntOrNull() ?: durations.first())
                        ConfigChips(
                            options = durations.map { it.toString() to "${it}s" },
                            selected = clampedDuration.toString(),
                            onSelect = { controller.updateNodeParams(node.id, "duration", it) },
                            themeColor = nodeColor
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        Label("Aspect Ratio")
                        ConfigChips(
                            options = currentModel.ratios.keys.map { it to it },
                            selected = node.params["aspectRatio"] ?: currentModel.ratios.keys.first(),
                            onSelect = { controller.updateNodeParams(node.id, "aspectRatio", it) },
                            themeColor = nodeColor
                        )
                        
                        if (currentModel.supportsAudio) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Generate Audio", fontSize = 13.sp, color = Color(0xFF333333), fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = (node.params["audio"] ?: "false").toBoolean(),
                                    onCheckedChange = { controller.updateNodeParams(node.id, "audio", it.toString()) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = nodeColor)
                                )
                            }
                        }
                    }
                    NodeType.TEXT_TO_SPEECH -> {
                        Label("Text")
                        val textEdge = connectedEdges.find { it.targetPortId == "prompt" }
                        val textSrc = textEdge?.let { e -> workflow?.nodes?.find { it.id == e.sourceNodeId } }
                        if (textSrc != null && textSrc.type == app.ak25.pocketflow.models.NodeType.TEXT_PROMPT) {
                            LinkedTextPrompt(textSrc.type.nodeName, textSrc.params["text"] ?: "") {
                                controller.deleteEdge(textEdge.id)
                            }
                        } else {
                            ConfigTextField(
                                value = node.params["text"] ?: "",
                                onValueChange = { controller.updateNodeParams(node.id, "text", it) },
                                hint = "Enter text to speak..."
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Label("Voice")
                        ConfigChips(
                            options = app.ak25.pocketflow.services.RunwayModels.voicePresets.map { it to it },
                            selected = node.params["voicePreset"] ?: "Maya",
                            onSelect = { controller.updateNodeParams(node.id, "voicePreset", it) },
                            themeColor = nodeColor
                        )
                    }
                    NodeType.UPLOADED_IMAGE -> {
                        Label("Image")
                        val uri = node.params["imageUri"] ?: ""
                        val displayUrl = node.outputUrl?.takeIf { it.startsWith("http") }
                            ?: uri.takeIf { it.isNotEmpty() && !it.startsWith("cache://") }
                        val cacheBytes = ImageCache.get(node.id) ?: ImageCache.get(uri)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF5F5F7), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(12.dp))
                                .clickable { singleImagePicker.launch() },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                cacheBytes != null -> Image(
                                    bitmap = cacheBytes.toImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                displayUrl != null -> AsyncMediaPreview(
                                    uri = displayUrl,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color(0xFFAAAAAA))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Tap to select", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                                }
                            }
                            if (isUploadingImage) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                    NodeType.MODEL3D_GENERATION -> {
                        val imgEdge = connectedEdges.find { it.targetPortId == "image" }
                        if (imgEdge != null) {
                            val src = workflow?.nodes?.find { it.id == imgEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Input Image")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(imgEdge.id)
                            }
                        } else {
                            Label("Info")
                            Text("Configure this node by connecting it to other nodes.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    NodeType.AD_LOCALIZATION -> {
                        val imgEdge = connectedEdges.find { it.targetPortId == "image" }
                        if (imgEdge != null) {
                            val src = workflow?.nodes?.find { it.id == imgEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Input Image")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(imgEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("Input Image")
                            Text("Connect an image node to localize", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        
                        Label("Target Language")
                        ConfigChips(
                            options = listOf(
                                "es" to "Spanish",
                                "fr" to "French",
                                "de" to "German",
                                "ja" to "Japanese",
                                "zh" to "Chinese"
                            ),
                            selected = node.params["targetLanguage"] ?: "es",
                            onSelect = { controller.updateNodeParams(node.id, "targetLanguage", it) },
                            themeColor = nodeColor
                        )
                    }
                    NodeType.MARKETING_STOCK_IMAGE -> {
                        Label("Prompt")
                        ConfigTextField(
                            value = node.params["prompt"] ?: "",
                            onValueChange = { controller.updateNodeParams(node.id, "prompt", it) },
                            hint = "Marketing image brief..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        val imgEdge = connectedEdges.find { it.targetPortId == "image" }
                        if (imgEdge != null) {
                            val src = workflow?.nodes?.find { it.id == imgEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Brand Logo (Optional)")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(imgEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("Brand Logo (Optional)")
                            Text("Connect an image node to guide with a brand logo", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        
                        Label("Output Count")
                        ConfigChips(
                            options = listOf("1" to "1", "2" to "2", "3" to "3", "4" to "4"),
                            selected = node.params["outputCount"] ?: "4",
                            onSelect = { controller.updateNodeParams(node.id, "outputCount", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Label("Quality")
                        ConfigChips(
                            options = listOf("low" to "Low", "medium" to "Medium", "high" to "High"),
                            selected = node.params["quality"] ?: "high",
                            onSelect = { controller.updateNodeParams(node.id, "quality", it) },
                            themeColor = nodeColor
                        )
                    }
                    NodeType.PRODUCT_AD -> {
                        val productEdges = connectedEdges.filter { it.targetPortId == "productImage" }
                        val styleEdges = connectedEdges.filter { it.targetPortId == "styleImage" }
                        
                        Label("Product Images (${productEdges.size}/10)")
                        if (productEdges.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(productEdges.size) { i ->
                                    val src = workflow?.nodes?.find { it.id == productEdges[i].sourceNodeId }
                                    val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                                    LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                        controller.deleteEdge(productEdges[i].id)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Text("Connect up to 10 image nodes as product images", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Label("Style References (${styleEdges.size}/4)")
                        if (styleEdges.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(styleEdges.size) { i ->
                                    val src = workflow?.nodes?.find { it.id == styleEdges[i].sourceNodeId }
                                    val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                                    LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                        controller.deleteEdge(styleEdges[i].id)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Text("Connect up to 4 image nodes as style references", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        
                        Label("Product Info (Optional)")
                        ConfigTextField(
                            value = node.params["productInfo"] ?: "",
                            onValueChange = { controller.updateNodeParams(node.id, "productInfo", it) },
                            hint = "Product description and specifications..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Creative Direction (Optional)")
                        ConfigTextField(
                            value = node.params["userConcept"] ?: "",
                            onValueChange = { controller.updateNodeParams(node.id, "userConcept", it) },
                            hint = "Brand voice, framing, narrative..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Label("Aspect Ratio")
                        ConfigChips(
                            options = listOf("1280:720" to "16:9", "720:1280" to "9:16", "960:960" to "1:1"),
                            selected = node.params["ratio"] ?: "1280:720",
                            onSelect = { controller.updateNodeParams(node.id, "ratio", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Resolution")
                        ConfigChips(
                            options = listOf("720p" to "720p", "1080p" to "1080p"),
                            selected = node.params["resolution"] ?: "720p",
                            onSelect = { controller.updateNodeParams(node.id, "resolution", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Duration")
                        ConfigChips(
                            options = listOf("4" to "4s", "5" to "5s", "10" to "10s", "15" to "15s"),
                            selected = node.params["duration"] ?: "10",
                            onSelect = { controller.updateNodeParams(node.id, "duration", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Audio")
                        ConfigChips(
                            options = listOf("false" to "No", "true" to "Yes"),
                            selected = node.params["audio"] ?: "false",
                            onSelect = { controller.updateNodeParams(node.id, "audio", it) },
                            themeColor = nodeColor
                        )
                    }
                    NodeType.PRODUCT_CAMPAIGN -> {
                        Label("Prompt")
                        ConfigTextField(
                            value = node.params["prompt"] ?: "",
                            onValueChange = { controller.updateNodeParams(node.id, "prompt", it) },
                            hint = "Style / creative brief for the campaign..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        val imgEdge = connectedEdges.find { it.targetPortId == "image" }
                        if (imgEdge != null) {
                            val src = workflow?.nodes?.find { it.id == imgEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Product Image")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(imgEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("Product Image")
                            Text("Connect an image node to provide the product image", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                    NodeType.PRODUCT_SWAP -> {
                        val refVideoEdge = connectedEdges.find { it.targetPortId == "referenceVideo" }
                        if (refVideoEdge != null) {
                            val src = workflow?.nodes?.find { it.id == refVideoEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Reference Video")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(refVideoEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("Reference Video")
                            Text("Connect a video node", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        val origProdEdge = connectedEdges.find { it.targetPortId == "originalProduct" }
                        if (origProdEdge != null) {
                            val src = workflow?.nodes?.find { it.id == origProdEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Original Product")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(origProdEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("Original Product")
                            Text("Connect an image node of original product", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        val newProdEdges = connectedEdges.filter { it.targetPortId == "newProduct" }
                        Label("New Products (${newProdEdges.size}/10)")
                        if (newProdEdges.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(newProdEdges.size) { i ->
                                    val src = workflow?.nodes?.find { it.id == newProdEdges[i].sourceNodeId }
                                    val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                                    LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                        controller.deleteEdge(newProdEdges[i].id)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Text("Connect up to 10 image nodes as new products", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Label("Duration")
                        ConfigChips(
                            options = listOf("4" to "4s", "5" to "5s", "10" to "10s", "15" to "15s"),
                            selected = node.params["duration"] ?: "10",
                            onSelect = { controller.updateNodeParams(node.id, "duration", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Resolution")
                        ConfigChips(
                            options = listOf("720p" to "720p", "1080p" to "1080p"),
                            selected = node.params["resolution"] ?: "720p",
                            onSelect = { controller.updateNodeParams(node.id, "resolution", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Audio")
                        ConfigChips(
                            options = listOf("false" to "No", "true" to "Yes"),
                            selected = node.params["audio"] ?: "true",
                            onSelect = { controller.updateNodeParams(node.id, "audio", it) },
                            themeColor = nodeColor
                        )
                    }
                    NodeType.MULTI_SHOT_VIDEO -> {
                        Label("Story Prompt")
                        ConfigTextField(
                            value = node.params["prompt"] ?: "",
                            onValueChange = { controller.updateNodeParams(node.id, "prompt", it) },
                            hint = "Story prompt for auto mode..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        val firstFrameEdge = connectedEdges.find { it.targetPortId == "firstFrame" }
                        if (firstFrameEdge != null) {
                            val src = workflow?.nodes?.find { it.id == firstFrameEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("First Frame")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(firstFrameEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("First Frame (Optional)")
                            Text("Connect an image node as the first frame", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Label("Aspect Ratio")
                        ConfigChips(
                            options = listOf("1280:720" to "16:9", "720:1280" to "9:16", "960:960" to "1:1"),
                            selected = node.params["ratio"] ?: "1280:720",
                            onSelect = { controller.updateNodeParams(node.id, "ratio", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Resolution")
                        ConfigChips(
                            options = listOf("720p" to "720p", "1080p" to "1080p"),
                            selected = node.params["resolution"] ?: "720p",
                            onSelect = { controller.updateNodeParams(node.id, "resolution", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Duration")
                        ConfigChips(
                            options = listOf("5" to "5s", "10" to "10s", "15" to "15s"),
                            selected = node.params["duration"] ?: "10",
                            onSelect = { controller.updateNodeParams(node.id, "duration", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Audio")
                        ConfigChips(
                            options = listOf("false" to "No", "true" to "Yes"),
                            selected = node.params["audio"] ?: "true",
                            onSelect = { controller.updateNodeParams(node.id, "audio", it) },
                            themeColor = nodeColor
                        )
                    }
                    NodeType.PRODUCT_UGC -> {
                        val charEdge = connectedEdges.find { it.targetPortId == "characterImage" }
                        if (charEdge != null) {
                            val src = workflow?.nodes?.find { it.id == charEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Character Image")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(charEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("Character Image")
                            Text("Connect an image node of the character", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        val prodEdge = connectedEdges.find { it.targetPortId == "productImage" }
                        if (prodEdge != null) {
                            val src = workflow?.nodes?.find { it.id == prodEdge.sourceNodeId }
                            val url = src?.outputUrl ?: (if (src?.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) src.params["imageUri"] else null)
                            Label("Product Image")
                            LinkedImagePreview(src?.type?.nodeName ?: "Node", url) {
                                controller.deleteEdge(prodEdge.id)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Label("Product Image")
                            Text("Connect an image node of the product", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        
                        Label("Product Info (Optional)")
                        ConfigTextField(
                            value = node.params["productInfo"] ?: "",
                            onValueChange = { controller.updateNodeParams(node.id, "productInfo", it) },
                            hint = "Product details and key benefits..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Creative Direction (Optional)")
                        ConfigTextField(
                            value = node.params["userConcept"] ?: "",
                            onValueChange = { controller.updateNodeParams(node.id, "userConcept", it) },
                            hint = "Tone, voice register, dialog script..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Label("Duration")
                        ConfigChips(
                            options = listOf("4" to "4s", "5" to "5s", "10" to "10s", "15" to "15s"),
                            selected = node.params["duration"] ?: "15",
                            onSelect = { controller.updateNodeParams(node.id, "duration", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Aspect Ratio")
                        ConfigChips(
                            options = listOf("1280:720" to "16:9", "720:1280" to "9:16", "960:960" to "1:1"),
                            selected = node.params["ratio"] ?: "720:1280",
                            onSelect = { controller.updateNodeParams(node.id, "ratio", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Resolution")
                        ConfigChips(
                            options = listOf("720p" to "720p", "1080p" to "1080p"),
                            selected = node.params["resolution"] ?: "720p",
                            onSelect = { controller.updateNodeParams(node.id, "resolution", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Label("Audio")
                        ConfigChips(
                            options = listOf("false" to "No", "true" to "Yes"),
                            selected = node.params["audio"] ?: "true",
                            onSelect = { controller.updateNodeParams(node.id, "audio", it) },
                            themeColor = nodeColor
                        )
                    }
                    NodeType.NOTE -> {
                        val noteMode = node.params["mode"] ?: "note"
                        Label("Mode")
                        ConfigChips(
                            options = listOf("note" to "Note", "todo" to "Checklist"),
                            selected = noteMode,
                            onSelect = { controller.updateNodeParams(node.id, "mode", it) },
                            themeColor = nodeColor
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        if (noteMode == "note") {
                            Label("Note")
                            ConfigTextField(
                                value = node.params["content"] ?: "",
                                onValueChange = {
                                    controller.updateNodeParams(node.id, "content", it)
                                    controller.notifyMentionedMembers(nodeId = node.id, text = it, contextSource = "Note")
                                },
                                hint = "Write your note... (use @name to mention)"
                            )
                        } else {
                            val items = node.params["items"].orEmpty().split("\n").filter { it.isNotBlank() }
                            val checked = node.params["checked"].orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableSet()

                            fun saveChecked(newChecked: Set<Int>) {
                                controller.updateNodeParams(node.id, "checked", newChecked.sorted().joinToString(","))
                            }
                            fun deleteItem(index: Int) {
                                val newItems = items.toMutableList().apply { removeAt(index) }
                                controller.updateNodeParams(node.id, "items", newItems.joinToString("\n"))
                                saveChecked(checked.filter { it != index }.map { if (it > index) it - 1 else it }.toSet())
                            }

                            Label("Tasks")
                            if (items.isEmpty()) {
                                Text("No tasks yet — add one below.", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                items.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (index in checked) nodeColor else Color(0xFFE5E5EA))
                                                .clickable {
                                                    val isNowChecked = index !in checked
                                                    val updated = if (index in checked) checked - index else checked + index
                                                    saveChecked(updated)
                                                    controller.notifyNoteChecklistToggled(node.id, item, isNowChecked)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (index in checked) {
                                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
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
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            item,
                                            fontSize = 14.sp,
                                            color = if (index in checked) Color(0xFFAAAAAA) else Color(0xFF1A1A1A),
                                            textDecoration = if (index in checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "✕",
                                            fontSize = 15.sp,
                                            color = Color(0xFFBDBDBD),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { deleteItem(index) }
                                                .padding(6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            var newTask by remember(node.id, items.size) { mutableStateOf("") }
                            Label("Add Task")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newTask,
                                    onValueChange = { newTask = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("New task... (@name to assign)", fontSize = 13.sp, color = Color(0xFFAAAAAA)) },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF1A1A1A)),
                                    maxLines = 1,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color(0xFFF5F5F7),
                                        focusedContainerColor = Color(0xFFF5F5F7),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    )
                                )
                                Button(
                                    onClick = {
                                        val text = newTask.trim()
                                        if (text.isNotEmpty()) {
                                            controller.updateNodeParams(node.id, "items", (items + text).joinToString("\n"))
                                            controller.notifyMentionedMembers(nodeId = node.id, text = text, contextSource = "Task")
                                            newTask = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = nodeColor)
                                ) {
                                    Text("Add", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    else -> {
                        Label("Info")
                        Text("Configure this node by connecting it to other nodes.", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                val workflowMembers = remember(workflow?.membersJson) {
                    controller.getWorkflowMembers(workflow?.id)
                }
                if (workflowMembers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Label("Assignee")
                    val currentAssigneeId = node.params["assignee_id"].orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = currentAssigneeId.isEmpty(),
                            onClick = {
                                controller.updateNodeParams(node.id, "assignee_id", "")
                                controller.updateNodeParams(node.id, "assignee_name", "")
                            },
                            label = { Text("Unassigned", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        workflowMembers.forEach { member ->
                            val isSelected = currentAssigneeId == member.userId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) {
                                        controller.updateNodeParams(node.id, "assignee_id", member.userId)
                                        controller.updateNodeParams(node.id, "assignee_name", member.userName)
                                        controller.notifyTaskAssigned(
                                            nodeId = node.id,
                                            taskTitle = if (node.type == NodeType.NOTE) (node.params["content"]?.takeIf { it.isNotBlank() } ?: "Note / Checklist") else node.type.nodeName,
                                            assigneeUserId = member.userId,
                                            assigneeName = member.userName
                                        )
                                    }
                                },
                                label = { Text(member.userName.ifEmpty { "Member" }, fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Error Message
            var errorMessage by remember { mutableStateOf<String?>(null) }
            
            if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp))
                LaunchedEffect(errorMessage) {
                    kotlinx.coroutines.delay(2500)
                    errorMessage = null
                }
            }

            // Run Button Section
            if (node.type != NodeType.UPLOADED_IMAGE && node.type != NodeType.NOTE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    val isRunning = node.status == NodeStatus.RUNNING
                    val btnColor = if (isRunning) Color(0xFFE53935) else nodeColor
                    Button(
                        onClick = {
                            if (!isRunning) {
                                if (node.type == NodeType.IMAGE_TO_VIDEO) {
                                    val hasImages = connectedEdges.any { it.targetPortId == "image" }
                                    val prompt = node.params["prompt"]
                                    if (!hasImages && prompt.isNullOrBlank()) {
                                        errorMessage = "Please link an image or type a prompt first."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.TEXT_TO_SPEECH) {
                                    val prompt = node.params["text"]
                                    val hasLinkedPrompt = connectedEdges.any { it.targetPortId == "prompt" }
                                    if (!hasLinkedPrompt && prompt.isNullOrBlank()) {
                                        errorMessage = "Please enter text or link a prompt."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.IMAGE_GENERATION) {
                                    val prompt = node.params["prompt"]
                                    val hasLinkedPrompt = connectedEdges.any { it.targetPortId == "prompt" }
                                    if (!hasLinkedPrompt && prompt.isNullOrBlank()) {
                                        errorMessage = "Please enter text or link a prompt."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.AD_LOCALIZATION) {
                                    val hasLinkedImage = connectedEdges.any { it.targetPortId == "image" }
                                    if (!hasLinkedImage) {
                                        errorMessage = "Please link an image to localize."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.MARKETING_STOCK_IMAGE) {
                                    if (node.params["prompt"].isNullOrBlank()) {
                                        errorMessage = "Please enter a prompt brief."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.PRODUCT_AD) {
                                    val productEdges = connectedEdges.filter { it.targetPortId == "productImage" }
                                    if (productEdges.isEmpty()) {
                                        errorMessage = "Please link at least one product image."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.PRODUCT_CAMPAIGN) {
                                    val hasLinkedImage = connectedEdges.any { it.targetPortId == "image" }
                                    if (!hasLinkedImage) {
                                        errorMessage = "Please link a product image."
                                        return@Button
                                    }
                                    if (node.params["prompt"].isNullOrBlank()) {
                                        errorMessage = "Please enter a creative brief prompt."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.PRODUCT_SWAP) {
                                    if (!connectedEdges.any { it.targetPortId == "referenceVideo" }) {
                                        errorMessage = "Please link a reference video."
                                        return@Button
                                    }
                                    if (!connectedEdges.any { it.targetPortId == "originalProduct" }) {
                                        errorMessage = "Please link original product image."
                                        return@Button
                                    }
                                    if (!connectedEdges.any { it.targetPortId == "newProduct" }) {
                                        errorMessage = "Please link at least one new product image."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.MULTI_SHOT_VIDEO) {
                                    if (node.params["prompt"].isNullOrBlank()) {
                                        errorMessage = "Please enter a story prompt."
                                        return@Button
                                    }
                                } else if (node.type == NodeType.PRODUCT_UGC) {
                                    if (!connectedEdges.any { it.targetPortId == "characterImage" }) {
                                        errorMessage = "Please link a character image."
                                        return@Button
                                    }
                                    if (!connectedEdges.any { it.targetPortId == "productImage" }) {
                                        errorMessage = "Please link a product image."
                                        return@Button
                                    }
                                }
                                errorMessage = null
                                onRunNode()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Stop Generation", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        } else {
                            val creditCost = app.ak25.pocketflow.services.PocketFlowPurchases.estimateNodeCredits(node)
                            val label = if (node.status == NodeStatus.COMPLETED) "Regenerate" else "Run Generation"
                            Text("$label ($creditCost credits)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterAlignedDragHandle() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.width(36.dp).height(4.dp).background(Color(0xFFE5E5EA), RoundedCornerShape(2.dp)))
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF666666))
    Spacer(modifier = Modifier.height(6.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigTextField(value: String, onValueChange: (String) -> Unit, hint: String) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, fontSize = 14.sp, color = Color(0xFFAAAAAA)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
        minLines = 3,
        maxLines = 5,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color(0xFF1A1A1A)),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color(0xFFF5F5F7),
            focusedContainerColor = Color(0xFFF5F5F7),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigChips(options: List<Pair<String, String>>, selected: String, themeColor: Color, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .background(if (isSelected) themeColor.copy(alpha = 0.1f) else Color(0xFFF5F5F7), RoundedCornerShape(8.dp))
                    .border(1.dp, if (isSelected) themeColor else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(label, fontSize = 12.sp, color = if (isSelected) themeColor else Color(0xFF666666), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LinkedTextPrompt(sourceNodeName: String, text: String, onUnlink: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8F0FE), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFD2E3FC), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Linked from $sourceNodeName", fontSize = 10.sp, color = Color(0xFF1967D2), fontWeight = FontWeight.SemiBold)
                if (onUnlink != null) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = "Unlink",
                        modifier = Modifier.size(14.dp).clickable { onUnlink() },
                        tint = Color(0xFF1967D2)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text.takeIf { it.isNotBlank() } ?: "Empty prompt", fontSize = 13.sp, color = Color(0xFF1967D2).copy(alpha = 0.8f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LinkedImagePreview(sourceNodeName: String, imageUrl: String?, onUnlink: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3E5F5), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE1BEE7), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (imageUrl != null) {
                AsyncMediaPreview(
                    uri = imageUrl,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(modifier = Modifier.size(60.dp).background(Color(0xFFE1BEE7), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("No image", fontSize = 10.sp, color = Color(0xFF8E24AA))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Linked Image", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A148C))
                Text("from $sourceNodeName", fontSize = 11.sp, color = Color(0xFF6A1B9A))
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onUnlink) {
                Text("×", fontSize = 24.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }
}
