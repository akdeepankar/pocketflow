package app.ak25.pocketflow.platform

interface LiveActivityBridge {
    fun startLiveActivity(
        workflowId: String,
        workflowName: String,
        nodeId: String,
        nodeTitle: String,
        nodeType: String
    )
    fun updateLiveActivity(
        nodeId: String,
        status: String,
        progress: Double,
        message: String,
        isFinished: Boolean,
        isSuccess: Boolean
    )
    fun endLiveActivity(nodeId: String, isSuccess: Boolean, message: String)
}

object LiveActivityBridgeHolder {
    var current: LiveActivityBridge? = null
}
