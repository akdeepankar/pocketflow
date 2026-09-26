package app.ak25.pocketflow.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import chaintech.videoplayer.ui.video.VideoPlayerView
import chaintech.videoplayer.model.PlayerConfig

@Composable
fun AsyncVideoPlayer(
    url: String, 
    modifier: Modifier = Modifier, 
    isMiniature: Boolean = true, 
    isPaused: Boolean = false,
    loop: Boolean = true,
    onEnd: (() -> Unit)? = null
) {
    val resolvedUrl = androidx.compose.runtime.remember(url) {
        app.ak25.pocketflow.storage.LocalStorage.resolveLocalPath(url)
    }
    VideoPlayerView(
        modifier = modifier,
        url = resolvedUrl,
        playerConfig = if (isMiniature) PlayerConfig(
            isPauseResumeEnabled = false,
            isSeekBarVisible = false,
            isDurationVisible = false,
            isFastForwardBackwardEnabled = false,
            isMuteControlEnabled = false,
            isSpeedControlEnabled = false,
            isFullScreenEnabled = false,
            isScreenLockEnabled = false,
            isScreenResizeEnabled = false,
            isMute = true,
            showDesktopControls = false,
            isPause = false,
            loop = loop,
            didEndVideo = onEnd
        ) else PlayerConfig(
            isMute = false,
            isPause = isPaused,
            isPauseResumeEnabled = false,
            isSeekBarVisible = false,
            isDurationVisible = false,
            isMuteControlEnabled = false,
            isFastForwardBackwardEnabled = false,
            isFullScreenEnabled = false,
            isScreenLockEnabled = false,
            isScreenResizeEnabled = false,
            showDesktopControls = false,
            loop = loop,
            didEndVideo = onEnd
        )
    )
}
