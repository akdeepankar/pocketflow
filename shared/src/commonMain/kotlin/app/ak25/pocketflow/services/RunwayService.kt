package app.ak25.pocketflow.services

import app.ak25.pocketflow.ui.utils.ImageCache
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import app.ak25.pocketflow.models.NodeType


class RunwayService {
    private var runwayApiKey = Env.RUNWAY_API_KEY
    private val runwayBaseUrl = "https://api.dev.runwayml.com/v1"
    var onRemoteUrlGenerated: ((String) -> Unit)? = null

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun toApiUri(uri: String): String {
        println("DEBUG: toApiUri called with: $uri")
        if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("data:")) {
            // Supabase Storage URLs may be authenticated — download and embed as base64 if needed.
            if (uri.startsWith("http") && SupabaseRepository.isSupabaseStorageUrl(uri)) {
                val bytes = SupabaseRepository.downloadStorageFile(uri)
                    ?: throw Exception("Failed to download Supabase storage file: $uri")
                val base64 = Base64.Default.encode(bytes)
                println("DEBUG: toApiUri embedded Supabase storage file. base64Length=${base64.length}")
                return "data:image/png;base64,$base64"
            }
            return uri
        }
        
        if (uri.startsWith("cache://")) {
            val id = uri.removePrefix("cache://")
            val bytes = ImageCache.get(id) ?: throw Exception("Image not found in cache: $id")
            val base64 = Base64.Default.encode(bytes)
            return "data:image/png;base64,$base64"
        }
        
        try {
            val localBytes = app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(uri)
            if (localBytes != null) {
                val base64 = Base64.Default.encode(localBytes)
                val mimeType = if (uri.contains(".mp4", ignoreCase = true) || uri.contains(".webm", ignoreCase = true)) "video/mp4" else "image/png"
                println("DEBUG: toApiUri encoded local file. base64Length=${base64.length}, mimeType=$mimeType")
                return "data:$mimeType;base64,$base64"
            }
        } catch (e: Exception) {
            // ignore
        }

        throw Exception("Unsupported URI format: $uri")
    }

    var onTaskIdGenerated: ((String) -> Unit)? = null
    var activeLiveActivityMetadata: JsonObject? = null

    /**
     * Submit a Runway task.
     *
     * Routing:
     *   1. Try via Supabase Edge Function `runway-generate` (API key stays server-side).
     *   2. If that fails (JWT absent, function not deployed), fall back to direct call.
     */
    private suspend fun triggerLiveActivityPolling(jobId: String) {
        val meta = activeLiveActivityMetadata ?: return
        try {
            SupabaseRepository.pollLiveActivityJob(
                jobId = jobId,
                provider = "runway",
                liveActivity = meta
            )
        } catch (e: Exception) {
            println("[RunwayService] Failed to trigger Live Activity poll-job: ${e.message}")
        }
    }

    private suspend fun runwayFetch(endpoint: String, body: JsonObject): JsonObject {
        // ── Path 1: via Supabase Function ──────────────────────────────────────
        val jwt = app.ak25.pocketflow.storage.LocalStorage.loadString("supabase_jwt")
            ?: app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_jwt")
        if (!jwt.isNullOrEmpty()) {
            try {
                val jobId = SupabaseRepository.invokeRunwayFunction(
                    endpoint = endpoint,
                    payload = body
                )
                if (jobId != null) {
                    onTaskIdGenerated?.invoke(jobId)
                    triggerLiveActivityPolling(jobId)
                    // Return a minimal JsonObject matching what callers expect
                    return kotlinx.serialization.json.buildJsonObject {
                        put("id", JsonPrimitive(jobId))
                        put("status", JsonPrimitive("PENDING"))
                    }
                }
            } catch (e: Exception) {
                println("[RunwayService] Supabase Function failed, falling back to direct call: ${e.message}")
            }
        }

        // ── Path 2: direct call fallback ───────────────────────────────────────
        return runwayFetchDirect(endpoint, body)
    }

    private suspend fun runwayFetchDirect(endpoint: String, body: JsonObject): JsonObject {
        try {
            val response = httpClient.post("$runwayBaseUrl$endpoint") {
                header("Authorization", "Bearer $runwayApiKey")
                header("X-Runway-Version", "2024-11-06")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                val errorBody = response.bodyAsText()
                throw Exception("Runway ${response.status.value}: $errorBody")
            }
            val json = response.body<JsonObject>()
            json["id"]?.jsonPrimitive?.content?.let { id ->
                onTaskIdGenerated?.invoke(id)
                triggerLiveActivityPolling(id)
            }
            return json
        } catch (e: Exception) {
            val isKeyError = e.message?.contains("401") == true || 
                             e.message?.contains("403") == true || 
                             e.message?.contains("402") == true ||
                             e.message?.contains("insufficient") == true ||
                             e.message?.contains("balance") == true ||
                             e.message?.contains("credit") == true ||
                             e.message?.contains("limit") == true

            if (isKeyError && runwayApiKey == Env.RUNWAY_API_KEY) {
                println("RunwayService: Primary API key failed/low balance, falling back to second API key...")
                runwayApiKey = Env.RUNWAY_API_KEY_FALLBACK
                
                val response = httpClient.post("$runwayBaseUrl$endpoint") {
                    header("Authorization", "Bearer $runwayApiKey")
                    header("X-Runway-Version", "2024-11-06")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

                if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                    val errorBody = response.bodyAsText()
                    throw Exception("Runway ${response.status.value}: $errorBody")
                }
                val json = response.body<JsonObject>()
                json["id"]?.jsonPrimitive?.content?.let { id ->
                    onTaskIdGenerated?.invoke(id)
                    triggerLiveActivityPolling(id)
                }
                return json
            } else {
                throw e
            }
        }
    }

    suspend fun pollAndDownloadTask(taskId: String, type: NodeType): String {
        val task = pollTask(taskId)
        val outputs = task["output"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            ?: task["artifactUrl"]?.jsonPrimitive?.content?.let { listOf(it) }
            ?: throw Exception("No output URL found")
            
        return when (type) {
            NodeType.IMAGE_GENERATION, NodeType.AD_LOCALIZATION -> {
                downloadToLocal(outputs.first(), "png")
            }
            NodeType.MARKETING_STOCK_IMAGE, NodeType.PRODUCT_CAMPAIGN -> {
                outputs.map { downloadToLocal(it, "png") }.joinToString(",")
            }
            NodeType.IMAGE_TO_VIDEO, NodeType.PRODUCT_AD, NodeType.PRODUCT_SWAP, NodeType.MULTI_SHOT_VIDEO, NodeType.PRODUCT_UGC -> {
                val remoteUrl = outputs.first()
                val ext = if (remoteUrl.contains(".mp3") || remoteUrl.contains(".wav")) "mp3" else "mp4"
                downloadToLocal(remoteUrl, ext)
            }
            NodeType.TEXT_TO_SPEECH -> {
                downloadToLocal(outputs.first(), "mp3")
            }
            else -> throw Exception("Unsupported Runway node type for polling")
        }
    }


    suspend fun getTask(taskId: String): JsonObject = pollTask(taskId)

    private suspend fun pollTask(taskId: String): JsonObject {
        var consecutiveErrors = 0
        var lastRenewTime = app.ak25.pocketflow.utils.getCurrentTimeMillis()
        for (i in 0 until 180) { // Poll up to 15 minutes
            delay(5000)
            val now = app.ak25.pocketflow.utils.getCurrentTimeMillis()
            if (now - lastRenewTime >= 20_000L) {
                lastRenewTime = now
                app.ak25.pocketflow.storage.LocalStorage.beginBackgroundTask("pocketflow_poll_$taskId")
            }
            try {
                val response = httpClient.get("$runwayBaseUrl/tasks/$taskId") {
                    header("Authorization", "Bearer $runwayApiKey")
                    header("X-Runway-Version", "2024-11-06")
                }

                if (response.status != HttpStatusCode.OK) {
                    println("[RunwayService] Poll HTTP status ${response.status.value}, retrying...")
                    consecutiveErrors++
                    if (consecutiveErrors > 25) {
                        throw Exception("Poll error: ${response.status.value}")
                    }
                    continue
                }

                val data = response.body<JsonObject>()
                val status = data["status"]?.jsonPrimitive?.content

                consecutiveErrors = 0

                if (status == "SUCCEEDED") {
                    return data
                } else if (status == "FAILED") {
                    val failure = data["failure"]?.jsonPrimitive?.content ?: data["failureCode"]?.jsonPrimitive?.content ?: "Task failed"
                    throw Exception("Task failed: $failure")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = e.message ?: ""
                if (msg.startsWith("Task failed:")) {
                    throw e
                }
                consecutiveErrors++
                println("[RunwayService] Poll network exception (attempt $consecutiveErrors/25): $msg")
                if (consecutiveErrors > 25) {
                    throw e
                }
            }
        }
        throw Exception("Task timed out after 15 minutes")
    }

    private fun getImageRatio(model: String, aspectRatio: String?): String {
        val ar = aspectRatio ?: "16:9"
        if (model == "gpt_image_2") {
            return mapOf(
                "16:9" to "1920:1088",
                "9:16" to "1088:1920",
                "1:1" to "1920:1920"
            )[ar] ?: "1920:1088"
        }
        return mapOf(
            "16:9" to "1344:768",
            "9:16" to "768:1344",
            "1:1" to "1024:1024",
            "4:3" to "1152:896",
            "3:4" to "896:1152"
        )[ar] ?: "1344:768"
    }

    suspend fun textToImage(
        prompt: String,
        model: String = "gemini_image3_pro",
        aspectRatio: String? = null,
        referenceImageUris: List<String>? = null,
        resolution: String? = null,
        quality: String? = null
    ): String {
        val ratio = if (model == "gemini_image3_pro" && !resolution.isNullOrEmpty() && !aspectRatio.isNullOrEmpty()) {
            // Resolution can be 1K, 2K, 4K. Aspect Ratio can be 16:9, 9:16, 1:1.
            val key = "${aspectRatio}_${resolution}"
            val map = mapOf(
                "16:9_1K" to "1344:768",
                "16:9_2K" to "2528:1696",
                "16:9_4K" to "5056:3392",
                "9:16_1K" to "768:1344",
                "9:16_2K" to "1696:2528",
                "9:16_4K" to "3392:5056",
                "1:1_1K" to "1024:1024",
                "1:1_2K" to "2048:2048",
                "1:1_4K" to "4096:4096"
            )
            map[key] ?: getImageRatio(model, aspectRatio)
        } else {
            getImageRatio(model, aspectRatio)
        }
        
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "model" to kotlinx.serialization.json.JsonPrimitive(model),
            "promptText" to kotlinx.serialization.json.JsonPrimitive(prompt),
            "ratio" to kotlinx.serialization.json.JsonPrimitive(ratio)
        )

        if (model == "gpt_image_2" && !quality.isNullOrEmpty()) {
            map["quality"] = kotlinx.serialization.json.JsonPrimitive(quality)
        }

        if (model == "gemini_image3_pro" && !referenceImageUris.isNullOrEmpty()) {
            val references = referenceImageUris.map { uri ->
                kotlinx.serialization.json.JsonObject(mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(uri))))
            }
            map["referenceImages"] = kotlinx.serialization.json.JsonArray(references)
        }

        val result = runwayFetch("/text_to_image", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)

        val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: task["artifactUrl"]?.jsonPrimitive?.content
            ?: throw Exception("No output URL found")
        return downloadToLocal(remoteUrl, "png")
    }

    suspend fun imageToVideo(
        model: String = "veo3.1_fast",
        firstFrameUri: String? = null,
        lastFrameUri: String? = null,
        prompt: String? = null,
        duration: Int = 5,
        aspectRatio: String? = null,
        audio: Boolean = true
    ): String {
        val hasImages = !firstFrameUri.isNullOrEmpty()
        var modelCfg = RunwayModels.getVideoModel(model)

        if (!hasImages && !modelCfg.supportsTextOnly) {
            modelCfg = RunwayModels.getVideoModel("veo3.1_fast")
        }

        val ratio = aspectRatio?.let { modelCfg.ratios[it] } ?: modelCfg.ratios.values.first()
        val dur = RunwayModels.clampDuration(modelCfg, duration)

        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "model" to kotlinx.serialization.json.JsonPrimitive(modelCfg.key),
            "ratio" to kotlinx.serialization.json.JsonPrimitive(ratio),
            "duration" to kotlinx.serialization.json.JsonPrimitive(dur)
        )

        if (modelCfg.supportsAudio) {
            map["audio"] = kotlinx.serialization.json.JsonPrimitive(audio)
        }

        if (!prompt.isNullOrEmpty()) {
            map["promptText"] = kotlinx.serialization.json.JsonPrimitive(prompt)
        }

        if (hasImages) {
            val promptImages = mutableListOf<kotlinx.serialization.json.JsonObject>()
            promptImages.add(
                kotlinx.serialization.json.JsonObject(mapOf(
                    "uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(firstFrameUri!!)),
                    "position" to kotlinx.serialization.json.JsonPrimitive("first")
                ))
            )
            
            if (!lastFrameUri.isNullOrEmpty() && modelCfg.maxImages > 1) {
                promptImages.add(
                    kotlinx.serialization.json.JsonObject(mapOf(
                        "uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(lastFrameUri)),
                        "position" to kotlinx.serialization.json.JsonPrimitive("last")
                    ))
                )
            }
            
            map["promptImage"] = kotlinx.serialization.json.JsonArray(promptImages)

            val result = runwayFetch("/image_to_video", JsonObject(map))
            val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
            val task = pollTask(taskId)
            val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                ?: task["artifactUrl"]?.jsonPrimitive?.content
                ?: throw Exception("No output URL found")
            return downloadToLocal(remoteUrl, "mp4")
        } else {
            if (prompt.isNullOrEmpty()) {
                throw Exception("Either a prompt or image is required")
            }
            val result = runwayFetch("/text_to_video", JsonObject(map))
            val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
            val task = pollTask(taskId)
            val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                ?: task["artifactUrl"]?.jsonPrimitive?.content
                ?: throw Exception("No output URL found")
            return downloadToLocal(remoteUrl, "mp4")
        }
    }

    suspend fun textToSpeech(text: String, voicePreset: String = "Maya"): String {
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "model" to kotlinx.serialization.json.JsonPrimitive("eleven_multilingual_v2"),
            "promptText" to kotlinx.serialization.json.JsonPrimitive(text),
            "voice" to kotlinx.serialization.json.JsonObject(
                mapOf(
                    "type" to kotlinx.serialization.json.JsonPrimitive("runway-preset"),
                    "presetId" to kotlinx.serialization.json.JsonPrimitive(voicePreset)
                )
            )
        )

        val result = runwayFetch("/text_to_speech", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)

        val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: task["artifactUrl"]?.jsonPrimitive?.content
            ?: throw Exception("No output URL found")
        return downloadToLocal(remoteUrl, "mp3")
    }

    suspend fun adLocalization(imageUri: String, targetLanguage: String): String {
        val map = mapOf(
            "version" to kotlinx.serialization.json.JsonPrimitive("2026-06"),
            "referenceImage" to kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(imageUri)))
            ),
            "targetLanguage" to kotlinx.serialization.json.JsonPrimitive(targetLanguage)
        )
        val result = runwayFetch("/recipes/ad_localization", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)
        val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: task["artifactUrl"]?.jsonPrimitive?.content
            ?: throw Exception("No output URL found")
        return downloadToLocal(remoteUrl, "png")
    }

    suspend fun marketingStockImage(
        prompt: String,
        imageUri: String? = null,
        outputCount: Int = 4,
        quality: String = "high"
    ): List<String> {
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "version" to kotlinx.serialization.json.JsonPrimitive("2026-06"),
            "prompt" to kotlinx.serialization.json.JsonPrimitive(prompt),
            "outputCount" to kotlinx.serialization.json.JsonPrimitive(outputCount),
            "quality" to kotlinx.serialization.json.JsonPrimitive(quality)
        )
        if (!imageUri.isNullOrBlank()) {
            map["referenceImage"] = kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(imageUri)))
            )
        }
        val result = runwayFetch("/recipes/marketing_stock_image", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)
        
        val urls = task["output"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            ?: listOfNotNull(task["artifactUrl"]?.jsonPrimitive?.content)
            
        if (urls.isEmpty()) throw Exception("No output URLs found")
        
        return urls.map { downloadToLocal(it, "png") }
    }

    suspend fun productAd(
        productImages: List<String>,
        styleImages: List<String>,
        productInfo: String,
        userConcept: String,
        ratio: String,
        duration: Int,
        audio: Boolean,
        nodeQuality: String = "720p"
    ): String {
        val finalRatio = if (ratio == "720:1280" || ratio == "1088:1920") {
            if (nodeQuality == "1080p") "1088:1920" else "720:1280"
        } else if (ratio == "1280:720" || ratio == "1920:1088") {
            if (nodeQuality == "1080p") "1920:1088" else "1280:720"
        } else {
            if (nodeQuality == "1080p") "1080:1080" else "960:960"
        }
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "version" to kotlinx.serialization.json.JsonPrimitive("2026-06"),
            "ratio" to kotlinx.serialization.json.JsonPrimitive(finalRatio),
            "duration" to kotlinx.serialization.json.JsonPrimitive(duration),
            "audio" to kotlinx.serialization.json.JsonPrimitive(audio)
        )
        if (productInfo.isNotBlank()) map["productInfo"] = kotlinx.serialization.json.JsonPrimitive(productInfo)
        if (userConcept.isNotBlank()) map["userConcept"] = kotlinx.serialization.json.JsonPrimitive(userConcept)
        
        map["productImages"] = kotlinx.serialization.json.JsonArray(
            productImages.map { 
                kotlinx.serialization.json.JsonObject(mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(it))))
            }
        )
        if (styleImages.isNotEmpty()) {
            map["styleImages"] = kotlinx.serialization.json.JsonArray(
                styleImages.map { 
                    kotlinx.serialization.json.JsonObject(mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(it))))
                }
            )
        }
        
        val result = runwayFetch("/recipes/product_ad", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)
        val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: task["artifactUrl"]?.jsonPrimitive?.content
            ?: throw Exception("No output URL found")
            
        return downloadToLocal(remoteUrl, "mp4")
    }

    suspend fun productCampaignImage(
        imageUri: String,
        prompt: String
    ): List<String> {
        val map = mapOf(
            "version" to kotlinx.serialization.json.JsonPrimitive("2026-06"),
            "image" to kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(imageUri)))
            ),
            "prompt" to kotlinx.serialization.json.JsonPrimitive(prompt)
        )
        val result = runwayFetch("/recipes/product_campaign_image", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)
        
        val urls = task["output"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            ?: listOfNotNull(task["artifactUrl"]?.jsonPrimitive?.content)
            
        if (urls.isEmpty()) throw Exception("No output URLs found")
        
        return urls.map { downloadToLocal(it, "png") }
    }

    suspend fun productSwap(
        referenceVideoUri: String,
        originalProductUri: String,
        newProductUris: List<String>,
        duration: Int,
        resolution: String,
        audio: Boolean
    ): String {
        val map = mapOf(
            "version" to kotlinx.serialization.json.JsonPrimitive("2026-06"),
            "referenceVideo" to kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(referenceVideoUri)))
            ),
            "originalProductImage" to kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(originalProductUri)))
            ),
            "newProductImages" to kotlinx.serialization.json.JsonArray(
                newProductUris.map { 
                    kotlinx.serialization.json.JsonObject(mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(it))))
                }
            ),
            "duration" to kotlinx.serialization.json.JsonPrimitive(duration),
            "resolution" to kotlinx.serialization.json.JsonPrimitive(resolution),
            "audio" to kotlinx.serialization.json.JsonPrimitive(audio)
        )
        val result = runwayFetch("/recipes/product_swap", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)
        val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: task["artifactUrl"]?.jsonPrimitive?.content
            ?: throw Exception("No output URL found")
            
        return downloadToLocal(remoteUrl, "mp4")
    }

    suspend fun multiShotVideo(
        prompt: String,
        firstFrameUri: String?,
        ratio: String,
        duration: Int,
        audio: Boolean,
        nodeQuality: String = "720p"
    ): String {
        val finalRatio = if (ratio == "720:1280" || ratio == "1088:1920") {
            if (nodeQuality == "1080p") "1088:1920" else "720:1280"
        } else if (ratio == "1280:720" || ratio == "1920:1088") {
            if (nodeQuality == "1080p") "1920:1088" else "1280:720"
        } else {
            if (nodeQuality == "1080p") "1080:1080" else "960:960"
        }
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "version" to kotlinx.serialization.json.JsonPrimitive("2026-06"),
            "mode" to kotlinx.serialization.json.JsonPrimitive("auto"),
            "prompt" to kotlinx.serialization.json.JsonPrimitive(prompt),
            "ratio" to kotlinx.serialization.json.JsonPrimitive(finalRatio),
            "duration" to kotlinx.serialization.json.JsonPrimitive(duration),
            "audio" to kotlinx.serialization.json.JsonPrimitive(audio)
        )
        if (firstFrameUri != null) {
            map["firstFrame"] = kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(firstFrameUri)))
            )
        }
        
        val result = runwayFetch("/recipes/multi_shot_video", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)
        val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: task["artifactUrl"]?.jsonPrimitive?.content
            ?: throw Exception("No output URL found")
            
        return downloadToLocal(remoteUrl, "mp4")
    }

    suspend fun productUgc(
        characterImageUri: String,
        productImageUri: String,
        productInfo: String,
        userConcept: String,
        duration: Int,
        ratio: String,
        audio: Boolean,
        nodeQuality: String = "720p"
    ): String {
        val finalRatio = if (ratio == "720:1280" || ratio == "1088:1920") {
            if (nodeQuality == "1080p") "1088:1920" else "720:1280"
        } else if (ratio == "1280:720" || ratio == "1920:1088") {
            if (nodeQuality == "1080p") "1920:1088" else "1280:720"
        } else {
            if (nodeQuality == "1080p") "1080:1080" else "960:960"
        }
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "version" to kotlinx.serialization.json.JsonPrimitive("2026-06"),
            "characterImage" to kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(characterImageUri)))
            ),
            "productImage" to kotlinx.serialization.json.JsonObject(
                mapOf("uri" to kotlinx.serialization.json.JsonPrimitive(toApiUri(productImageUri)))
            ),
            "duration" to kotlinx.serialization.json.JsonPrimitive(duration),
            "ratio" to kotlinx.serialization.json.JsonPrimitive(finalRatio),
            "audio" to kotlinx.serialization.json.JsonPrimitive(audio)
        )
        if (productInfo.isNotBlank()) map["productInfo"] = kotlinx.serialization.json.JsonPrimitive(productInfo)
        if (userConcept.isNotBlank()) map["userConcept"] = kotlinx.serialization.json.JsonPrimitive(userConcept)
        
        val result = runwayFetch("/recipes/product_ugc", JsonObject(map))
        val taskId = result["id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
        val task = pollTask(taskId)
        val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: task["artifactUrl"]?.jsonPrimitive?.content
            ?: throw Exception("No output URL found")
            
        return downloadToLocal(remoteUrl, "mp4")
    }

    private suspend fun downloadToLocal(url: String, extension: String): String {
        onRemoteUrlGenerated?.invoke(url)
        for (attempt in 1..4) {
            try {
                val response = httpClient.get(url)
                if (response.status == HttpStatusCode.OK) {
                    val bytes = response.readBytes()
                    return app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, extension)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                println("[RunwayService] downloadToLocal attempt $attempt failed: ${e.message}")
                delay(1500L * attempt)
            }
        }
        // If local temp saving failed during backgrounding, return the remote URL so execution succeeds
        return url
    }
}

class VideoModelConfig(
    val key: String,
    val label: String,
    val durations: List<Int>,
    val durationRange: Boolean = false,
    val ratios: Map<String, String>,
    val supportsAudio: Boolean = false,
    val maxImages: Int = 0,
    val supportsTextOnly: Boolean = true
)

object RunwayModels {
    val voicePresets = listOf("Maya", "Nova", "Atlas", "Echo", "Sage", "Coral", "Vale", "Storm")
    
    val videoModels = listOf(
        VideoModelConfig("veo3.1_fast", "Veo 3.1 Fast", listOf(4, 6, 8), false, mapOf("16:9" to "1280:720", "9:16" to "720:1280"), true, 2, true),
        VideoModelConfig("veo3.1", "Veo 3.1", listOf(4, 6, 8), false, mapOf("16:9" to "1280:720", "9:16" to "720:1280"), true, 2, true),
        VideoModelConfig("seedance2", "Seedance 2", listOf(4, 15), true, mapOf("16:9" to "1920:1080", "9:16" to "1080:1920"), true, 2, true)
    )

    fun getVideoModel(key: String): VideoModelConfig {
        return videoModels.firstOrNull { it.key == key } ?: videoModels.first()
    }

    fun getModelDurations(model: VideoModelConfig): List<Int> {
        if (model.durationRange) {
            val min = model.durations.first()
            val max = model.durations.last()
            return listOf(2, 3, 4, 5, 6, 8, 10, 12, 15).filter { it in min..max }
        }
        return model.durations
    }

    fun clampDuration(model: VideoModelConfig, duration: Int): Int {
        if (model.durationRange) {
            return duration.coerceIn(model.durations.first(), model.durations.last())
        }
        return model.durations.minByOrNull { kotlin.math.abs(it - duration) } ?: model.durations.first()
    }
}
