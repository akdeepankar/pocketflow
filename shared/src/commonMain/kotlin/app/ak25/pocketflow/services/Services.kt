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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive



class StorageService {
    fun saveWorkflow() {}
    fun loadWorkflows() {}
}

class ExecutionEngine(private val controller: WorkflowController) {
    val engineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private val runwayService = app.ak25.pocketflow.services.RunwayService()

    fun isRunning(nodeId: String): Boolean = activeRuns.contains(nodeId)

    private suspend fun resolveNodeOutput(sourceNode: app.ak25.pocketflow.models.WorkflowNode): String? {
        val localExists = if (!sourceNode.outputLocalPath.isNullOrEmpty()) {
            try {
                val firstPath = sourceNode.outputLocalPath!!.split(",").firstOrNull()?.trim() ?: sourceNode.outputLocalPath!!
                app.ak25.pocketflow.storage.LocalStorage.readMediaFromTemp(firstPath) != null
            } catch (e: Exception) { false }
        } else false

        return if (localExists && !sourceNode.outputLocalPath.isNullOrEmpty()) {
            sourceNode.outputLocalPath
        } else {
            sourceNode.outputUrl ?: sourceNode.outputLocalPath ?: (if (sourceNode.type == NodeType.UPLOADED_IMAGE) sourceNode.params["imageUri"] else null)
        }
    }

    private var cancelled = false
    private val activeRuns = mutableSetOf<String>()

    fun cancel() {
        cancelled = true
    }

    suspend fun runNode(
        nodeId: String,
        currentStep: Int = 1,
        totalSteps: Int = 1,
        completedSteps: Int = 0,
        activityKey: String? = null,
        stepNodeTypes: List<String> = emptyList()
    ): Boolean {
        if (activeRuns.contains(nodeId)) return false
        activeRuns.add(nodeId)
        if (activityKey == null) {
            app.ak25.pocketflow.storage.LocalStorage.beginBackgroundTask("pocketflow_node_$nodeId")
        }
        
        val workflow = controller.currentWorkflow.value?.takeIf { it.nodes.any { n -> n.id == nodeId } }
            ?: controller.workflows.value.find { wf -> wf.nodes.any { it.id == nodeId } }
            ?: run {
                if (activityKey == null) {
                    app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
                }
                activeRuns.remove(nodeId)
                return false
            }
        val node = workflow.nodes.find { it.id == nodeId } ?: run {
            if (activityKey == null) {
                app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
            }
            activeRuns.remove(nodeId)
            return false
        }

        // Notes node is a standalone note/todo — nothing to execute.
        if (node.type == NodeType.NOTE) {
            controller.updateNodeStatus(nodeId, NodeStatus.COMPLETED)
            if (activityKey == null) {
                app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
            }
            activeRuns.remove(nodeId)
            return true
        }
        
        controller.updateNodeStatus(nodeId, NodeStatus.RUNNING)

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
        val displayTitle = node.params["title"] ?: friendlyName
        val effectiveActivityKey = activityKey ?: nodeId
        val stepTypesList = if (stepNodeTypes.isNotEmpty()) stepNodeTypes else listOf(nodeTypeName)
        val stepTypesJson = Json.encodeToString<List<String>>(stepTypesList)

        // Start or update iOS Live Activity on Lock Screen & Dynamic Island
        try {
            if (activityKey == null) {
                app.ak25.pocketflow.storage.LocalStorage.startLiveActivity(
                    workflowId = workflow.id,
                    workflowName = workflow.name,
                    nodeId = nodeId,
                    nodeTitle = displayTitle,
                    nodeType = nodeTypeName,
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                    stepNodeTypesJson = stepTypesJson
                )
            } else {
                app.ak25.pocketflow.storage.LocalStorage.updateLiveActivity(
                    nodeId = effectiveActivityKey,
                    status = if (totalSteps > 1) "Step $currentStep/$totalSteps: Generating $displayTitle..." else "Generating $displayTitle...",
                    progress = -1.0,
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                    completedSteps = completedSteps,
                    nodeTitle = displayTitle,
                    nodeType = nodeTypeName
                )
            }
        } catch (e: Exception) {
            // ignore
        }

        // Track the latest task ID so we can persist it
        var latestJobId: String? = node.jobId ?: node.params["jobId"]

        // Setup Live Activity metadata for server-side OneSignal push integration
        runwayService.activeLiveActivityMetadata = kotlinx.serialization.json.buildJsonObject {
            put("activityId", kotlinx.serialization.json.JsonPrimitive(effectiveActivityKey))
            put("workflowId", kotlinx.serialization.json.JsonPrimitive(workflow.id))
            put("workflowName", kotlinx.serialization.json.JsonPrimitive(workflow.name))
            put("nodeTitle", kotlinx.serialization.json.JsonPrimitive(displayTitle))
            put("nodeType", kotlinx.serialization.json.JsonPrimitive(node.type.name))
            put("currentStep", kotlinx.serialization.json.JsonPrimitive(currentStep))
            put("totalSteps", kotlinx.serialization.json.JsonPrimitive(totalSteps))
            put("stepNodeTypes", kotlinx.serialization.json.JsonArray(stepNodeTypes.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }

        // Setup callbacks — persist jobId to Supabase immediately on task creation
        runwayService.onTaskIdGenerated = { taskId ->
            latestJobId = taskId
            controller.updateNodeParams(nodeId, "jobId", taskId)
            // Also persist via updateNodeStatus so it reaches Supabase
            controller.updateNodeStatus(nodeId, NodeStatus.RUNNING, jobId = taskId)
        }
        val generatedRemoteUrls = mutableListOf<String>()
        runwayService.onRemoteUrlGenerated = { url ->
            generatedRemoteUrls.add(url)
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
                        runwayService.triggerLiveActivityPolling(currentJobId)
                        runwayService.pollAndDownloadTask(currentJobId, node.type)
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
                        // End Live Activity on failure (only for single-node runs)
                        try {
                            if (activityKey == null) {
                                app.ak25.pocketflow.storage.LocalStorage.endLiveActivity(
                                    nodeId = effectiveActivityKey,
                                    isSuccess = false,
                                    message = if (totalSteps > 1) "Step $currentStep ($displayTitle) failed" else "Generation failed",
                                    completedSteps = completedSteps,
                                    totalSteps = totalSteps
                                )
                            }
                            // For workflow runs, runWorkflow handles the endLiveActivity
                        } catch (ex: Exception) {}

                        // Show Local Notification on failure
                        try {
                            app.ak25.pocketflow.storage.LocalStorage.showLocalNotification(
                                title = "Generation Failed (${workflow.name})",
                                body = "$displayTitle generation failed!",
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

            // Update / End Live Activity on success
            try {
                if (activityKey == null) {
                    app.ak25.pocketflow.storage.LocalStorage.endLiveActivity(
                        nodeId = nodeId,
                        isSuccess = true,
                        message = "$displayTitle Completed! ✓",
                        completedSteps = 1,
                        totalSteps = 1
                    )
                } else {
                    app.ak25.pocketflow.storage.LocalStorage.updateLiveActivity(
                        nodeId = effectiveActivityKey,
                        status = "Step $currentStep of $totalSteps completed ✓",
                        progress = (completedSteps + 1).toDouble() / totalSteps,
                        isFinished = false,
                        isSuccess = true,
                        currentStep = currentStep,
                        totalSteps = totalSteps,
                        completedSteps = completedSteps + 1,
                        nodeTitle = displayTitle,
                        nodeType = nodeTypeName
                    )
                }
            } catch (e: Exception) {}
            
            // Show Local Notification instantly while in background or foreground
            try {
                val notificationText = "$displayTitle has been completed"
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
            try {
                if (activityKey == null) {
                    app.ak25.pocketflow.storage.LocalStorage.endLiveActivity(
                        nodeId = effectiveActivityKey,
                        isSuccess = false,
                        message = "Cancelled",
                        completedSteps = completedSteps,
                        totalSteps = totalSteps
                    )
                }
                // For workflow runs, runWorkflow handles the endLiveActivity
            } catch (ex: Exception) {}
            throw e
        } catch (e: Exception) {
            println("[ExecutionEngine] Node $nodeId unexpected error: ${e.message}")
            try {
                if (activityKey == null) {
                    app.ak25.pocketflow.storage.LocalStorage.endLiveActivity(
                        nodeId = effectiveActivityKey,
                        isSuccess = false,
                        message = "Failed",
                        completedSteps = completedSteps,
                        totalSteps = totalSteps
                    )
                }
                // For workflow runs, runWorkflow handles the endLiveActivity
            } catch (ex: Exception) {}
            false
        } finally {
            if (activityKey == null) {
                app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
            }
            runwayService.activeLiveActivityMetadata = null
            runwayService.onTaskIdGenerated = null
            runwayService.onRemoteUrlGenerated = null
            activeRuns.remove(nodeId)
        }
    }



    private suspend fun executeNode(nodeId: String): String {
        val workflow = controller.currentWorkflow.value?.takeIf { it.nodes.any { n -> n.id == nodeId } }
            ?: controller.workflows.value.find { wf -> wf.nodes.any { it.id == nodeId } }
            ?: throw Exception("No workflow found containing node $nodeId")
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
                throw Exception("3D Model Generation is not supported.")
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

    suspend fun runWorkflow(onlyEmpty: Boolean = false): Boolean {
        cancelled = false
        val workflow = controller.currentWorkflow.value ?: return false
        app.ak25.pocketflow.storage.LocalStorage.beginBackgroundTask("pocketflow_workflow_${workflow.id}")
        
        return try {
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

            val nodesMap = workflow.nodes.associateBy { it.id }
            val nodesToRun = mutableListOf<String>()
            val freshlyRunSet = mutableSetOf<String>()

            if (onlyEmpty) {
                for (nodeId in sorted) {
                    val node = nodesMap[nodeId] ?: continue
                    if (node.type == NodeType.TEXT_PROMPT) {
                        nodesToRun.add(nodeId)
                        continue
                    }
                    val isEmptyOutput = node.outputUrl.isNullOrEmpty() || node.status != NodeStatus.COMPLETED
                    val hasUpstreamReRun = workflow.edges.any { it.targetNodeId == nodeId && it.sourceNodeId in freshlyRunSet }
                    if (isEmptyOutput || hasUpstreamReRun) {
                        nodesToRun.add(nodeId)
                        freshlyRunSet.add(nodeId)
                    }
                }
            } else {
                nodesToRun.addAll(sorted)
            }

            if (nodesToRun.isEmpty()) {
                return true
            }

            // Mark all runnable nodes as PENDING upfront
            for (nodeId in nodesToRun) {
                val node = nodesMap[nodeId]
                if (node?.type != NodeType.TEXT_PROMPT && node?.type != NodeType.NOTE) {
                    controller.updateNodeStatus(nodeId, NodeStatus.PENDING)
                }
            }

            val genNodes = nodesToRun.mapNotNull { nodesMap[it] }.filter { it.type != NodeType.TEXT_PROMPT && it.type != NodeType.NOTE }
            val totalSteps = genNodes.size
            val stepTypes = genNodes.map { it.type.name }
            val stepTypesJson = Json.encodeToString<List<String>>(stepTypes)

            // Start workflow-wide Live Activity
            if (totalSteps > 0) {
                val firstNode = genNodes.first()
                val firstFriendly = firstNode.params["title"] ?: when (firstNode.type.name) {
                    "IMAGE_GENERATION" -> "Image"
                    "VIDEO_GENERATION" -> "Video"
                    "TEXT_TO_SPEECH" -> "Audio"
                    "AUDIO_GENERATION" -> "Audio"
                    "TEXT_GENERATION" -> "Text"
                    "MODEL3D_GENERATION" -> "3D Model"
                    else -> firstNode.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
                }
                try {
                    app.ak25.pocketflow.storage.LocalStorage.startLiveActivity(
                        workflowId = workflow.id,
                        workflowName = workflow.name,
                        nodeId = workflow.id,
                        nodeTitle = firstFriendly,
                        nodeType = firstNode.type.name,
                        currentStep = 1,
                        totalSteps = totalSteps,
                        stepNodeTypesJson = stepTypesJson
                    )
                } catch (e: Exception) {}
            }

            var completedCount = 0
            for (nodeId in nodesToRun) {
                if (cancelled) {
                    if (totalSteps > 0) {
                        try {
                            app.ak25.pocketflow.storage.LocalStorage.endLiveActivity(
                                nodeId = workflow.id,
                                isSuccess = false,
                                message = "Cancelled",
                                completedSteps = completedCount,
                                totalSteps = totalSteps
                            )
                        } catch (e: Exception) {}
                    }
                    return false
                }
                val node = nodesMap[nodeId]
                if (node?.type == NodeType.TEXT_PROMPT || node?.type == NodeType.NOTE) {
                    continue
                }
                val stepIndex = genNodes.indexOfFirst { it.id == nodeId } + 1
                val currentStepNum = if (stepIndex > 0) stepIndex else (completedCount + 1)
                
                // Rotate background task for this specific step so iOS watchdog gives a fresh timer
                app.ak25.pocketflow.storage.LocalStorage.beginBackgroundTask("pocketflow_step_${currentStepNum}_$nodeId")

                val success = runNode(
                    nodeId = nodeId,
                    currentStep = currentStepNum,
                    totalSteps = totalSteps,
                    completedSteps = completedCount,
                    activityKey = workflow.id,
                    stepNodeTypes = stepTypes
                )
                if (!success) {
                    if (totalSteps > 0) {
                        try {
                            app.ak25.pocketflow.storage.LocalStorage.endLiveActivity(
                                nodeId = workflow.id,
                                isSuccess = false,
                                message = "Generation failed",
                                completedSteps = completedCount,
                                totalSteps = totalSteps
                            )
                        } catch (e: Exception) {}
                    }
                    return false
                }
                completedCount++
            }
            
            // Finalize Live Activity for the overall workflow
            if (totalSteps > 0) {
                try {
                    app.ak25.pocketflow.storage.LocalStorage.endLiveActivity(
                        nodeId = workflow.id,
                        isSuccess = true,
                        message = "All $totalSteps nodes completed! ✓",
                        completedSteps = totalSteps,
                        totalSteps = totalSteps
                    )
                } catch (e: Exception) {}
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

            true
        } finally {
            app.ak25.pocketflow.storage.LocalStorage.endBackgroundTask()
        }
    }
}

