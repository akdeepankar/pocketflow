package app.ak25.pocketflow.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.*
import app.ak25.pocketflow.ui.utils.toImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewer(
    url: String,
    mediaType: String,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview") },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (mediaType == "image") {
                var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                LaunchedEffect(url) {
                    try {
                        val bytes = app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(url)
                        if (bytes != null) {
                            bitmap = bytes.toImageBitmap()
                        }
                    } catch (e: Exception) {
                        // ignore
                    } finally {
                        isLoading = false
                    }
                }

                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap!!,
                        contentDescription = "Image preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("Failed to load image", color = Color.White)
                }
            } else {
                Text("Media Player Placeholder\nType: $mediaType\nURL: $url", color = Color.White)
            }
        }
    }
}
