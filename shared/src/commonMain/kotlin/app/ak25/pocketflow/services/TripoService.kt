package app.ak25.pocketflow.services

import app.ak25.pocketflow.ui.utils.ImageCache
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class TripoService {
    private val tripoApiKey = Env.TRIPO_API_KEY
    private val tripoBaseUrl = "https://api.tripo3d.ai/v2/openapi"

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend fun downloadFile(url: String): ByteArray {
        // Appwrite Storage files are private (need JWT/session) — fetch with auth.
        if (SupabaseRepository.isAppwriteStorageUrl(url)) {
            return SupabaseRepository.downloadStorageFile(url)
                ?: throw Exception("Failed to download Appwrite storage file: $url")
        }
        val response = httpClient.get(url)
        return response.readBytes()
    }

    private suspend fun downloadFileAndSaveLocal(url: String, extension: String): String {
        val bytes = downloadFile(url)
        return app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, extension)
    }

    private suspend fun uploadFile(imageUri: String): String {
        val bytes: ByteArray
        val filename: String

        if (imageUri.startsWith("http://") || imageUri.startsWith("https://")) {
            bytes = downloadFile(imageUri)
            filename = "downloaded_image.png"
        } else if (imageUri.startsWith("cache://")) {
            val id = imageUri.removePrefix("cache://")
            bytes = ImageCache.get(id) ?: throw Exception("Image not found in cache: $id")
            filename = "cached_image.png"
        } else {
            val localBytes = try {
                app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(imageUri)
            } catch (e: Exception) {
                null
            }
            if (localBytes != null) {
                bytes = localBytes
                filename = "local_image.png"
            } else {
                throw Exception("Unsupported URI format for Tripo upload: $imageUri")
            }
        }

        val response = httpClient.submitFormWithBinaryData(
            url = "$tripoBaseUrl/upload",
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                })
            }
        ) {
            header("Authorization", "Bearer $tripoApiKey")
        }

        val respStr = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw Exception("Tripo upload error: ${response.status.value} - $respStr")
        }

        val data = Json.parseToJsonElement(respStr) as JsonObject
        val code = data["code"]?.jsonPrimitive?.content?.toIntOrNull()
        if (code != 0) {
            throw Exception("Tripo API Error: ${data["message"]?.jsonPrimitive?.content}")
        }

        val dataObj = data["data"] as? JsonObject ?: throw Exception("No data object in upload response")
        return dataObj["image_token"]?.jsonPrimitive?.content ?: throw Exception("No image_token in upload response")
    }

    var onTaskIdGenerated: ((String) -> Unit)? = null
    var onRemoteUrlGenerated: ((String) -> Unit)? = null

    suspend fun imageTo3d(imageUri: String, existingTaskId: String? = null): String {
        val taskId = existingTaskId ?: run {
            val fileToken = uploadFile(imageUri)

            val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                "type" to kotlinx.serialization.json.JsonPrimitive("image_to_model"),
                "file" to kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "type" to kotlinx.serialization.json.JsonPrimitive("png"),
                        "file_token" to kotlinx.serialization.json.JsonPrimitive(fileToken)
                    )
                )
            )

            val response = httpClient.post("$tripoBaseUrl/task") {
                header("Authorization", "Bearer $tripoApiKey")
                contentType(ContentType.Application.Json)
                setBody(JsonObject(map))
            }

            val respStr = response.bodyAsText()
            if (response.status != HttpStatusCode.OK) {
                throw Exception("Tripo task error: ${response.status.value} - $respStr")
            }

            val data = Json.parseToJsonElement(respStr) as JsonObject
            val code = data["code"]?.jsonPrimitive?.content?.toIntOrNull()
            if (code != 0) {
                throw Exception("Tripo API Error: ${data["message"]?.jsonPrimitive?.content}")
            }

            val dataObj = data["data"] as? JsonObject ?: throw Exception("No data object in task response")
            val id = dataObj["task_id"]?.jsonPrimitive?.content ?: throw Exception("No task ID")
            onTaskIdGenerated?.invoke(id)
            id
        }


        // Poll
        var consecutiveErrors = 0
        var lastRenewTime = app.ak25.pocketflow.utils.getCurrentTimeMillis()
        for (i in 0 until 180) {
            delay(5000)
            val now = app.ak25.pocketflow.utils.getCurrentTimeMillis()
            if (now - lastRenewTime >= 20_000L) {
                lastRenewTime = now
                app.ak25.pocketflow.storage.LocalStorage.beginBackgroundTask("pocketflow_tripo_poll_$taskId")
            }
            try {
                val pollResp = httpClient.get("$tripoBaseUrl/task/$taskId") {
                    header("Authorization", "Bearer $tripoApiKey")
                }

                if (pollResp.status != HttpStatusCode.OK) {
                    consecutiveErrors++
                    if (consecutiveErrors > 25) {
                        throw Exception("Poll error: ${pollResp.status.value} - ${pollResp.bodyAsText()}")
                    }
                    continue
                }

                val pollData = Json.parseToJsonElement(pollResp.bodyAsText()) as JsonObject
                val pollCode = pollData["code"]?.jsonPrimitive?.content?.toIntOrNull()
                if (pollCode != 0) {
                    throw Exception("Tripo API Error: ${pollData["message"]?.jsonPrimitive?.content}")
                }

                val taskInfo = pollData["data"] as? JsonObject ?: throw Exception("No data object in poll response")
                val status = taskInfo["status"]?.jsonPrimitive?.content

                consecutiveErrors = 0

                if (status == "success") {
                    val output = taskInfo["output"] as? JsonObject ?: throw Exception("Task succeeded but output is null")
                    val modelUrl = output["model"]?.jsonPrimitive?.content
                        ?: output["pbr_model"]?.jsonPrimitive?.content
                        ?: output["base_model"]?.jsonPrimitive?.content
                        ?: output["model_file"]?.jsonPrimitive?.content
                    
                    if (modelUrl == null) {
                        throw Exception("Model URL not found in output.")
                    }
                    onRemoteUrlGenerated?.invoke(modelUrl)
                    return downloadFileAndSaveLocal(modelUrl, "glb")
                } else if (status == "failed") {
                    throw Exception("Task failed: ${taskInfo["error"]?.jsonPrimitive?.content}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = e.message ?: ""
                if (msg.startsWith("Task failed:") || msg.startsWith("Tripo API Error:")) {
                    throw e
                }
                consecutiveErrors++
                println("[TripoService] Poll network exception (attempt $consecutiveErrors/25): $msg")
                if (consecutiveErrors > 25) {
                    throw e
                }
            }
        }

        throw Exception("Task timed out after 15 minutes")
    }
}
