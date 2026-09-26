package app.ak25.pocketflow.services

import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.NodeStatus
import app.ak25.pocketflow.models.NodeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive



class StorageService {
    fun saveWorkflow() {}
    fun loadWorkflows() {}
}

class ExecutionEngine(private val controller: WorkflowController) {
    val engineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private val runwayService = app.ak25.pocketflow.services.RunwayService()
    private val tripoService = app.ak25.pocketflow.services.TripoService()

    fun isRunning(nodeId: String): Boolean = activeRuns.contains(nodeId)

    private suspend fun resolveNodeOutput(sourceNode: app.ak25.pocketflow.models.WorkflowNode): String? {
        var uri = sourceNode.outputUrl ?: sourceNode.outputLocalPath ?: (if (sourceNode.type == NodeType.UPLOADED_IMAGE) sourceNode.params["imageUri"] else null)
        val currentJobId = sourceNode.jobId ?: sourceNode.params["jobId"]
        if (uri != null && !uri.startsWith("http://") && !uri.startsWith("https://") && !uri.startsWith("data:") && !currentJobId.isNullOrEmpty()) {
            try {
                val task = runwayService.getTask(currentJobId)
                val remoteUrl = task["output"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    ?: task["artifactUrl"]?.jsonPrimitive?.content
                if (!remoteUrl.isNullOrEmpty()) {
                    uri = remoteUrl
                    controller.updateNodeStatus(sourceNode.id, sourceNode.status, outputUrl = remoteUrl)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return uri
    }

    private var cancelled = false
    private val activeRuns = mutableSetOf<String>()

    fun cancel() {
        cancelled = true
    }

    suspend fun runNode(nodeId: String): Boolean {
        if (activeRuns.contains(nodeId)) return false
        activeRuns.add(nodeId)
        app.ak25.pocketflow.storage.LocalStorage.beginBackgroundTask("pocketflow_node_$nodeId")
        
        val workflow = controller.workflows.value.find { wf -> wf.nodes.any { it.id == nodeId } } ?: run {
            app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
            activeRuns.remove(nodeId)
            return false
        }
        val node = workflow.nodes.find { it.id == nodeId } ?: run {
            app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
            activeRuns.remove(nodeId)
            return false
        }

        // Notes node is a standalone note/todo — nothing to execute.
        if (node.type == NodeType.NOTE) {
            controller.updateNodeStatus(nodeId, NodeStatus.COMPLETED)
            app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
            activeRuns.remove(nodeId)
            return true
        }
        
        controller.updateNodeStatus(nodeId, NodeStatus.RUNNING)

        // Track the latest task ID so we can persist it
        var latestJobId: String? = node.jobId ?: node.params["jobId"]

        // Setup callbacks — persist jobId to Appwrite immediately on task creation
        runwayService.onTaskIdGenerated = { taskId ->
            latestJobId = taskId
            controller.updateNodeParams(nodeId, "jobId", taskId)
            // Also persist via updateNodeStatus so it reaches Appwrite
            controller.updateNodeStatus(nodeId, NodeStatus.RUNNING, jobId = taskId)
        }
        val generatedRemoteUrls = mutableListOf<String>()
        runwayService.onRemoteUrlGenerated = { url ->
            generatedRemoteUrls.add(url)
        }
        tripoService.onRemoteUrlGenerated = { url ->
            generatedRemoteUrls.add(url)
        }
        tripoService.onTaskIdGenerated = { taskId ->
            latestJobId = taskId
            controller.updateNodeParams(nodeId, "jobId", taskId)
            controller.updateNodeStatus(nodeId, NodeStatus.RUNNING, jobId = taskId)
        }

        val initialJobId = node.jobId ?: node.params["jobId"]
        var currentJobId = initialJobId
        var outputResult: String? = null

        return try {
            var loopAttempt = 0
            while (loopAttempt < 5 && outputResult == null) {
                loopAttempt++
                try {
                    outputResult = if (!currentJobId.isNullOrEmpty()) {
                        if (node.type == NodeType.MODEL3D_GENERATION) {
                            tripoService.imageTo3d(imageUri = "", existingTaskId = currentJobId)
                        } else {
                            runwayService.pollAndDownloadTask(currentJobId, node.type)
                        }
                    } else {
                        executeNode(nodeId)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && cancelled) {
                        println("[ExecutionEngine] Node $nodeId execution explicitly cancelled by user.")
                        throw e
                    }
                    val msg = e.message ?: ""
                    val isExplicitFailure = msg.startsWith("Task failed:", ignoreCase = true) ||
                        msg.startsWith("Tripo API Error:", ignoreCase = true) ||
                        msg.contains("timed out after", ignoreCase = true)

                    if (isExplicitFailure || (latestJobId.isNullOrEmpty() && loopAttempt >= 2)) {
                        // Mark the node FAILED only on explicit failure
                        controller.updateNodeStatus(
                            nodeId,
                            NodeStatus.FAILED,
                            message = e.message ?: "Generation failed",
                            jobId = latestJobId
                        )
                        println("[ExecutionEngine] Node $nodeId failed: ${e.message}")
                        // Show Local Notification on failure
                        try {
                            val nodeTypeName = node.type.name
                            val friendlyName = when (nodeTypeName) {
                                "IMAGE_GENERATION" -> "Image"
                                "VIDEO_GENERATION" -> "Video"
                                "TEXT_TO_SPEECH" -> "Audio"
                                "AUDIO_GENERATION" -> "Audio"
                                "TEXT_GENERATION" -> "Text"
                                "MODEL3D_GENERATION" -> "3D Model"
                                else -> nodeTypeName.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
                            }
                            app.ak25.pocketflow.storage.LocalStorage.showLocalNotification(
                                title = "Generation Failed (${workflow.name})",
                                body = "$friendlyName generation failed!",
                                workflowId = workflow.id,
                                nodeId = nodeId
                            )
                        } catch (ex: Exception) {
                            // ignore
                        }
                        return false
                    }

                    // If task was submitted and running on cloud, retry polling in background
                    if (!latestJobId.isNullOrEmpty()) {
                        currentJobId = latestJobId
                        println("[ExecutionEngine] Background poll transient glitch ($msg), retrying in 3s (attempt $loopAttempt/5)...")
                        delay(3000)
                    } else {
                        println("[ExecutionEngine] Node $nodeId initial request error ($msg), retrying in 2s...")
                        delay(2000)
                    }
                }
            }

            val output = outputResult ?: return false

            val remoteUrlString = if (generatedRemoteUrls.isNotEmpty()) generatedRemoteUrls.joinToString(",") else null
            val finalOutputUrl = remoteUrlString ?: (if (output.startsWith("http://") || output.startsWith("https://") || output.startsWith("data:")) output else node.outputUrl)
            // Persist jobId + outputUrl + COMPLETED in one atomic update
            controller.updateNodeStatus(
                nodeId,
                NodeStatus.COMPLETED,
                outputUrl = finalOutputUrl,
                localPath = output,
                jobId = latestJobId
            )
            
            // Show Local Notification instantly while in background or foreground
            try {
                val nodeTypeName = node.type.name
                val friendlyName = when (nodeTypeName) {
                    "IMAGE_GENERATION" -> "Image"
                    "VIDEO_GENERATION" -> "Video"
                    "TEXT_TO_SPEECH" -> "Audio"
                    "AUDIO_GENERATION" -> "Audio"
                    "TEXT_GENERATION" -> "Text"
                    "MODEL3D_GENERATION" -> "3D Model"
                    else -> nodeTypeName.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
                }
                val notificationText = if (nodeTypeName == "IMAGE_GENERATION" || nodeTypeName == "VIDEO_GENERATION" || nodeTypeName == "TEXT_TO_SPEECH" || nodeTypeName == "AUDIO_GENERATION" || nodeTypeName == "TEXT_GENERATION" || nodeTypeName == "MODEL3D_GENERATION") {
                    "$friendlyName has been completed"
                } else {
                    "Node '$friendlyName' has finished generation successfully!"
                }
                app.ak25.pocketflow.storage.LocalStorage.showLocalNotification(
                    title = "Generation Completed (${workflow.name})",
                    body = notificationText,
                    workflowId = workflow.id,
                    nodeId = nodeId
                )
            } catch (e: Exception) {
                // ignore
            }

            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            println("[ExecutionEngine] Node $nodeId execution cancelled.")
            throw e
        } catch (e: Exception) {
            println("[ExecutionEngine] Node $nodeId unexpected error: ${e.message}")
            false
        } finally {
            app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
            runwayService.onTaskIdGenerated = null
            runwayService.onRemoteUrlGenerated = null
            tripoService.onTaskIdGenerated = null
            tripoService.onRemoteUrlGenerated = null
            activeRuns.remove(nodeId)
        }
    }



    private suspend fun executeNode(nodeId: String): String {
        val workflow = controller.workflows.value.find { wf -> wf.nodes.any { it.id == nodeId } } ?: throw Exception("No workflow found containing node $nodeId")
        val node = workflow.nodes.find { it.id == nodeId } ?: throw Exception("Node not found")
        val edges = workflow.edges

        val inputs = mutableMapOf<String, String>()
        for (edge in edges.filter { it.targetNodeId == node.id }) {
            val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
            if (sourceNode != null) {
                val valToUse = resolveNodeOutput(sourceNode)
                if (!valToUse.isNullOrEmpty()) {
                    inputs[edge.targetPortId] = valToUse
                } else if (sourceNode.type == NodeType.TEXT_PROMPT) {
                    inputs[edge.targetPortId] = sourceNode.params["text"] ?: ""
                }
            }
        }

        return when (node.type) {
            NodeType.IMAGE_GENERATION -> {
                var finalPrompt = node.params["prompt"] ?: ""
                val referenceUris = mutableListOf<String>()
                
                for (edge in edges.filter { it.targetNodeId == node.id }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        if (edge.targetPortId == "reference") {
                            val v = resolveNodeOutput(sourceNode)
                            if (!v.isNullOrEmpty()) referenceUris.add(v)
                        } else if (edge.targetPortId == "prompt" && sourceNode.type == NodeType.TEXT_PROMPT) {
                            val v = sourceNode.params["text"]
                            if (!v.isNullOrEmpty()) finalPrompt = v
                        }
                    }
                }
                
                if (finalPrompt.isEmpty() && referenceUris.isEmpty()) {
                    throw Exception("Please provide a prompt or reference image")
                }
                
                runwayService.textToImage(
                    prompt = finalPrompt,
                    model = node.params["model"] ?: "gemini_image3_pro",
                    aspectRatio = node.params["aspectRatio"],
                    referenceImageUris = if (referenceUris.isNotEmpty()) referenceUris.take(2) else null,
                    resolution = node.params["resolution"],
                    quality = node.params["quality"]
                )
            }
            NodeType.UPLOADED_IMAGE -> {
                val uri = node.params["imageUri"]
                if (uri.isNullOrEmpty()) throw Exception("No image selected")
                uri
            }
            NodeType.IMAGE_TO_VIDEO -> {
                val imageUris = mutableListOf<String>()
                for (edge in edges.filter { it.targetNodeId == node.id && it.targetPortId == "image" }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        val v = resolveNodeOutput(sourceNode)
                        if (!v.isNullOrEmpty()) imageUris.add(v)
                    }
                }
                
                val modelStr = node.params["model"] ?: "veo3.1_fast"
                val firstFrame = imageUris.firstOrNull()
                val lastFrame = if (imageUris.size > 1) imageUris[1] else null
                
                val finalPrompt = node.params["prompt"] ?: ""
                if (firstFrame.isNullOrEmpty() && finalPrompt.isEmpty()) {
                    throw Exception("Please provide an image or a text prompt")
                }
                
                runwayService.imageToVideo(
                    model = modelStr,
                    firstFrameUri = firstFrame,
                    lastFrameUri = lastFrame,
                    prompt = finalPrompt,
                    duration = node.params["duration"]?.toIntOrNull() ?: 5,
                    aspectRatio = node.params["aspectRatio"],
                    audio = (node.params["audio"] ?: "false").toBoolean()
                )
            }
            NodeType.TEXT_TO_SPEECH -> {
                val promptEdge = edges.find { it.targetNodeId == node.id && it.targetPortId == "prompt" }
                val promptSourceText = promptEdge?.let { edge ->
                    workflow.nodes
                        .find { it.id == edge.sourceNodeId && it.type == NodeType.TEXT_PROMPT }
                        ?.params?.get("text")
                }
                val text = (promptSourceText ?: node.params["text"]).orEmpty()
                if (text.isEmpty()) throw Exception("Please enter text to generate speech")
                
                runwayService.textToSpeech(
                    text = text,
                    voicePreset = node.params["voicePreset"] ?: "Maya"
                )
            }
            NodeType.MODEL3D_GENERATION -> {
                var imageUri: String? = null
                for (edge in edges.filter { it.targetNodeId == node.id && it.targetPortId == "image" }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        imageUri = resolveNodeOutput(sourceNode)
                        break
                    }
                }
                if (imageUri.isNullOrEmpty()) throw Exception("No input image provided for 3D model generation")
                tripoService.imageTo3d(imageUri)
            }
            NodeType.AD_LOCALIZATION -> {
                var imageUri: String? = null
                for (edge in edges.filter { it.targetNodeId == node.id && it.targetPortId == "image" }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        imageUri = resolveNodeOutput(sourceNode)
                        break
                    }
                }
                if (imageUri.isNullOrEmpty()) throw Exception("No input image provided for ad localization")
                
                val targetLang = node.params["targetLanguage"] ?: "es"
                runwayService.adLocalization(imageUri, targetLang)
            }
            NodeType.MARKETING_STOCK_IMAGE -> {
                var imageUri: String? = null
                for (edge in edges.filter { it.targetNodeId == node.id && it.targetPortId == "image" }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        imageUri = resolveNodeOutput(sourceNode)
                        break
                    }
                }
                val prompt = node.params["prompt"] ?: ""
                val count = node.params["outputCount"]?.toIntOrNull() ?: 4
                val quality = node.params["quality"] ?: "high"
                
                if (prompt.isBlank()) throw Exception("Marketing stock image requires a prompt")
                
                val urls = runwayService.marketingStockImage(prompt, imageUri, count, quality)
                urls.joinToString(",")
            }
            NodeType.PRODUCT_AD -> {
                val productImages = mutableListOf<String>()
                val styleImages = mutableListOf<String>()
                
                for (edge in edges.filter { it.targetNodeId == node.id }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        val uri = resolveNodeOutput(sourceNode)
                        if (uri != null) {
                            if (edge.targetPortId == "productImage") {
                                productImages.add(uri)
                            } else if (edge.targetPortId == "styleImage") {
                                styleImages.add(uri)
                            }
                        }
                    }
                }
                
                if (productImages.isEmpty()) throw Exception("Product Ad requires at least one product image")
                
                val productInfo = node.params["productInfo"] ?: ""
                val userConcept = node.params["userConcept"] ?: ""
                val ratio = node.params["ratio"] ?: "1280:720"
                val duration = node.params["duration"]?.toIntOrNull() ?: 10
                val audio = node.params["audio"]?.toBoolean() ?: false
                val nodeQuality = node.params["resolution"] ?: "720p"
                
                runwayService.productAd(productImages, styleImages, productInfo, userConcept, ratio, duration, audio, nodeQuality)
            }
            NodeType.PRODUCT_CAMPAIGN -> {
                var imageUri: String? = null
                for (edge in edges.filter { it.targetNodeId == node.id && it.targetPortId == "image" }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        imageUri = resolveNodeOutput(sourceNode)
                        break
                    }
                }
                
                val prompt = node.params["prompt"] ?: ""
                if (prompt.isBlank()) throw Exception("Product Campaign requires a prompt")
                if (imageUri.isNullOrBlank()) throw Exception("Product Campaign requires a linked product image")
                
                val urls = runwayService.productCampaignImage(imageUri, prompt)
                urls.joinToString(",")
            }
            NodeType.PRODUCT_SWAP -> {
                var referenceVideo: String? = null
                var originalProduct: String? = null
                val newProducts = mutableListOf<String>()
                
                for (edge in edges.filter { it.targetNodeId == node.id }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        val uri = resolveNodeOutput(sourceNode)
                        if (uri != null) {
                            when (edge.targetPortId) {
                                "referenceVideo" -> referenceVideo = uri
                                "originalProduct" -> originalProduct = uri
                                "newProduct" -> newProducts.add(uri)
                            }
                        }
                    }
                }
                
                if (referenceVideo == null) throw Exception("Product Swap requires a reference video")
                if (originalProduct == null) throw Exception("Product Swap requires the original product image")
                if (newProducts.isEmpty()) throw Exception("Product Swap requires at least one new product image")
                
                println("DEBUG: Product Swap inputs: referenceVideo=$referenceVideo, originalProduct=$originalProduct, newProducts=$newProducts")
                val duration = node.params["duration"]?.toIntOrNull() ?: 10
                val resolution = node.params["resolution"] ?: "720p"
                val audio = node.params["audio"]?.toBoolean() ?: true
                
                runwayService.productSwap(referenceVideo, originalProduct, newProducts, duration, resolution, audio)
            }
            NodeType.MULTI_SHOT_VIDEO -> {
                var firstFrame: String? = null
                
                for (edge in edges.filter { it.targetNodeId == node.id && it.targetPortId == "firstFrame" }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        firstFrame = resolveNodeOutput(sourceNode)
                        break
                    }
                }
                
                val prompt = node.params["prompt"] ?: ""
                if (prompt.isBlank()) throw Exception("MultiShot Video requires a story prompt")
                
                val ratio = node.params["ratio"] ?: "1280:720"
                val duration = node.params["duration"]?.toIntOrNull() ?: 10
                val audio = node.params["audio"]?.toBoolean() ?: true
                val nodeQuality = node.params["resolution"] ?: "720p"
                
                runwayService.multiShotVideo(prompt, firstFrame, ratio, duration, audio, nodeQuality)
            }
            NodeType.PRODUCT_UGC -> {
                var characterImage: String? = null
                var productImage: String? = null
                
                for (edge in edges.filter { it.targetNodeId == node.id }) {
                    val sourceNode = workflow.nodes.find { it.id == edge.sourceNodeId }
                    if (sourceNode != null) {
                        val uri = resolveNodeOutput(sourceNode)
                        if (uri != null) {
                            if (edge.targetPortId == "characterImage") characterImage = uri
                            if (edge.targetPortId == "productImage") productImage = uri
                        }
                    }
                }
                
                if (characterImage == null) throw Exception("Product UGC requires a character image")
                if (productImage == null) throw Exception("Product UGC requires a product image")
                
                val productInfo = node.params["productInfo"] ?: ""
                val userConcept = node.params["userConcept"] ?: ""
                val duration = node.params["duration"]?.toIntOrNull() ?: 15
                val ratio = node.params["ratio"] ?: "720:1280"
                val audio = node.params["audio"]?.toBoolean() ?: true
                val nodeQuality = node.params["resolution"] ?: "720p"
                
                runwayService.productUgc(characterImage, productImage, productInfo, userConcept, duration, ratio, audio, nodeQuality)
            }

            NodeType.NOTE -> {
                // A note/todo node has no media output — mark as executed with nothing produced.
                ""
            }

            NodeType.TEXT_PROMPT -> {
                node.params["text"] ?: ""
            }
        }
    }

    suspend fun runWorkflow(): Boolean {
        cancelled = false
        val workflow = controller.currentWorkflow.value ?: return false
        
        val inDegree = mutableMapOf<String, Int>()
        val adjList = mutableMapOf<String, MutableList<String>>()
        
        workflow.nodes.forEach { inDegree[it.id] = 0 }
        workflow.edges.forEach {
            inDegree[it.targetNodeId] = (inDegree[it.targetNodeId] ?: 0) + 1
            adjList.getOrPut(it.sourceNodeId) { mutableListOf() }.add(it.targetNodeId)
        }
        
        val queue = mutableListOf<String>()
        inDegree.forEach { (id, deg) -> if (deg == 0) queue.add(id) }
        
        val sorted = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            sorted.add(current)
            
            adjList[current]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 1) - 1
                if (inDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }
        
        if (sorted.size != workflow.nodes.size) {
            throw Exception("Cycle detected in workflow graph")
        }

        for (nodeId in sorted) {
            if (cancelled) return false
            controller.updateNodeStatus(nodeId, NodeStatus.PENDING)
        }

        for (nodeId in sorted) {
            if (cancelled) return false
            val success = runNode(nodeId)
            if (!success) return false
        }
        
        // Show Local Notification instantly
        try {
            app.ak25.pocketflow.storage.LocalStorage.showLocalNotification(
                title = "Workflow Completed",
                body = "Your workflow '${workflow.name}' has finished generation successfully!",
                workflowId = workflow.id
            )
        } catch (e: Exception) {
            // ignore
        }

        return true
    }
}

