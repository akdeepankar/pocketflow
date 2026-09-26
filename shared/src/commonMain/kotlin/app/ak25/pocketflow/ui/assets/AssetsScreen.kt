package app.ak25.pocketflow.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import pocketflow.shared.generated.resources.Res
import pocketflow.shared.generated.resources.EduAUVICWANTHand
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.NodeType
import app.ak25.pocketflow.ui.editor.AppIcons
import app.ak25.pocketflow.ui.utils.toImageBitmap
import app.ak25.pocketflow.ui.viewer.MediaViewer

enum class AssetFilter {
    ALL, IMAGES, VIDEOS, AUDIOS
}

data class GeneratedAsset(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val nodeType: NodeType,
    val path: String
)

@Composable
fun AssetsScreen(
    controller: WorkflowController,
    onPreviewStateChanged: ((Boolean) -> Unit)? = null
) {
    val workflows by controller.workflows.collectAsState()
    
    val assets = remember(workflows) {
        workflows.flatMap { wf ->
            wf.nodes.filter {
                it.status == app.ak25.pocketflow.models.NodeStatus.COMPLETED &&
                it.type != app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE &&
                (!it.outputUrl.isNullOrEmpty() || !it.outputLocalPath.isNullOrEmpty())
            }.flatMap { node ->
                val primaryUrl = node.outputUrl
                val localPath = node.outputLocalPath

                // Check if local file exists on this device
                val localExists = if (!localPath.isNullOrEmpty()) {
                    try {
                        val firstPath = localPath.split(",").firstOrNull()?.trim() ?: localPath
                        app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(firstPath) != null
                    } catch (e: Exception) { false }
                } else false

                // If local file exists, we can use it; otherwise use the remote URL so all collaborators can view it
                val resolvedPath = if (localExists && !localPath.isNullOrEmpty()) {
                    localPath
                } else if (!primaryUrl.isNullOrEmpty() && (primaryUrl.startsWith("http://") || primaryUrl.startsWith("https://") || primaryUrl.startsWith("data:") || primaryUrl.startsWith("cache://"))) {
                    primaryUrl
                } else if (!localPath.isNullOrEmpty()) {
                    localPath
                } else {
                    primaryUrl ?: ""
                }

                if (resolvedPath.contains(",")) {
                    resolvedPath.split(",").map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { idx, p ->
                        GeneratedAsset(
                            id = "${node.id}_$idx",
                            workflowId = wf.id,
                            workflowName = wf.name,
                            nodeType = node.type,
                            path = p
                        )
                    }
                } else {
                    listOf(
                        GeneratedAsset(
                            id = node.id,
                            workflowId = wf.id,
                            workflowName = wf.name,
                            nodeType = node.type,
                            path = resolvedPath
                        )
                    )
                }
            }
        }
    }

    var selectedAsset by remember { mutableStateOf<GeneratedAsset?>(null) }
    var currentFilter by remember { mutableStateOf(AssetFilter.ALL) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isPortraitPreview by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedAsset) {
        if (selectedAsset == null) {
            isPortraitPreview = false
        }
        onPreviewStateChanged?.invoke(selectedAsset != null)
    }

    val filteredAssets = remember(assets, currentFilter) {
        when (currentFilter) {
            AssetFilter.ALL -> assets
            AssetFilter.IMAGES -> assets.filter { it.nodeType.outputs.firstOrNull()?.dataType == app.ak25.pocketflow.models.PortDataType.IMAGE }
            AssetFilter.VIDEOS -> assets.filter { it.nodeType.outputs.firstOrNull()?.dataType == app.ak25.pocketflow.models.PortDataType.VIDEO }
            AssetFilter.AUDIOS -> assets.filter { it.nodeType.outputs.firstOrNull()?.dataType == app.ak25.pocketflow.models.PortDataType.AUDIO }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)
            ) {
                Text(
                    text = "assets",
                    fontFamily = FontFamily(org.jetbrains.compose.resources.Font(Res.font.EduAUVICWANTHand)),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 24.sp
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Filter Chip Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssetFilter.values().forEach { filter ->
                        val isSelected = currentFilter == filter
                        Surface(
                            onClick = { currentFilter = filter },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color(0xFF0EA5E9).copy(alpha = 0.1f) else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF0EA5E9) else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = filter.name.lowercase(),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0EA5E9) else Color.Gray,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (filteredAssets.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (assets.isEmpty()) {
                                "No generated assets yet.\nRun nodes to see them here."
                            } else {
                                "No assets found matching the '${currentFilter.name.lowercase()}' filter."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 64.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredAssets) { asset ->
                            AssetCard(
                                asset = asset,
                                onClick = { selectedAsset = asset }
                            )
                        }
                    }
                }
            }
        }

        if (selectedAsset != null) {
            val path = selectedAsset!!.path
            val isVideo = path.contains(".mp4", ignoreCase = true) || path.contains(".webm", ignoreCase = true)
            val isAudio = path.contains(".mp3", ignoreCase = true) || path.contains(".wav", ignoreCase = true)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { selectedAsset = null },
                contentAlignment = Alignment.Center
            ) {
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
                            url = path,
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
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
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
                        .shadow(12.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                        .padding(32.dp)
                    ) {
                        app.ak25.pocketflow.ui.editor.AsyncVideoPlayer(
                            url = path,
                            modifier = Modifier.size(1.dp).alpha(0.001f),
                            isMiniature = false,
                            isPaused = isPaused,
                            loop = false,
                            onEnd = { isPaused = true }
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0xFFE8F5E9), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
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
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { isPaused = !isPaused },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPaused) AppIcons.Play else AppIcons.Pause,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp).offset(x = if (isPaused) 2.dp else 0.dp)
                                )
                            }
                        }
                    }
                } else {
                    app.ak25.pocketflow.ui.editor.AsyncMediaPreview(
                        uri = path,
                        modifier = Modifier.fillMaxWidth().padding(32.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isVideo) {
                        IconButton(
                            onClick = { isPortraitPreview = !isPortraitPreview },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Text(
                                text = if (isPortraitPreview) "9:16" else "16:9",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = { 
                            coroutineScope.launch {
                                val isRemote = path.startsWith("http://") || path.startsWith("https://") || path.startsWith("data:")
                                val localPath = if (isRemote) {
                                    toastMessage = "Downloading..."
                                    try {
                                        val bytes = app.ak25.pocketflow.ui.utils.MediaLoader.loadBytes(path)
                                        if (bytes != null) {
                                            val ext = if (path.contains(".mp4", ignoreCase = true) || path.contains(".webm", ignoreCase = true)) "mp4" else if (path.contains(".mp3", ignoreCase = true) || path.contains(".wav", ignoreCase = true)) "mp3" else "png"
                                            app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, ext)
                                        } else null
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    path
                                }

                                if (localPath != null) {
                                    val saved = app.ak25.pocketflow.storage.LocalStorage.exportMediaToGallery(localPath)
                                    if (saved) {
                                        toastMessage = "Saved successfully!"
                                    } else {
                                        toastMessage = "Failed to export media."
                                    }
                                } else {
                                    toastMessage = "Failed to download media."
                                }
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = AppIcons.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { 
                            coroutineScope.launch {
                                val isRemote = path.startsWith("http://") || path.startsWith("https://") || path.startsWith("data:")
                                val localPath = if (isRemote) {
                                    toastMessage = "Downloading for share..."
                                    try {
                                        val bytes = app.ak25.pocketflow.ui.utils.MediaLoader.loadBytes(path)
                                        if (bytes != null) {
                                            val ext = if (path.contains(".mp4", ignoreCase = true) || path.contains(".webm", ignoreCase = true)) "mp4" else if (path.contains(".mp3", ignoreCase = true) || path.contains(".wav", ignoreCase = true)) "mp3" else "png"
                                            app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, ext)
                                        } else null
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    path
                                }

                                if (localPath != null) {
                                    val shared = app.ak25.pocketflow.storage.LocalStorage.shareMedia(localPath)
                                    if (!shared) {
                                        toastMessage = "Unable to share media."
                                    }
                                } else {
                                    toastMessage = "Failed to download media for share."
                                }
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = AppIcons.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { selectedAsset = null },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        if (toastMessage != null) {
            LaunchedEffect(toastMessage) {
                kotlinx.coroutines.delay(2500)
                toastMessage = null
            }
            val isSuccess = toastMessage?.contains("successfully", ignoreCase = true) == true
            val alertBg = if (isSuccess) Color(0xFF34C759) else Color(0xFFE53935)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 80.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .background(alertBg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(toastMessage!!, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AssetCard(
    asset: GeneratedAsset,
    onClick: () -> Unit
) {
    val isImage = when (asset.nodeType) {
        NodeType.IMAGE_GENERATION, NodeType.AD_LOCALIZATION, NodeType.MARKETING_STOCK_IMAGE, NodeType.PRODUCT_CAMPAIGN -> true
        else -> false
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isImage) {
                AssetThumbnail(path = asset.path, modifier = Modifier.fillMaxSize())
            } else {
                val isVideoNode = when (asset.nodeType) {
                    NodeType.IMAGE_TO_VIDEO, NodeType.PRODUCT_AD, NodeType.PRODUCT_SWAP, NodeType.MULTI_SHOT_VIDEO, NodeType.PRODUCT_UGC -> true
                    else -> false
                }
                if (isVideoNode) {
                    app.ak25.pocketflow.ui.editor.AsyncVideoPlayer(
                        url = asset.path,
                        modifier = Modifier.fillMaxSize(),
                        isMiniature = true
                    )
                } else {
                    val icon = when (asset.nodeType) {
                        NodeType.TEXT_TO_SPEECH -> AppIcons.Audio
                        NodeType.MODEL3D_GENERATION -> AppIcons.Cube3D
                        else -> AppIcons.Info
                    }
                    val iconColor = when (asset.nodeType) {
                        NodeType.TEXT_TO_SPEECH -> Color(0xFFFF2D55)
                        NodeType.MODEL3D_GENERATION -> Color(0xFF5856D6)
                        else -> Color.Gray
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Top-right corner overlay view button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Eye,
                    contentDescription = "View",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun AssetThumbnail(path: String, modifier: Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var hasError by remember(path) { mutableStateOf(false) }

    val singlePath = remember(path) {
        path.split(",").firstOrNull()?.trim() ?: path
    }

    LaunchedEffect(singlePath) {
        if (singlePath.isEmpty()) {
            hasError = true
            return@LaunchedEffect
        }
        val bytes = app.ak25.pocketflow.ui.utils.MediaLoader.loadBytes(singlePath)
        if (bytes != null) {
            bitmap = bytes.toImageBitmap()
            hasError = false
        } else {
            hasError = true
        }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else if (hasError) {
        Box(
            modifier = modifier.background(Color(0xFFF5F5F7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Image,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        Box(
            modifier = modifier.background(Color(0xFFF5F5F7)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
