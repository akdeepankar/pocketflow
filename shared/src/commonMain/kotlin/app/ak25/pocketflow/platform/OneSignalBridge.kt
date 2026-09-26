package app.ak25.pocketflow.platform

interface OneSignalBridge {
    fun addTrigger(key: String, value: String)
    fun addTag(key: String, value: String)
}

object OneSignalBridgeHolder {
    private val pendingTriggers = mutableMapOf<String, String>()
    private val pendingTags = mutableMapOf<String, String>()

    var current: OneSignalBridge? = null
        set(value) {
            field = value
            if (value != null) {
                println("[OneSignal-Bridge] 🔌 Native OneSignalBridge attached! Flushing ${pendingTriggers.size} triggers and ${pendingTags.size} tags...")
                pendingTriggers.forEach { (k, v) ->
                    value.addTrigger(k, v)
                }
                pendingTags.forEach { (k, v) ->
                    value.addTag(k, v)
                }
            }
        }

    fun addTrigger(key: String, value: String) {
        pendingTriggers[key] = value
        val bridge = current
        if (bridge != null) {
            bridge.addTrigger(key, value)
        } else {
            println("[OneSignal-Bridge] ⏳ Bridge not ready yet, cached trigger '$key' = '$value'")
        }
    }

    fun addTag(key: String, value: String) {
        pendingTags[key] = value
        val bridge = current
        if (bridge != null) {
            bridge.addTag(key, value)
        } else {
            println("[OneSignal-Bridge] ⏳ Bridge not ready yet, cached tag '$key' = '$value'")
        }
    }
}
