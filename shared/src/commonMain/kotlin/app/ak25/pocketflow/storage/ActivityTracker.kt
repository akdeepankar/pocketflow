package app.ak25.pocketflow.storage

import app.ak25.pocketflow.models.ActivityLog
import app.ak25.pocketflow.models.ActivityType
import app.ak25.pocketflow.utils.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ActivityTracker {
    private val _logs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val logs: StateFlow<List<ActivityLog>> = _logs.asStateFlow()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        try {
            val jsonStr = LocalStorage.loadString("activity_logs")
            if (jsonStr != null) {
                val list = Json.decodeFromString<List<ActivityLog>>(jsonStr)
                _logs.value = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLogs(list: List<ActivityLog>) {
        try {
            val jsonStr = Json.encodeToString(list)
            LocalStorage.saveString("activity_logs", jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun log(
        type: ActivityType,
        title: String,
        details: String,
        creditsSpent: Int? = null,
        workflowName: String? = null
    ) {
        val entry = ActivityLog(
            id = IdGenerator.generate(),
            timestamp = app.ak25.pocketflow.utils.getCurrentTimeMillis(),
            type = type,
            title = title,
            details = details,
            creditsSpent = creditsSpent,
            workflowName = workflowName
        )
        val updated = listOf(entry) + _logs.value
        _logs.value = updated
        saveLogs(updated)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        saveLogs(emptyList())
    }
}
