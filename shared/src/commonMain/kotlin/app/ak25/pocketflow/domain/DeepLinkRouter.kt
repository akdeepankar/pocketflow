package app.ak25.pocketflow.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.native.concurrent.ThreadLocal

data class DeepLinkTarget(
    val workflowId: String,
    val nodeId: String? = null,
    val type: String? = null
)

@ThreadLocal
object DeepLinkRouter {
    private val _pendingDeepLink = MutableStateFlow<DeepLinkTarget?>(null)
    val pendingDeepLink: StateFlow<DeepLinkTarget?> = _pendingDeepLink.asStateFlow()

    fun onNotificationClicked(workflowId: String, nodeId: String? = null, type: String? = null) {
        println("[DeepLinkRouter] 🔗 Notification clicked: workflowId=$workflowId, nodeId=$nodeId, type=$type")
        val cleanWorkflowId = workflowId.trim()
        if (cleanWorkflowId.isNotEmpty()) {
            val cleanNodeId = nodeId?.trim()?.takeIf { it.isNotEmpty() }
            val cleanType = type?.trim()?.takeIf { it.isNotEmpty() }
            _pendingDeepLink.value = DeepLinkTarget(cleanWorkflowId, cleanNodeId, cleanType)
        }
    }

    fun clearPendingDeepLink() {
        _pendingDeepLink.value = null
    }
}
