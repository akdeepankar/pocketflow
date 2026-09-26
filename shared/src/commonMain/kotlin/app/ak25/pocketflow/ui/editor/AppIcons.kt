package app.ak25.pocketflow.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Image: ImageVector
        get() = ImageVector.Builder(
            name = "Image", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(21f, 19f)
            lineTo(21f, 5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            lineTo(5f, 3f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            close()
            moveTo(8.5f, 13.5f)
            lineTo(11f, 16.51f)
            lineTo(14.5f, 12f)
            lineTo(19f, 18f)
            lineTo(5f, 18f)
            lineTo(8.5f, 13.5f)
            close()
        }.build()

    val Video: ImageVector
        get() = ImageVector.Builder(
            name = "Video", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 4f)
            lineTo(20f, 8f)
            lineTo(17f, 8f)
            lineTo(15f, 4f)
            lineTo(13f, 4f)
            lineTo(15f, 8f)
            lineTo(12f, 8f)
            lineTo(10f, 4f)
            lineTo(8f, 4f)
            lineTo(10f, 8f)
            lineTo(7f, 8f)
            lineTo(5f, 4f)
            lineTo(4f, 4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            lineTo(22f, 4f)
            lineTo(18f, 4f)
            close()
        }.build()

    val Audio: ImageVector
        get() = ImageVector.Builder(
            name = "Audio", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 14.99f, 12.66f, 14.99f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            moveTo(17.3f, 11f)
            curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
            curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.72f)
            curveTo(16.28f, 17.24f, 19f, 14.42f, 19f, 11f)
            lineTo(17.3f, 11f)
            close()
        }.build()

    val Text: ImageVector
        get() = ImageVector.Builder(
            name = "Text", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(14f, 2f)
            lineTo(6f, 2f)
            curveTo(4.9f, 2f, 4.01f, 2.9f, 4.01f, 4f)
            lineTo(4f, 20f)
            curveTo(4f, 21.1f, 4.89f, 22f, 5.99f, 22f)
            lineTo(18f, 22f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            lineTo(20f, 8f)
            lineTo(14f, 2f)
            close()
            moveTo(16f, 18f)
            lineTo(8f, 18f)
            lineTo(8f, 16f)
            lineTo(16f, 16f)
            lineTo(16f, 18f)
            close()
            moveTo(16f, 14f)
            lineTo(8f, 14f)
            lineTo(8f, 12f)
            lineTo(16f, 12f)
            lineTo(16f, 14f)
            close()
            moveTo(13f, 9f)
            lineTo(13f, 3.5f)
            lineTo(18.5f, 9f)
            lineTo(13f, 9f)
            close()
        }.build()

    val Cube3D: ImageVector
        get() = ImageVector.Builder(
            name = "Cube3D", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 16.5f)
            lineTo(3f, 7.5f)
            curveTo(3f, 7.12f, 3.21f, 6.79f, 3.53f, 6.62f)
            lineTo(11.43f, 2.18f)
            curveTo(11.59f, 2.06f, 11.79f, 2f, 12f, 2f)
            curveTo(12.21f, 2f, 12.41f, 2.06f, 12.57f, 2.18f)
            lineTo(20.47f, 6.62f)
            curveTo(20.79f, 6.79f, 21f, 7.12f, 21f, 7.5f)
            lineTo(21f, 16.5f)
            curveTo(21f, 16.88f, 20.79f, 17.21f, 20.47f, 17.38f)
            lineTo(12.57f, 21.82f)
            curveTo(12.41f, 21.94f, 12.21f, 22f, 12f, 22f)
            curveTo(11.79f, 22f, 11.59f, 21.94f, 11.43f, 21.82f)
            lineTo(3.53f, 17.38f)
            curveTo(3.21f, 17.21f, 3f, 16.88f, 3f, 16.5f)
            close()
            moveTo(12f, 4.15f)
            lineTo(6.04f, 7.5f)
            lineTo(12f, 10.85f)
            lineTo(17.96f, 7.5f)
            lineTo(12f, 4.15f)
            close()
            moveTo(5f, 15.91f)
            lineTo(11f, 19.29f)
            lineTo(11f, 12.58f)
            lineTo(5f, 9.19f)
            lineTo(5f, 15.91f)
            close()
            moveTo(19f, 15.91f)
            lineTo(19f, 9.19f)
            lineTo(13f, 12.58f)
            lineTo(13f, 19.29f)
            lineTo(5f, 15.91f)
            close()
        }.build()

    val Eye: ImageVector
        get() = ImageVector.Builder(
            name = "Eye", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4.5f)
            curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
            close()
            moveTo(12f, 17f)
            curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
            close()
            moveTo(12f, 9f)
            curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
            close()
        }.build()

    val Add: ImageVector
        get() = ImageVector.Builder(
            name = "Add", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 13f)
            lineTo(13f, 13f)
            lineTo(13f, 19f)
            lineTo(11f, 19f)
            lineTo(11f, 13f)
            lineTo(5f, 13f)
            lineTo(5f, 11f)
            lineTo(11f, 11f)
            lineTo(11f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 11f)
            lineTo(19f, 11f)
            close()
        }.build()

    val Play: ImageVector
        get() = ImageVector.Builder(
            name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5f)
            lineTo(8f, 19f)
            lineTo(19f, 12f)
            close()
        }.build()

    val Pause: ImageVector
        get() = ImageVector.Builder(
            name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f)
            lineTo(10f, 19f)
            lineTo(10f, 5f)
            lineTo(6f, 5f)
            lineTo(6f, 19f)
            close()
            moveTo(14f, 5f)
            lineTo(14f, 19f)
            lineTo(18f, 19f)
            lineTo(18f, 5f)
            lineTo(14f, 5f)
            close()
        }.build()

    val Close: ImageVector
        get() = ImageVector.Builder(
            name = "Close", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }.build()

    val Download: ImageVector
        get() = ImageVector.Builder(
            name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 9f)
            lineTo(15f, 9f)
            lineTo(15f, 3f)
            lineTo(9f, 3f)
            lineTo(9f, 9f)
            lineTo(5f, 9f)
            lineTo(12f, 16f)
            lineTo(19f, 9f)
            close()
            moveTo(5f, 18f)
            lineTo(19f, 18f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            close()
        }.build()

    val Refresh: ImageVector
        get() = ImageVector.Builder(
            name = "Refresh", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            // simple refresh-like shape (reused download path as placeholder)
            moveTo(19f, 9f)
            lineTo(15f, 9f)
            lineTo(15f, 3f)
            lineTo(9f, 3f)
            lineTo(9f, 9f)
            lineTo(5f, 9f)
            lineTo(12f, 16f)
            lineTo(19f, 9f)
            close()
            moveTo(5f, 18f)
            lineTo(19f, 18f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            close()
        }.build()

    val Settings: ImageVector
        get() = ImageVector.Builder(
            name = "Settings", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.13f, 11.06f)
            lineTo(21.16f, 9.48f)
            curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.28f, 8.87f)
            lineTo(19.36f, 5.55f)
            curveTo(19.24f, 5.33f, 18.99f, 5.26f, 18.77f, 5.33f)
            lineTo(16.38f, 6.29f)
            curveTo(15.88f, 5.91f, 15.35f, 5.59f, 14.76f, 5.35f)
            lineTo(14.4f, 2.81f)
            curveTo(14.36f, 2.57f, 14.16f, 2.4f, 13.92f, 2.4f)
            lineTo(10.08f, 2.4f)
            curveTo(9.84f, 2.4f, 9.65f, 2.57f, 9.61f, 2.81f)
            lineTo(9.25f, 5.35f)
            curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
            lineTo(5.24f, 5.33f)
            curveTo(5.02f, 5.25f, 4.77f, 5.33f, 4.65f, 5.55f)
            lineTo(2.74f, 8.87f)
            curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
            lineTo(4.84f, 11.06f)
            curveTo(4.8f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
            curveTo(4.8f, 12.31f, 4.82f, 12.64f, 4.86f, 12.94f)
            lineTo(2.86f, 14.52f)
            curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.72f, 15.13f)
            lineTo(4.65f, 18.45f)
            curveTo(4.77f, 18.67f, 5.02f, 18.74f, 5.24f, 18.67f)
            lineTo(7.63f, 17.71f)
            curveTo(8.12f, 18.09f, 8.66f, 18.41f, 9.25f, 18.65f)
            lineTo(9.61f, 21.19f)
            curveTo(9.65f, 21.43f, 9.84f, 21.6f, 10.08f, 21.6f)
            lineTo(13.92f, 21.6f)
            curveTo(14.16f, 21.6f, 14.36f, 21.43f, 14.4f, 21.19f)
            lineTo(14.76f, 18.65f)
            curveTo(15.35f, 18.41f, 15.88f, 18.09f, 16.38f, 17.71f)
            lineTo(18.77f, 18.67f)
            curveTo(18.99f, 18.75f, 19.24f, 18.67f, 19.36f, 18.45f)
            lineTo(21.28f, 15.13f)
            curveTo(21.4f, 14.91f, 21.34f, 14.66f, 21.16f, 14.52f)
            lineTo(19.14f, 12.94f)
            close()
            moveTo(12f, 15.6f)
            curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
            curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
            curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
            curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
            close()
        }.build()

    val ArrowBack: ImageVector
        get() = ImageVector.Builder(
            name = "ArrowBack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.41f, 18.59f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            lineTo(20f, 11f)
            close()
        }.build()

    val ChevronRight: ImageVector
        get() = ImageVector.Builder(
            name = "ChevronRight", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 16.59f)
            lineTo(10f, 18f)
            lineTo(16f, 12f)
            close()
        }.build()

    val Info: ImageVector
        get() = ImageVector.Builder(
            name = "Info", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(13f, 17f)
            lineTo(11f, 17f)
            lineTo(11f, 11f)
            lineTo(13f, 11f)
            lineTo(13f, 17f)
            close()
            moveTo(13f, 9f)
            lineTo(11f, 9f)
            lineTo(11f, 7f)
            lineTo(13f, 7f)
            lineTo(13f, 9f)
            close()
        }.build()

    val Build: ImageVector
        get() = ImageVector.Builder(
            name = "Build", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(22.7f, 19f)
            lineTo(13.6f, 9.9f)
            curveTo(14.5f, 7.6f, 14f, 4.9f, 12.1f, 3f)
            curveTo(10f, 0.9f, 6.9f, 0.5f, 4.4f, 1.7f)
            lineTo(8.6f, 5.9f)
            lineTo(5.7f, 8.8f)
            lineTo(1.4f, 4.6f)
            curveTo(0.2f, 7.1f, 0.6f, 10.2f, 2.7f, 12.3f)
            curveTo(4.6f, 14.2f, 7.3f, 14.7f, 9.6f, 13.8f)
            lineTo(18.7f, 22.9f)
            curveTo(19.1f, 23.3f, 19.7f, 23.3f, 20.1f, 22.9f)
            lineTo(22.6f, 20.4f)
            curveTo(23.1f, 20f, 23.1f, 19.3f, 22.7f, 19f)
            close()
        }.build()

    val StorageIcon: ImageVector
        get() = ImageVector.Builder(
            name = "Storage", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(2f, 20f)
            lineTo(22f, 20f)
            lineTo(22f, 17f)
            lineTo(2f, 17f)
            lineTo(2f, 20f)
            close()
            moveTo(5f, 18.5f)
            curveTo(5f, 17.67f, 4.33f, 17f, 3.5f, 17.5f)
            curveTo(2.67f, 17f, 2f, 17.67f, 2f, 18.5f)
            curveTo(2f, 19.33f, 2.67f, 20f, 3.5f, 20f)
            curveTo(4.33f, 20f, 5f, 19.33f, 5f, 18.5f)
            close()
            moveTo(2f, 14f)
            lineTo(22f, 14f)
            lineTo(22f, 11f)
            lineTo(2f, 11f)
            lineTo(2f, 14f)
            close()
            moveTo(5f, 12.5f)
            curveTo(5f, 11.67f, 4.33f, 11f, 3.5f, 11f)
            curveTo(2.67f, 11f, 2f, 11.67f, 2f, 12.5f)
            curveTo(2f, 13.33f, 2.67f, 14f, 3.5f, 14f)
            curveTo(4.33f, 14f, 5f, 13.33f, 5f, 12.5f)
            close()
            moveTo(2f, 8f)
            lineTo(22f, 8f)
            lineTo(22f, 5f)
            lineTo(2f, 5f)
            lineTo(2f, 8f)
            close()
            moveTo(5f, 6.5f)
            curveTo(5f, 5.67f, 4.33f, 5f, 3.5f, 5f)
            curveTo(2.67f, 5f, 2f, 5.67f, 2f, 6.5f)
            curveTo(2f, 7.33f, 2.67f, 8f, 3.5f, 8f)
            curveTo(4.33f, 8f, 5f, 7.33f, 5f, 6.5f)
            close()
        }.build()

    val LockIcon: ImageVector
        get() = ImageVector.Builder(
            name = "Lock", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 8f)
            lineTo(17f, 8f)
            lineTo(17f, 6f)
            curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
            curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
            lineTo(7f, 8f)
            lineTo(6f, 8f)
            curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
            lineTo(4f, 20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            lineTo(18f, 22f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            lineTo(20f, 10f)
            curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
            close()
            moveTo(12f, 17f)
            curveTo(10.9f, 17f, 10f, 16.1f, 10f, 15f)
            curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
            curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f)
            curveTo(14f, 16.1f, 13.1f, 17f, 12f, 17f)
            close()
            moveTo(15.1f, 8f)
            lineTo(8.9f, 8f)
            lineTo(8.9f, 6f)
            curveTo(8.9f, 4.29f, 10.29f, 2.9f, 12f, 2.9f)
            curveTo(13.71f, 2.9f, 15.1f, 4.29f, 15.1f, 6f)
            lineTo(15.1f, 8f)
            close()
        }.build()

    val CloudIcon: ImageVector
        get() = ImageVector.Builder(
            name = "Cloud", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            lineTo(19f, 20f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
        }.build()

    val StarIcon: ImageVector
        get() = ImageVector.Builder(
            name = "Star", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineTo(16.54f, 13.97f)
            lineTo(22f, 9.24f)
            lineTo(14.81f, 8.63f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineTo(7.46f, 13.97f)
            lineTo(5.82f, 21f)
            close()
        }.build()

    val GridIcon: ImageVector
        get() = ImageVector.Builder(
            name = "Grid", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 3f)
            lineTo(3f, 11f)
            lineTo(11f, 11f)
            lineTo(11f, 3f)
            close()
            moveTo(13f, 3f)
            lineTo(13f, 11f)
            lineTo(21f, 11f)
            lineTo(21f, 3f)
            close()
            moveTo(3f, 13f)
            lineTo(3f, 21f)
            lineTo(11f, 21f)
            lineTo(11f, 13f)
            close()
            moveTo(13f, 13f)
            lineTo(13f, 21f)
            lineTo(21f, 21f)
            lineTo(21f, 13f)
            close()
        }.build()

    val TouchIcon: ImageVector
        get() = ImageVector.Builder(
            name = "Touch", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 11.24f)
            lineTo(9f, 2f)
            curveTo(9f, 1.45f, 9.45f, 1f, 10f, 1f)
            curveTo(10.55f, 1f, 11f, 1.45f, 11f, 2f)
            lineTo(11f, 11.24f)
            curveTo(10.68f, 11.09f, 10.35f, 11f, 10f, 11f)
            curveTo(9.65f, 11f, 9.32f, 11.09f, 9f, 11.24f)
            close()
            moveTo(13f, 11.24f)
            lineTo(13f, 4f)
            curveTo(13f, 3.45f, 13.45f, 3f, 14f, 3f)
            curveTo(14.55f, 3f, 15f, 3.45f, 15f, 4f)
            lineTo(15f, 12f)
            curveTo(14.35f, 11.38f, 13.72f, 11.2f, 13f, 11.24f)
            close()
            moveTo(7f, 12f)
            lineTo(7f, 5f)
            curveTo(7f, 4.45f, 6.55f, 4f, 6f, 4f)
            curveTo(5.45f, 4f, 5f, 4.45f, 5f, 5f)
            lineTo(5f, 16f)
            curveTo(5f, 19.31f, 7.69f, 22f, 11f, 22f)
            lineTo(12f, 22f)
            curveTo(15.31f, 22f, 18f, 19.31f, 18f, 16f)
            lineTo(18f, 12f)
            curveTo(18f, 11.45f, 17.55f, 11f, 17f, 11f)
            curveTo(16.45f, 11f, 16f, 11.45f, 16f, 12f)
            lineTo(16f, 14f)
            lineTo(14f, 14f)
            lineTo(14f, 12f)
            curveTo(14f, 11.45f, 13.55f, 11f, 13f, 11f)
            curveTo(12.45f, 11f, 12f, 11.45f, 12f, 12f)
            lineTo(12f, 14f)
            lineTo(10f, 14f)
            lineTo(10f, 12f)
            curveTo(10f, 11.45f, 9.55f, 11f, 9f, 11f)
            curveTo(8.45f, 11f, 8f, 11.45f, 8f, 12f)
            lineTo(8f, 14f)
            lineTo(7f, 14f)
            lineTo(7f, 12f)
            close()
        }.build()

    val Pin: ImageVector
        get() = ImageVector.Builder(
            name = "Pin", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 12f)
            lineTo(16f, 4f)
            lineTo(17f, 4f)
            curveTo(17.55f, 4f, 18f, 3.55f, 18f, 3f)
            curveTo(18f, 2.45f, 17.55f, 2f, 17f, 2f)
            lineTo(7f, 2f)
            curveTo(6.45f, 2f, 6f, 2.45f, 6f, 3f)
            curveTo(6f, 3.55f, 6.45f, 4f, 7f, 4f)
            lineTo(8f, 4f)
            lineTo(8f, 12f)
            lineTo(6f, 14f)
            curveTo(5.45f, 14.55f, 5.84f, 15.5f, 6.6f, 15.5f)
            lineTo(11f, 15.5f)
            lineTo(11f, 21.5f)
            curveTo(11f, 22.05f, 11.45f, 22.5f, 12f, 22.5f)
            curveTo(12.55f, 22.5f, 13f, 22.05f, 13f, 21.5f)
            lineTo(13f, 15.5f)
            lineTo(17.4f, 15.5f)
            curveTo(18.16f, 15.5f, 18.55f, 14.55f, 18f, 14f)
            lineTo(16f, 12f)
            close()
        }.build()

    val NavHome: ImageVector
        get() = ImageVector.Builder(
            name = "NavHome", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 20f)
            lineTo(10f, 14f)
            lineTo(14f, 14f)
            lineTo(14f, 20f)
            curveTo(14f, 20.55f, 14.45f, 21f, 15f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 12f)
            lineTo(22.7f, 12f)
            curveTo(23.15f, 12f, 23.38f, 11.46f, 23.06f, 11.14f)
            lineTo(12.7f, 1.83f)
            curveTo(12.3f, 1.47f, 11.7f, 1.47f, 11.3f, 1.83f)
            lineTo(0.94f, 11.14f)
            curveTo(0.62f, 11.46f, 0.85f, 12f, 1.3f, 12f)
            lineTo(3f, 12f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            lineTo(9f, 21f)
            curveTo(9.55f, 21f, 10f, 20.55f, 10f, 20f)
            close()
        }.build()

    val NavAssets: ImageVector
        get() = ImageVector.Builder(
            name = "NavAssets", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(21f, 19f)
            lineTo(21f, 5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            lineTo(5f, 3f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            close()
            moveTo(8.5f, 12.5f)
            lineTo(11f, 15.51f)
            lineTo(14.5f, 11f)
            lineTo(19f, 17f)
            lineTo(5f, 17f)
            lineTo(8.5f, 12.5f)
            close()
        }.build()

    val NavSettings: ImageVector
        get() = ImageVector.Builder(
            name = "NavSettings", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 17f)
            horizontalLineTo(9f)
            verticalLineTo(15f)
            horizontalLineTo(3f)
            verticalLineTo(17f)
            close()
            moveTo(3f, 5f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 19f)
            verticalLineTo(17f)
            horizontalLineTo(21f)
            verticalLineTo(15f)
            horizontalLineTo(13f)
            verticalLineTo(13f)
            horizontalLineTo(11f)
            verticalLineTo(19f)
            horizontalLineTo(13f)
            close()
            moveTo(7f, 9f)
            verticalLineTo(11f)
            horizontalLineTo(21f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(5f, 13f)
            horizontalLineTo(7f)
            verticalLineTo(7f)
            horizontalLineTo(5f)
            verticalLineTo(13f)
            close()
            moveTo(17f, 11f)
            horizontalLineTo(19f)
            verticalLineTo(3f)
            horizontalLineTo(17f)
            verticalLineTo(11f)
            close()
        }.build()

    val Compass: ImageVector
        get() = ImageVector.Builder(
            name = "Compass", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
            curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
            curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
            curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
            close()
            moveTo(12f, 10.9f)
            lineTo(17f, 7f)
            lineTo(13.1f, 12f)
            lineTo(7f, 17f)
            close()
        }.build()

    val Share: ImageVector
        get() = ImageVector.Builder(
            name = "Share", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 16.08f)
            curveTo(17.24f, 16.08f, 16.56f, 16.38f, 16.04f, 16.85f)
            lineTo(8.91f, 12.7f)
            curveTo(8.96f, 12.47f, 9f, 12.24f, 9f, 12f)
            curveTo(9f, 11.76f, 8.96f, 11.53f, 8.91f, 11.3f)
            lineTo(15.96f, 7.19f)
            curveTo(16.5f, 7.69f, 17.21f, 8f, 18f, 8f)
            curveTo(19.66f, 8f, 21f, 6.66f, 21f, 5f)
            curveTo(21f, 3.34f, 19.66f, 2f, 18f, 2f)
            curveTo(16.34f, 2f, 15f, 3.34f, 15f, 5f)
            curveTo(15f, 5.24f, 15.04f, 5.47f, 15.09f, 5.7f)
            lineTo(8.04f, 9.81f)
            curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
            curveTo(4.34f, 9f, 3f, 10.34f, 3f, 12f)
            curveTo(3f, 13.66f, 4.34f, 15f, 6f, 15f)
            curveTo(6.79f, 15f, 7.5f, 14.69f, 8.04f, 14.19f)
            lineTo(15.16f, 18.35f)
            curveTo(15.11f, 18.56f, 15.14f, 18.78f, 15.14f, 19f)
            curveTo(15.14f, 20.66f, 16.48f, 22f, 18.14f, 22f)
            curveTo(19.8f, 22f, 21.14f, 20.66f, 21.14f, 19f)
            curveTo(21.14f, 17.34f, 19.8f, 16.08f, 18.14f, 16.08f)
            close()
        }.build()

    val Message: ImageVector
        get() = ImageVector.Builder(
            name = "Message", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 2f)
            lineTo(4f, 2f)
            curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            lineTo(2f, 22f)
            lineTo(6f, 18f)
            lineTo(20f, 18f)
            curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            lineTo(22f, 4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
        }.build()

    val TwitterX: ImageVector
        get() = ImageVector.Builder(
            name = "TwitterX", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(18.24f, 2.25f)
            lineTo(20.27f, 2.25f)
            lineTo(15.83f, 7.33f)
            lineTo(21.05f, 14.25f)
            lineTo(16.96f, 14.25f)
            lineTo(13.76f, 10.06f)
            lineTo(10.09f, 14.25f)
            lineTo(8.06f, 14.25f)
            lineTo(12.8f, 8.84f)
            lineTo(7.84f, 2.25f)
            lineTo(12.04f, 2.25f)
            lineTo(14.93f, 6.07f)
            close()
            moveTo(17.53f, 13.04f)
            lineTo(18.66f, 13.04f)
            lineTo(11.31f, 3.39f)
            lineTo(10.1f, 3.39f)
            close()
        }.build()

    val Mail: ImageVector
        get() = ImageVector.Builder(
            name = "Mail", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 4f)
            lineTo(4f, 4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            lineTo(22f, 6f)
            curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
            close()
            moveTo(20f, 8f)
            lineTo(12f, 13f)
            lineTo(4f, 8f)
            lineTo(4f, 6f)
            lineTo(12f, 11f)
            lineTo(20f, 6f)
            close()
        }.build()

    val Trash: ImageVector
        get() = ImageVector.Builder(
            name = "Trash", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            lineTo(16f, 21f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            lineTo(18f, 7f)
            lineTo(6f, 7f)
            close()
            moveTo(19f, 4f)
            lineTo(15.5f, 4f)
            lineTo(14.5f, 3f)
            lineTo(9.5f, 3f)
            lineTo(8.5f, 4f)
            lineTo(5f, 4f)
            lineTo(5f, 6f)
            lineTo(19f, 6f)
            close()
        }.build()

    val Template: ImageVector
        get() = ImageVector.Builder(
            name = "Template", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 3f)
            lineTo(5f, 3f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(19f, 11f)
            lineTo(13f, 11f)
            lineTo(13f, 5f)
            lineTo(19f, 5f)
            close()
            moveTo(11f, 5f)
            lineTo(11f, 11f)
            lineTo(5f, 11f)
            lineTo(5f, 5f)
            close()
            moveTo(5f, 13f)
            lineTo(19f, 13f)
            lineTo(19f, 19f)
            lineTo(5f, 19f)
            close()
        }.build()

    val Translate: ImageVector
        get() = ImageVector.Builder(
            name = "Translate", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12.87f, 15.07f)
            lineTo(10.33f, 12.56f)
            lineTo(10.38f, 12.51f)
            curveTo(12.28f, 10.37f, 13.64f, 7.77f, 14.44f, 5f)
            lineTo(18f, 5f)
            lineTo(18f, 3f)
            lineTo(11f, 3f)
            lineTo(11f, 1f)
            lineTo(9f, 1f)
            lineTo(9f, 3f)
            lineTo(2f, 3f)
            lineTo(2f, 5f)
            lineTo(12.56f, 5f)
            curveTo(11.85f, 7.36f, 10.75f, 9.53f, 9.3f, 11.5f)
            curveTo(8.37f, 10.47f, 7.6f, 9.3f, 7f, 8f)
            lineTo(5f, 8f)
            curveTo(5.73f, 9.68f, 6.75f, 11.23f, 8f, 12.56f)
            lineTo(2.93f, 17.58f)
            lineTo(4.34f, 19f)
            lineTo(9.3f, 14.04f)
            lineTo(12.11f, 16.85f)
            lineTo(12.87f, 15.07f)
            close()
            moveTo(18.5f, 10f)
            lineTo(16.5f, 10f)
            lineTo(12f, 22f)
            lineTo(14f, 22f)
            lineTo(15.12f, 19f)
            lineTo(19.88f, 19f)
            lineTo(21f, 22f)
            lineTo(23f, 22f)
            lineTo(18.5f, 10f)
            close()
            moveTo(15.88f, 17f)
            lineTo(17.5f, 12.67f)
            lineTo(19.12f, 17f)
            lineTo(15.88f, 17f)
            close()
        }.build()

    val Megaphone: ImageVector
        get() = ImageVector.Builder(
            name = "Megaphone", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 12f)
            curveTo(20.35f, 12f, 20.68f, 11.86f, 20.92f, 11.62f)
            curveTo(21.41f, 11.13f, 21.41f, 10.33f, 20.92f, 9.84f)
            lineTo(17f, 6f)
            lineTo(17f, 18f)
            lineTo(20.92f, 14.16f)
            curveTo(21.16f, 13.92f, 21.3f, 13.59f, 21.3f, 13.24f)
            curveTo(21.3f, 12.56f, 20.72f, 12f, 20f, 12f)
            close()
            moveTo(15f, 4f)
            lineTo(8f, 8.5f)
            lineTo(3f, 8.5f)
            curveTo(2.45f, 8.5f, 2f, 8.95f, 2f, 9.5f)
            lineTo(2f, 14.5f)
            curveTo(2f, 15.05f, 2.45f, 15.5f, 3f, 15.5f)
            lineTo(5f, 15.5f)
            lineTo(6f, 20.5f)
            curveTo(6.11f, 21.05f, 6.55f, 21.5f, 7.1f, 21.5f)
            lineTo(9.9f, 21.5f)
            curveTo(10.55f, 21.5f, 11f, 21.05f, 10.9f, 20.4f)
            lineTo(10f, 15.5f)
            lineTo(15f, 20f)
            curveTo(15.55f, 20.5f, 16f, 20.1f, 16f, 19.5f)
            lineTo(16f, 4.5f)
            curveTo(16f, 3.9f, 15.55f, 3.5f, 15f, 4f)
            close()
        }.build()

    val Swap: ImageVector
        get() = ImageVector.Builder(
            name = "Swap", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 8f)
            lineTo(15f, 12f)
            lineTo(18f, 12f)
            curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
            curveTo(10.97f, 18f, 10.01f, 17.74f, 9.17f, 17.28f)
            lineTo(7.71f, 18.74f)
            curveTo(8.93f, 19.53f, 10.41f, 20f, 12f, 20f)
            curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
            lineTo(23f, 12f)
            lineTo(19f, 8f)
            close()
            moveTo(6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(13.03f, 6f, 13.99f, 6.26f, 14.83f, 6.72f)
            lineTo(16.29f, 5.26f)
            curveTo(15.07f, 4.47f, 13.59f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            lineTo(1f, 12f)
            lineTo(5f, 16f)
            lineTo(9f, 12f)
            lineTo(6f, 12f)
            close()
        }.build()

    val FilmRoll: ImageVector
        get() = ImageVector.Builder(
            name = "FilmRoll", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 4f)
            lineTo(20f, 4f)
            curveTo(21.1f, 4f, 22f, 4.9f, 22f, 6f)
            lineTo(22f, 18f)
            curveTo(22f, 19.1f, 21.1f, 20f, 20f, 20f)
            lineTo(18f, 20f)
            lineTo(18f, 18f)
            lineTo(20f, 18f)
            lineTo(20f, 15f)
            lineTo(18f, 15f)
            lineTo(18f, 13f)
            lineTo(20f, 13f)
            lineTo(20f, 11f)
            lineTo(18f, 11f)
            lineTo(18f, 9f)
            lineTo(20f, 9f)
            lineTo(20f, 6f)
            lineTo(18f, 6f)
            close()
            moveTo(16f, 4f)
            lineTo(16f, 20f)
            lineTo(4f, 20f)
            curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f)
            lineTo(2f, 6f)
            curveTo(2f, 4.9f, 2.9f, 4f, 4f, 4f)
            lineTo(16f, 4f)
            close()
            moveTo(6f, 6f)
            lineTo(14f, 6f)
            lineTo(14f, 11f)
            lineTo(6f, 11f)
            close()
            moveTo(6f, 13f)
            lineTo(14f, 13f)
            lineTo(14f, 18f)
            lineTo(6f, 18f)
            close()
        }.build()

    val Ugc: ImageVector
        get() = ImageVector.Builder(
            name = "Ugc", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 12f)
            curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
            curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
            curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
            curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
            close()
            moveTo(12f, 14f)
            curveTo(9.33f, 14f, 4f, 15.33f, 4f, 18f)
            lineTo(4f, 20f)
            lineTo(20f, 20f)
            lineTo(20f, 18f)
            curveTo(20f, 15.33f, 14.67f, 14f, 12f, 14f)
            close()
            moveTo(18.5f, 2f)
            lineTo(19.5f, 4.5f)
            lineTo(22f, 5f)
            lineTo(20f, 6.8f)
            lineTo(20.5f, 9.3f)
            lineTo(18.5f, 8f)
            lineTo(16.5f, 9.3f)
            lineTo(17f, 6.8f)
            lineTo(15f, 5f)
            lineTo(17.5f, 4.5f)
            close()
        }.build()

    val Tag: ImageVector
        get() = ImageVector.Builder(
            name = "Tag", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(21.41f, 11.58f)
            lineTo(12.42f, 2.58f)
            curveTo(12.05f, 2.22f, 11.55f, 2f, 11f, 2f)
            lineTo(4f, 2f)
            curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            lineTo(2f, 11f)
            curveTo(2f, 11.55f, 2.22f, 12.05f, 2.59f, 12.42f)
            lineTo(11.59f, 21.42f)
            curveTo(11.96f, 21.79f, 12.46f, 22f, 13f, 22f)
            curveTo(13.54f, 22f, 14.04f, 21.79f, 14.41f, 21.41f)
            lineTo(21.41f, 14.41f)
            curveTo(21.78f, 14.04f, 22f, 13.54f, 22f, 13f)
            curveTo(22f, 12.46f, 21.78f, 11.96f, 21.41f, 11.58f)
            close()
            moveTo(6.5f, 8f)
            curveTo(5.67f, 8f, 5f, 7.33f, 5f, 6.5f)
            curveTo(5f, 5.67f, 5.67f, 5f, 6.5f, 5f)
            curveTo(7.33f, 5f, 8f, 5.67f, 8f, 6.5f)
            curveTo(8f, 7.33f, 7.33f, 8f, 6.5f, 8f)
            close()
        }.build()

    val SignOut: ImageVector
        get() = ImageVector.Builder(
            name = "SignOut", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            // Arrow pointing right out of a door shape
            moveTo(10.09f, 15.59f)
            lineTo(11.5f, 17f)
            lineTo(16.5f, 12f)
            lineTo(11.5f, 7f)
            lineTo(10.09f, 8.41f)
            lineTo(12.67f, 11f)
            lineTo(3f, 11f)
            lineTo(3f, 13f)
            lineTo(12.67f, 13f)
            close()
            moveTo(19f, 3f)
            lineTo(5f, 3f)
            curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 9f)
            lineTo(5f, 9f)
            lineTo(5f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 19f)
            lineTo(5f, 19f)
            lineTo(5f, 15f)
            lineTo(3f, 15f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
        }.build()

    val Login: ImageVector
        get() = ImageVector.Builder(
            name = "Login", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            // Arrow pointing into a door shape (sign in)
            moveTo(13.91f, 15.59f)
            lineTo(12.5f, 17f)
            lineTo(7.5f, 12f)
            lineTo(12.5f, 7f)
            lineTo(13.91f, 8.41f)
            lineTo(11.33f, 11f)
            lineTo(21f, 11f)
            lineTo(21f, 13f)
            lineTo(11.33f, 13f)
            close()
            moveTo(5f, 3f)
            lineTo(19f, 3f)
            curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
            lineTo(21f, 9f)
            lineTo(19f, 9f)
            lineTo(19f, 5f)
            lineTo(5f, 5f)
            lineTo(5f, 19f)
            lineTo(19f, 19f)
            lineTo(19f, 15f)
            lineTo(21f, 15f)
            lineTo(21f, 19f)
            curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
            lineTo(5f, 21f)
            curveTo(3.89f, 21f, 3f, 20.1f, 3f, 19f)
            lineTo(3f, 5f)
            curveTo(3f, 3.9f, 3.89f, 3f, 5f, 3f)
            close()
        }.build()

    val Warning: ImageVector
        get() = ImageVector.Builder(
            name = "Warning", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            // Triangle with exclamation
            moveTo(1f, 21f)
            lineTo(23f, 21f)
            lineTo(12f, 2f)
            close()
            moveTo(13f, 18f)
            lineTo(11f, 18f)
            lineTo(11f, 16f)
            lineTo(13f, 16f)
            close()
            moveTo(13f, 14f)
            lineTo(11f, 14f)
            lineTo(11f, 10f)
            lineTo(13f, 10f)
            close()
        }.build()

    val Bolt: ImageVector
        get() = ImageVector.Builder(
            name = "Bolt", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 21f)
            curveTo(11f, 21.55f, 11.53f, 21.91f, 12f, 21.65f)
            lineTo(19.2f, 12.65f)
            curveTo(19.65f, 12.09f, 19.25f, 11.25f, 18.5f, 11.25f)
            lineTo(13f, 11.25f)
            lineTo(15.8f, 3.85f)
            curveTo(16.1f, 3.1f, 15.4f, 2.35f, 14.65f, 2.65f)
            lineTo(8.2f, 11.15f)
            curveTo(7.75f, 11.75f, 8.18f, 12.6f, 8.95f, 12.6f)
            lineTo(13f, 12.6f)
            lineTo(11f, 21f)
            close()
        }.build()

    val Edit: ImageVector
        get() = ImageVector.Builder(
            name = "Edit", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 17.25f)
            lineTo(3f, 21f)
            lineTo(6.75f, 21f)
            lineTo(17.81f, 9.94f)
            lineTo(14.06f, 6.19f)
            lineTo(3f, 17.25f)
            close()
            moveTo(20.71f, 7.04f)
            curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
            lineTo(18.37f, 3.29f)
            curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
            lineTo(15.13f, 5.12f)
            lineTo(18.88f, 8.87f)
            lineTo(20.71f, 7.04f)
            close()
        }.build()

    val Notification: ImageVector
        get() = ImageVector.Builder(
            name = "Notification", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 22f)
            curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
            lineTo(10f, 20f)
            curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
            close()
            moveTo(18f, 16f)
            lineTo(18f, 11f)
            curveTo(18f, 7.93f, 16.37f, 5.36f, 13.5f, 4.68f)
            lineTo(13.5f, 4f)
            curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f)
            curveTo(11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f)
            lineTo(10.5f, 4.68f)
            curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f)
            lineTo(6f, 16f)
            lineTo(4f, 18f)
            lineTo(4f, 19f)
            lineTo(20f, 19f)
            lineTo(20f, 18f)
            lineTo(18f, 16f)
            close()
            moveTo(16f, 17f)
            lineTo(8f, 17f)
            lineTo(8f, 11f)
            curveTo(8f, 8.52f, 9.51f, 6.5f, 12f, 6.5f)
            curveTo(14.49f, 6.5f, 16f, 8.52f, 16f, 11f)
            lineTo(16f, 17f)
            close()
        }.build()
}

