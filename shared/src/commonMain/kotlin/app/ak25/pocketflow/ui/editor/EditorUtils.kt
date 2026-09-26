package app.ak25.pocketflow.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.ak25.pocketflow.models.NodeType
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs

/**
 * Calculates the shortest distance from point [p] to the line segment defined by points [a] and [b].
 */
fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = p - a
    val abLenSq = ab.x * ab.x + ab.y * ab.y
    if (abLenSq == 0f) return (p - a).getDistance()
    val t = (ap.x * ab.x + ap.y * ab.y) / abLenSq
    val clampedT = t.coerceIn(0f, 1f)
    val projection = a + ab * clampedT
    return (p - projection).getDistance()
}

/**
 * Determines whether a tap is close enough to an edge defined by [start] and [end].
 * The [threshold] defines how many pixels away from the curve are considered a hit.
 */
fun isTapNearEdge(tap: Offset, start: Offset, end: Offset, threshold: Float = 40f): Boolean {
    // Quick bounding-box rejection for performance
    val minX = min(start.x, end.x) - threshold
    val maxX = max(start.x, end.x) + threshold
    val minY = min(start.y, end.y) - threshold
    val maxY = max(start.y, end.y) + threshold
    if (tap.x < minX || tap.x > maxX || tap.y < minY || tap.y > maxY) return false

    // Approximate the cubic Bézier curve with 20 samples
    val dx = abs(end.x - start.x) * 0.5f
    val c1 = Offset(start.x + dx, start.y)
    val c2 = Offset(end.x - dx, end.y)
    var minDistance = Float.MAX_VALUE
    for (i in 0..20) {
        val t = i / 20f
        val t1 = 1f - t
        val px = t1*t1*t1*start.x + 3*t1*t1*t*c1.x + 3*t1*t*t*c2.x + t*t*t*end.x
        val py = t1*t1*t1*start.y + 3*t1*t1*t*c1.y + 3*t1*t*t*c2.y + t*t*t*end.y
        val dist = (Offset(px, py) - tap).getDistance()
        if (dist < minDistance) minDistance = dist
    }
    return minDistance < threshold
}

/** Returns the theme colour and icon for a given [NodeType]. */
fun getNodeTheme(type: NodeType): Pair<Color, ImageVector> = when (type) {
    NodeType.IMAGE_GENERATION      -> Color(0xFFBA68C8) to AppIcons.Image
    NodeType.UPLOADED_IMAGE        -> Color(0xFF64B5F6) to AppIcons.Image
    NodeType.IMAGE_TO_VIDEO        -> Color(0xFFF06292) to AppIcons.Video
    NodeType.TEXT_PROMPT           -> Color(0xFF4DD0E1) to AppIcons.Text
    NodeType.TEXT_TO_SPEECH        -> Color(0xFF81C784) to AppIcons.Audio
    NodeType.MODEL3D_GENERATION    -> Color(0xFFCE93D8) to AppIcons.Image
    NodeType.AD_LOCALIZATION       -> Color(0xFFFFB74D) to AppIcons.Translate
    NodeType.MARKETING_STOCK_IMAGE -> Color(0xFF9CCC65) to AppIcons.Tag
    NodeType.PRODUCT_AD            -> Color(0xFF4FC3F7) to AppIcons.Megaphone
    NodeType.PRODUCT_CAMPAIGN      -> Color(0xFFFF8A65) to AppIcons.Image
    NodeType.PRODUCT_SWAP          -> Color(0xFF81C784) to AppIcons.Swap
    NodeType.MULTI_SHOT_VIDEO      -> Color(0xFFBA68C8) to AppIcons.FilmRoll
    NodeType.PRODUCT_UGC           -> Color(0xFF4DB6AC) to AppIcons.Ugc
    NodeType.NOTE                  -> Color(0xFF66BB6A) to AppIcons.Message
}

/** Returns a human-readable description for a given [NodeType]. */
fun getNodeDescription(type: NodeType): String = when (type) {
    NodeType.TEXT_PROMPT           -> "Enter a text prompt"
    NodeType.IMAGE_GENERATION      -> "Generate image from text"
    NodeType.UPLOADED_IMAGE        -> "Upload an image"
    NodeType.IMAGE_TO_VIDEO        -> "Generate video from image"
    NodeType.TEXT_TO_SPEECH        -> "Text to speech audio"
    NodeType.MODEL3D_GENERATION    -> "Generate 3D model"
    NodeType.AD_LOCALIZATION       -> "Localize an ad image"
    NodeType.MARKETING_STOCK_IMAGE -> "Generate marketing stock images"
    NodeType.PRODUCT_AD            -> "Create a product ad video"
    NodeType.PRODUCT_CAMPAIGN      -> "Create product campaign images"
    NodeType.PRODUCT_SWAP          -> "Swap a product in a video"
    NodeType.MULTI_SHOT_VIDEO      -> "Create a multi-shot video"
    NodeType.PRODUCT_UGC           -> "Create a product UGC video"
    NodeType.NOTE                  -> "Write notes or a todo checklist"
}
