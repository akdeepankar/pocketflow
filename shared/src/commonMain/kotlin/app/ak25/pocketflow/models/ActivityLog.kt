package app.ak25.pocketflow.models

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityType {
    CREATE_WORKFLOW, DELETE_WORKFLOW, LEAVE_WORKFLOW, ADD_NODE, RUN_NODE, SPEND_CREDITS, ADD_CREDITS, RENAME_WORKFLOW
}

@Serializable
data class ActivityLog(
    val id: String,
    val timestamp: Long,
    val type: ActivityType,
    val title: String,
    val details: String,
    val creditsSpent: Int? = null,
    val workflowName: String? = null
)
