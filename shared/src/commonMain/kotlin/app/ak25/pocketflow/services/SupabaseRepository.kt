package app.ak25.pocketflow.services

import app.ak25.pocketflow.models.Workflow
import app.ak25.pocketflow.models.WorkflowNode
import app.ak25.pocketflow.models.WorkflowEdge
import app.ak25.pocketflow.storage.LocalStorage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.functions
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.ak25.pocketflow.utils.getCurrentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

@Serializable
data class SupabaseWorkflow(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String,
    @SerialName("last_edited") val lastEdited: Long,
    @SerialName("created_at_date") val createdAtDate: String,
    @SerialName("card_color_hex") val cardColorHex: String,
    @SerialName("nodes_json") val nodesJson: String,
    @SerialName("edges_json") val edgesJson: String,
    @SerialName("join_code") val joinCode: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("members_json") val membersJson: String
) {
    fun toCore(): Workflow {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val nodes = try {
            json.decodeFromString<List<WorkflowNode>>(this.nodesJson)
        } catch (e: Exception) { emptyList() }
        
        val edges = try {
            json.decodeFromString<List<WorkflowEdge>>(this.edgesJson)
        } catch (e: Exception) { emptyList() }
        
        val localPinned = LocalStorage.loadString("pinned_workflow_${this.id}") == "true"
        
        return Workflow(
            id = this.id,
            name = this.name,
            nodes = nodes.toMutableList(),
            edges = edges.toMutableList(),
            lastEdited = this.lastEdited,
            createdAtDate = this.createdAtDate,
            isPinned = localPinned,
            cardColorHex = this.cardColorHex,
            joinCode = this.joinCode,
            ownerUserId = this.ownerUserId,
            membersJson = this.membersJson
        )
    }
}

@Serializable
data class SupabasePresence(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    @SerialName("workflow_id") val workflowId: String,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("action") val action: String = "VIEWING",
    @SerialName("cursor_x") val cursorX: Float = 0f,
    @SerialName("cursor_y") val cursorY: Float = 0f,
    @SerialName("node_id") val nodeId: String = ""
)

object SupabaseRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Client for downloading public files
    internal val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private fun jwt(): String = LocalStorage.loadString("supabase_jwt") ?: LocalStorage.loadString("appwrite_jwt") ?: ""
    private fun userId(): String = LocalStorage.loadString("supabase_user_id") ?: LocalStorage.loadString("appwrite_user_id") ?: ""
    private fun sessionId(): String = LocalStorage.loadString("supabase_session_id") ?: LocalStorage.loadString("appwrite_session_id") ?: ""

    internal fun jwtExpired(token: String): Boolean {
        if (token.isEmpty()) return true
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return true
            val payload = parts[1]
            val padding = (4 - payload.length % 4) % 4
            val decoded = kotlin.io.encoding.Base64.UrlSafe.decode(payload + "=".repeat(padding))
            val exp = Json.parseToJsonElement(decoded.decodeToString()).jsonObject["exp"]?.jsonPrimitive?.longOrNull ?: 0L
            exp == 0L || exp * 1000L < getCurrentTimeMillis() + 60_000L
        } catch (e: Exception) {
            true
        }
    }

    fun isAuthError(e: Throwable): Boolean {
        val msg = e.message ?: ""
        return msg.contains("JWT expired", ignoreCase = true) ||
               msg.contains("token is expired", ignoreCase = true) ||
               msg.contains("invalid JWT", ignoreCase = true) ||
               msg.contains("invalid claim: exp", ignoreCase = true) ||
               msg.contains("Invalid Refresh Token", ignoreCase = true) ||
               msg.contains("refresh_token_not_found", ignoreCase = true) ||
               msg.contains("401", ignoreCase = true) ||
               msg.contains("Unauthorized", ignoreCase = true) ||
               msg.contains("User from sub claim in JWT does not exist", ignoreCase = true)
    }

    fun handleAuthErrorIfPresent(e: Throwable) {
        if (isAuthError(e)) {
            println("[Supabase] Auth error detected: ${e.message}. Triggering signOut...")
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                app.ak25.pocketflow.ui.auth.SharedAuthViewModel.signOut()
            }
        }
    }

    internal fun refreshJwtBlocking(): String {
        return try {
            kotlinx.coroutines.runBlocking {
                supabaseClient.auth.refreshCurrentSession()
                val session = supabaseClient.auth.currentSessionOrNull()
                val token = session?.accessToken ?: ""
                if (token.isNotEmpty()) {
                    LocalStorage.saveString("supabase_jwt", token)
                    LocalStorage.saveString("appwrite_jwt", token)
                }
                token
            }
        } catch (e: Exception) {
            println("[Supabase] JWT refresh exception: ${e.message}")
            handleAuthErrorIfPresent(e)
            ""
        }
    }

    suspend fun fetchWorkflows(): List<Workflow>? = withContext(Dispatchers.IO) {
        try {
            val uid = userId().ifEmpty {
                println("[Supabase] fetchWorkflows skipped — no userId")
                return@withContext null
            }
            println("[Supabase] fetchWorkflows for userId=$uid")
            
            val response = supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                .select {
                    filter {
                        or {
                            eq("user_id", uid)
                            eq("owner_user_id", uid)
                            like("members_json", "%$uid%")
                        }
                    }
                }
            val list = response.decodeList<SupabaseWorkflow>()
            println("[Supabase] fetchWorkflows got ${list.size} documents")
            list.map { it.toCore() }
        } catch (e: Exception) {
            println("[Supabase] fetchWorkflows exception: ${e.message}")
            handleAuthErrorIfPresent(e)
            null
        }
    }

    suspend fun fetchWorkflowById(workflowId: String): Workflow? = withContext(Dispatchers.IO) {
        try {
            println("[Supabase] fetchWorkflowById: $workflowId")
            val response = supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                .select {
                    filter {
                        eq("id", workflowId)
                    }
                }
            val doc = response.decodeSingleOrNull<SupabaseWorkflow>()
            doc?.toCore()
        } catch (e: Exception) {
            println("[Supabase] fetchWorkflowById exception: ${e.message}")
            handleAuthErrorIfPresent(e)
            null
        }
    }

    suspend fun upsertWorkflow(workflow: Workflow) = withContext(Dispatchers.IO) {
        try {
            val uid = userId()
            if (uid.isEmpty()) {
                println("[Supabase] upsertWorkflow skipped — no user ID (not logged in)")
                return@withContext
            }

            val nodesJson = json.encodeToString(workflow.nodes.toList())
            val edgesJson = json.encodeToString(workflow.edges.toList())

            // Check if document already exists to merge share fields
            val existing = fetchWorkflowById(workflow.id)
            val effectiveJoinCode = workflow.joinCode.ifEmpty { existing?.joinCode ?: "" }
            val effectiveMembers = workflow.membersJson.takeIf { it.isNotBlank() && it != "[]" } ?: existing?.membersJson ?: "[]"
            val effectiveOwnerUserId = workflow.ownerUserId.ifEmpty { existing?.ownerUserId ?: uid }

            if (existing != null) {
                // Update existing workflow in Supabase (works for both owner and shared members)
                supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                    .update({
                        set("name", workflow.name)
                        set("last_edited", workflow.lastEdited)
                        set("nodes_json", nodesJson)
                        set("edges_json", edgesJson)
                        set("card_color_hex", workflow.cardColorHex)
                        if (effectiveJoinCode.isNotEmpty()) {
                            set("join_code", effectiveJoinCode)
                        }
                        if (effectiveMembers.isNotEmpty() && effectiveMembers != "[]") {
                            set("members_json", effectiveMembers)
                        }
                    }) {
                        filter {
                            eq("id", workflow.id)
                        }
                    }
                println("[Supabase] ✅ Updated workflow ${workflow.id} ('${workflow.name}') in cloud (by user $uid)")
            } else {
                // Create new workflow in Supabase
                val dbWf = SupabaseWorkflow(
                    id = workflow.id,
                    userId = uid,
                    name = workflow.name,
                    lastEdited = workflow.lastEdited,
                    createdAtDate = workflow.createdAtDate,
                    cardColorHex = workflow.cardColorHex,
                    nodesJson = nodesJson,
                    edgesJson = edgesJson,
                    joinCode = effectiveJoinCode,
                    ownerUserId = uid,
                    membersJson = effectiveMembers
                )
                supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS).upsert(dbWf)
                println("[Supabase] ✅ Created new workflow ${workflow.id} ('${workflow.name}') in cloud")
            }
        } catch (e: Exception) {
            println("[Supabase] ❌ upsertWorkflow exception: ${e.message}")
            handleAuthErrorIfPresent(e)
            e.printStackTrace()
        }
    }

    suspend fun deleteWorkflow(workflowId: String) = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_WORKFLOWS)
                .delete {
                    filter {
                        eq("id", workflowId)
                    }
                }
            println("[Supabase] ✅ Deleted workflow $workflowId")
        } catch (e: Exception) {
            println("[Supabase] deleteWorkflow exception: ${e.message}")
            handleAuthErrorIfPresent(e)
        }
    }

    suspend fun updatePresence(workflowId: String) = withContext(Dispatchers.IO) {
        try {
            val uid = userId().ifEmpty { return@withContext }
            val userName = LocalStorage.loadString("user_name") ?: ""
            val now = getCurrentTimeMillis()
            val docId = "presence_${uid}_${workflowId}"

            val pres = SupabasePresence(
                id = docId,
                userId = uid,
                userName = userName,
                workflowId = workflowId,
                updatedAt = now
            )
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE).upsert(pres)
        } catch (e: Exception) {
            println("[Supabase] updatePresence exception: ${e.message}")
        }
    }

    suspend fun fetchPresence(workflowId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val thirtySecondsAgo = getCurrentTimeMillis() - 30_000
            val list = supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE)
                .select {
                    filter {
                        eq("workflowId", workflowId)
                        gt("updatedAt", thirtySecondsAgo)
                    }
                }.decodeList<SupabasePresence>()
            list.map { it.userName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearPresence(workflowId: String) = withContext(Dispatchers.IO) {
        try {
            val uid = userId().ifEmpty { return@withContext }
            val docId = "presence_${uid}_${workflowId}"
            supabaseClient.postgrest.from(SupabaseConfig.TABLE_PRESENCE)
                .delete {
                    filter {
                        eq("id", docId)
                    }
                }
        } catch (e: Exception) {
            println("[Supabase] clearPresence exception: ${e.message}")
        }
    }

    suspend fun invokeRunwayFunction(
        endpoint: String,
        payload: JsonObject
    ): String? = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("endpoint", endpoint)
                put("payload", payload)
            }
            val response = supabaseClient.functions.invoke("runway-generate", body)
            val responseText = response.bodyAsText()
            val parsed = json.parseToJsonElement(responseText).jsonObject
            if (parsed["success"]?.jsonPrimitive?.booleanOrNull == true) {
                val jobId = parsed["jobId"]?.jsonPrimitive?.contentOrNull
                println("[Supabase] ✅ Function returned jobId: $jobId")
                jobId
            } else {
                println("[Supabase] Function returned error: ${parsed["error"]}")
                null
            }
        } catch (e: Exception) {
            println("[Supabase] invokeRunwayFunction exception: ${e.message}")
            handleAuthErrorIfPresent(e)
            null
        }
    }

    /**
     * Proxy a Runway GET /tasks/{taskId} call through the `runway-poll` Supabase edge function,
     * so the client doesn't need a valid local Runway API key.
     * Returns the raw Runway task JSON (with status, output, etc.) or null on failure.
     */
    suspend fun pollRunwayTask(taskId: String): kotlinx.serialization.json.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("taskId", taskId)
            }
            val response = supabaseClient.functions.invoke("runway-poll", body)
            val responseText = response.bodyAsText()
            val parsed = json.parseToJsonElement(responseText).jsonObject
            if (parsed["success"]?.jsonPrimitive?.booleanOrNull == true) {
                parsed["data"]?.jsonObject
            } else {
                println("[Supabase] pollRunwayTask error: ${parsed["error"]}")
                null
            }
        } catch (e: Exception) {
            println("[Supabase] pollRunwayTask exception: ${e.message}")
            null
        }
    }

    suspend fun pollLiveActivityJob(
        jobId: String,
        provider: String = "runway",
        liveActivity: JsonObject? = null,
        recipientUserId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("jobId", jobId)
                put("provider", provider)
                if (liveActivity != null) {
                    put("liveActivity", liveActivity)
                }
                val uid = recipientUserId ?: userId()
                if (uid.isNotBlank()) {
                    put("recipientUserId", uid)
                }
            }
            println("[Supabase] 📡 Invoking poll-job edge function for jobId=$jobId, activityId=${liveActivity?.get("activityId")}")
            val response = supabaseClient.functions.invoke("poll-job", body)
            println("[Supabase] 📡 poll-job edge function response status: ${response.status.value}")
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[Supabase] ❌ pollLiveActivityJob exception: ${e.message}")
            false
        }
    }

    suspend fun uploadImageFile(nodeId: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val fileId = "img_${nodeId.replace("-", "_")}.jpg"
            val bucket = supabaseClient.storage.from(SupabaseConfig.BUCKET_IMAGES)
            
            try {
                bucket.delete(fileId)
            } catch (e: Exception) {}

            bucket.upload(fileId, bytes, upsert = true)
            val url = bucket.publicUrl(fileId)
            println("[Supabase] ✅ Uploaded image for node $nodeId → $url")
            url
        } catch (e: Exception) {
            println("[Supabase] uploadImageFile exception: ${e.message}")
            null
        }
    }

    suspend fun deleteImageFile(nodeId: String) = withContext(Dispatchers.IO) {
        try {
            val fileId = "img_${nodeId.replace("-", "_")}.jpg"
            supabaseClient.storage.from(SupabaseConfig.BUCKET_IMAGES).delete(fileId)
            println("[Supabase] 🗑 Deleted image file for node $nodeId")
        } catch (e: Exception) {
            println("[Supabase] deleteImageFile exception: ${e.message}")
        }
    }

    fun isSupabaseStorageUrl(url: String): Boolean = "/storage/v1/object/public/" in url || "/storage/v1/object/" in url

    suspend fun downloadStorageFile(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.get(url)
            if (!resp.status.isSuccess()) {
                println("[Supabase] downloadStorageFile ${resp.status.value}: ${resp.bodyAsText()}")
                null
            } else {
                val bytes = resp.readBytes()
                println("[Supabase] ✅ Downloaded storage file (${bytes.size} bytes)")
                bytes
            }
        } catch (e: Exception) {
            println("[Supabase] downloadStorageFile exception: ${e.message}")
            null
        }
    }
}
