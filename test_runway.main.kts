@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("io.ktor:ktor-client-core-jvm:2.3.11")
@file:DependsOn("io.ktor:ktor-client-cio-jvm:2.3.11")
@file:DependsOn("io.ktor:ktor-client-content-negotiation-jvm:2.3.11")
@file:DependsOn("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.11")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

val runwayApiKey = "key_a9062eb463a5666718c3ce446a89bebc30e843c0dcd1e5a513511eb4c6ceb044e39755ab7a8f1082c5f1cd9391abf8ce8cce2632b4b455b7617631336d3570bd"
val runwayBaseUrl = "https://api.dev.runwayml.com/v1"

val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

runBlocking {
    try {
        val map = mutableMapOf<String, JsonElement>(
            "model" to JsonPrimitive("veo3.1_fast"),
            "duration" to JsonPrimitive(5),
            "ratio" to JsonPrimitive("1280:720"),
            "promptText" to JsonPrimitive("A beautiful sunset over the ocean, waves crashing")
        )
        
        val response = httpClient.post("$runwayBaseUrl/text_to_video") {
            header("Authorization", "Bearer $runwayApiKey")
            header("X-Runway-Version", "2024-11-06")
            contentType(ContentType.Application.Json)
            setBody(JsonObject(map))
        }
        
        println("Status: ${response.status}")
        println("Response: ${response.bodyAsText()}")
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        httpClient.close()
    }
}
