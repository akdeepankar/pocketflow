package app.ak25.pocketflow.platform

interface OneSignalBridge {
    fun addTrigger(key: String, value: String)
    fun addTag(key: String, value: String)
}

object OneSignalBridgeHolder {
    var current: OneSignalBridge? = null
}
