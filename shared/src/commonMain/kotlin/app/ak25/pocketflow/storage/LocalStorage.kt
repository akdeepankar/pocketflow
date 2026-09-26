package app.ak25.pocketflow.storage

expect object LocalStorage {
    fun saveString(key: String, value: String)
    fun loadString(key: String): String?
    fun saveMediaToTemp(bytes: ByteArray, extension: String): String
    fun readMediaFromTemp(path: String): ByteArray?
    fun exportMediaToGallery(path: String): Boolean
    fun shareMedia(path: String): Boolean
    fun resolveLocalPath(path: String): String
    fun showLocalNotification(title: String, body: String, workflowId: String? = null, nodeId: String? = null)
    fun startLiveActivity(
        workflowId: String,
        workflowName: String,
        nodeId: String,
        nodeTitle: String,
        nodeType: String,
        currentStep: Int = 1,
        totalSteps: Int = 1,
        stepNodeTypesJson: String = "[]"
    )
    fun updateLiveActivity(
        nodeId: String,
        status: String,
        progress: Double = -1.0,
        message: String = "",
        isFinished: Boolean = false,
        isSuccess: Boolean = false,
        currentStep: Int = 1,
        totalSteps: Int = 1,
        completedSteps: Int = 0,
        nodeTitle: String = "",
        nodeType: String = ""
    )
    fun endLiveActivity(
        nodeId: String,
        isSuccess: Boolean = true,
        message: String = "",
        completedSteps: Int = 1,
        totalSteps: Int = 1
    )
    fun beginBackgroundTask(name: String = "generation")
    fun endBackgroundTask()
}
