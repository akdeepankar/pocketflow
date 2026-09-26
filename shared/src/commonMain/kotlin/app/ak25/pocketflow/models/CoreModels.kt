package app.ak25.pocketflow.models

import kotlinx.serialization.Serializable

@Serializable
enum class PortDataType {
    IMAGE, VIDEO, AUDIO, TEXT, MODEL3D
}

@Serializable
enum class NodeStatus {
    IDLE, PENDING, RUNNING, COMPLETED, FAILED
}

@Serializable
data class NodeNote(
    val id: String,
    val authorUserId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long = 0L
)

@Serializable
data class WorkflowNode(
    val id: String,
    val type: NodeType,
    var positionX: Float = 0f,
    var positionY: Float = 0f,
    var params: MutableMap<String, String> = mutableMapOf(),
    var status: NodeStatus = NodeStatus.IDLE,
    var errorMessage: String? = null,
    var outputUrl: String? = null,       // Final CDN/signed URL from Runway
    var outputLocalPath: String? = null, // Local cached copy
    var jobId: String? = null,           // Runway task ID (persisted for resume)
    var notes: MutableList<NodeNote> = mutableListOf()  // collaboration notes from members
)

@Serializable
data class WorkflowEdge(
    val id: String,
    val sourceNodeId: String,
    val sourcePortId: String,
    val targetNodeId: String,
    val targetPortId: String
)

@Serializable
data class Workflow(
    val id: String,
    var name: String,
    val nodes: MutableList<WorkflowNode> = mutableListOf(),
    val edges: MutableList<WorkflowEdge> = mutableListOf(),
    var lastEdited: Long = 0L,
    val createdAtDate: String = "",
    val isPinned: Boolean = false,
    val cardColorHex: String = "#FFFFFF",
    val joinCode: String = "",
    val ownerUserId: String = "",
    val membersJson: String = "[]"
)
