package app.ak25.pocketflow.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import app.ak25.pocketflow.services.SupabaseRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

expect fun ByteArray.toImageBitmap(): ImageBitmap

object ImageCache {
    private val cache = mutableMapOf<String, ByteArray>()
    
    fun put(key: String, bytes: ByteArray) { 
        cache[key] = bytes
        try {
            val path = app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(bytes, "png")
            app.ak25.pocketflow.storage.LocalStorage.saveString("cache_file_$key", path)
        } catch (e: Exception) {
            // ignore
        }
    }
    
    fun get(key: String): ByteArray? {
        val cached = cache[key]
        if (cached != null) return cached
        
        return try {
            val path = app.ak25.pocketflow.storage.LocalStorage.loadString("cache_file_$key") ?: return null
            val bytes = app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(path)
            if (bytes != null) {
                cache[key] = bytes
            }
            bytes
        } catch (e: Exception) {
            null
        }
    }
}

object MediaLoader {
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun loadBytes(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        if (uri.isBlank()) return@withContext null
        
        val cacheKey = if (uri.startsWith("cache://")) uri.removePrefix("cache://") else uri
        val cached = ImageCache.get(cacheKey)
        if (cached != null) return@withContext cached

        // 1. Try local file reading if it's not a remote URL
        if (!uri.startsWith("http://") && !uri.startsWith("https://") && !uri.startsWith("data:")) {
            try {
                val localBytes = app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(uri)
                if (localBytes != null) {
                    ImageCache.put(cacheKey, localBytes)
                    return@withContext localBytes
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 2. Base64 data URL
        if (uri.startsWith("data:")) {
            try {
                val base64Data = uri.substringAfter("base64,")
                val bytes = Base64.Default.decode(base64Data)
                ImageCache.put(cacheKey, bytes)
                return@withContext bytes
            } catch (e: Exception) {
                // ignore
            }
        }

        // 3. Appwrite / Supabase Storage URL
        if (uri.startsWith("http") && SupabaseRepository.isAppwriteStorageUrl(uri)) {
            try {
                val bytes = SupabaseRepository.downloadStorageFile(uri)
                if (bytes != null) {
                    ImageCache.put(cacheKey, bytes)
                    return@withContext bytes
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 4. Remote HTTP/HTTPS URL
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            try {
                val response = httpClient.get(uri)
                if (response.status.isSuccess()) {
                    val bytes = response.readBytes()
                    ImageCache.put(cacheKey, bytes)
                    return@withContext bytes
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        null
    }
}

