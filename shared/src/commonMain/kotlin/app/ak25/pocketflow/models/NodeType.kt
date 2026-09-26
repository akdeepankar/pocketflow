package app.ak25.pocketflow.models

import kotlinx.serialization.Serializable

@Serializable
enum class NodeType(
    val nodeName: String,
    val inputs: List<PortDefinition>,
    val outputs: List<PortDefinition>,
    val defaultParams: Map<String, String>
) {
    TEXT_PROMPT(
        nodeName = "Text Prompt",
        inputs = emptyList(),
        outputs = listOf(PortDefinition("text", PortDataType.TEXT)),
        defaultParams = mapOf("text" to "")
    ),
    IMAGE_GENERATION(
        nodeName = "Image Generation",
        inputs = listOf(
            PortDefinition("reference", PortDataType.IMAGE, isOptional = true, maxConnections = 2),
            PortDefinition("prompt", PortDataType.TEXT)
        ),
        outputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        defaultParams = mapOf("prompt" to "", "aspectRatio" to "16:9", "model" to "gemini_image3_pro")
    ),
    UPLOADED_IMAGE(
        nodeName = "Local Image",
        inputs = emptyList(),
        outputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        defaultParams = mapOf("imageUri" to "")
    ),
    IMAGE_TO_VIDEO(
        nodeName = "Video",
        inputs = listOf(
            PortDefinition("image", PortDataType.IMAGE, isOptional = true, maxConnections = 2),
            PortDefinition("prompt", PortDataType.TEXT, isOptional = true)
        ),
        outputs = listOf(PortDefinition("video", PortDataType.VIDEO)),
        defaultParams = mapOf("prompt" to "", "model" to "veo3.1_fast", "duration" to "5", "aspectRatio" to "16:9", "audio" to "true")
    ),
    TEXT_TO_SPEECH(
        nodeName = "Speech",
        inputs = listOf(PortDefinition("prompt", PortDataType.TEXT, isOptional = true)),
        outputs = listOf(PortDefinition("audio", PortDataType.AUDIO)),
        defaultParams = mapOf("text" to "", "voicePreset" to "Maya")
    ),

    MODEL3D_GENERATION(
        nodeName = "3D Model Generation",
        inputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        outputs = listOf(PortDefinition("model3d", PortDataType.MODEL3D)),
        defaultParams = emptyMap()
    ),
    AD_LOCALIZATION(
        nodeName = "Localize",
        inputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        outputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        defaultParams = mapOf("targetLanguage" to "es")
    ),
    MARKETING_STOCK_IMAGE(
        nodeName = "Marketing",
        inputs = listOf(PortDefinition("image", PortDataType.IMAGE, isOptional = true)),
        outputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        defaultParams = mapOf("prompt" to "", "outputCount" to "4", "quality" to "high")
    ),
    PRODUCT_AD(
        nodeName = "Product Ad",
        inputs = listOf(PortDefinition("productImage", PortDataType.IMAGE, maxConnections = 10), PortDefinition("styleImage", PortDataType.IMAGE, isOptional = true, maxConnections = 4)),
        outputs = listOf(PortDefinition("video", PortDataType.VIDEO)),
        defaultParams = mapOf("productInfo" to "", "userConcept" to "", "ratio" to "1280:720", "duration" to "10", "audio" to "false")
    ),
    PRODUCT_CAMPAIGN(
        nodeName = "Product Campaign",
        inputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        outputs = listOf(PortDefinition("image", PortDataType.IMAGE)),
        defaultParams = mapOf("prompt" to "")
    ),
    PRODUCT_SWAP(
        nodeName = "Product Swap",
        inputs = listOf(
            PortDefinition("referenceVideo", PortDataType.VIDEO),
            PortDefinition("originalProduct", PortDataType.IMAGE),
            PortDefinition("newProduct", PortDataType.IMAGE, maxConnections = 10)
        ),
        outputs = listOf(PortDefinition("video", PortDataType.VIDEO)),
        defaultParams = mapOf("duration" to "10", "resolution" to "720p", "audio" to "true")
    ),
    MULTI_SHOT_VIDEO(
        nodeName = "Multishot",
        inputs = listOf(PortDefinition("firstFrame", PortDataType.IMAGE, isOptional = true)),
        outputs = listOf(PortDefinition("video", PortDataType.VIDEO)),
        defaultParams = mapOf("prompt" to "", "ratio" to "1280:720", "duration" to "10", "audio" to "true")
    ),
    PRODUCT_UGC(
        nodeName = "Product UGC",
        inputs = listOf(
            PortDefinition("characterImage", PortDataType.IMAGE),
            PortDefinition("productImage", PortDataType.IMAGE)
        ),
        outputs = listOf(PortDefinition("video", PortDataType.VIDEO)),
        defaultParams = mapOf("productInfo" to "", "userConcept" to "", "duration" to "15", "ratio" to "720:1280", "audio" to "true")
    ),
    NOTE(
        nodeName = "Notes",
        inputs = emptyList(),
        outputs = emptyList(),
        defaultParams = mapOf(
            "content" to "",     // free-form note text
            "mode" to "note",    // "note" | "todo"
            "items" to "",       // todo items, newline-separated
            "checked" to ""      // comma-separated indices of ticked items
        )
    )
}

@Serializable
data class PortDefinition(
    val id: String,
    val dataType: PortDataType,
    val isOptional: Boolean = false,
    val maxConnections: Int = 1
)
