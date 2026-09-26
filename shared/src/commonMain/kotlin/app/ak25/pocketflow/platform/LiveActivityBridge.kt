package app.ak25.pocketflow.platform

interface LiveActivityBridge {
    fun startLiveActivity(
        workflowId: String,
        workflowName: String,
        nodeId: String,
        nodeTitle: String,
        nodeType: String,
        currentStep: Int,
        totalSteps: Int,
        stepNodeTypesJson: String
    )

    fun updateLiveActivity(
        nodeId: String,
        status: String,
        progress: Double,
        message: String,
        isFinished: Boolean,
        isSuccess: Boolean,
        currentStep: Int,
        totalSteps: Int,
        completedSteps: Int,
        nodeTitle: String,
        nodeType: String
    )

    fun endLiveActivity(
        nodeId: String,
        isSuccess: Boolean,
        message: String,
        completedSteps: Int,
        totalSteps: Int
    )
}

object LiveActivityBridgeHolder {
    var current: LiveActivityBridge? = null
}
