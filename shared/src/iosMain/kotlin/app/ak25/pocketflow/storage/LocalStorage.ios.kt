package app.ak25.pocketflow.storage

import platform.Foundation.*
import platform.posix.memcpy
import kotlinx.cinterop.*
import platform.UIKit.*


actual object LocalStorage {
    actual fun saveString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }
    actual fun loadString(key: String): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(key)
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    actual fun saveMediaToTemp(bytes: ByteArray, extension: String): String {
        val fileName = "media_${NSUUID().UUIDString}.$extension"
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val documentDir = paths.firstOrNull()?.toString() ?: NSTemporaryDirectory()
        val path = if (documentDir.endsWith("/")) "$documentDir$fileName" else "$documentDir/$fileName"

        val data = if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        } else {
            NSData()
        }

        data.writeToFile(path, atomically = true)
        return path
    }

    private fun resolveSandboxPath(path: String): String {
        val cleanPath = path.removePrefix("file://")
        val filename = cleanPath.substringAfterLast('/')
        
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val documentDir = paths.firstOrNull()?.toString() ?: NSTemporaryDirectory()
        val resolvedPath = if (documentDir.endsWith("/")) "$documentDir$filename" else "$documentDir/$filename"
        
        val fileManager = NSFileManager.defaultManager
        return if (fileManager.fileExistsAtPath(resolvedPath)) resolvedPath else cleanPath
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    actual fun readMediaFromTemp(path: String): ByteArray? {
        val finalPath = resolveSandboxPath(path)
        val data = NSData.dataWithContentsOfFile(finalPath) ?: return null
        if (data.length == 0uL) return ByteArray(0)
        
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return bytes
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    actual fun exportMediaToGallery(path: String): Boolean {
        val cleanPath = resolveSandboxPath(path)
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(cleanPath)) return false

        val isAudio = cleanPath.endsWith(".mp3", ignoreCase = true) || cleanPath.endsWith(".wav", ignoreCase = true) || cleanPath.endsWith(".m4a", ignoreCase = true)
        if (isAudio) {
            val url = NSURL.fileURLWithPath(cleanPath)
            val documentPicker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
            
            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
            var topVC = rootVC
            while (topVC?.presentedViewController != null) {
                topVC = topVC.presentedViewController
            }
            topVC?.presentViewController(documentPicker, animated = true, completion = null)
            return true
        }

        return if (cleanPath.endsWith(".mp4", ignoreCase = true) || cleanPath.endsWith(".mov", ignoreCase = true) || cleanPath.endsWith(".m4v", ignoreCase = true)) {
            if (UIVideoAtPathIsCompatibleWithSavedPhotosAlbum(cleanPath)) {
                UISaveVideoAtPathToSavedPhotosAlbum(cleanPath, null, null, null)
                true
            } else {
                false
            }
        } else {
            val image = UIImage.imageWithContentsOfFile(cleanPath)
            if (image != null) {
                UIImageWriteToSavedPhotosAlbum(image, null, null, null)
                true
            } else {
                false
            }
        }
    }

    actual fun shareMedia(path: String): Boolean {
        val cleanPath = resolveSandboxPath(path)
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(cleanPath)) return false
        
        val url = NSURL.fileURLWithPath(cleanPath)
        val activityVC = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        var topVC = rootVC
        while (topVC?.presentedViewController != null) {
            topVC = topVC.presentedViewController
        }
        
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            activityVC.popoverPresentationController?.sourceView = topVC?.view
        }
        
        topVC?.presentViewController(activityVC, animated = true, completion = null)
        return true
    }

    actual fun resolveLocalPath(path: String): String {
        val hasFilePrefix = path.startsWith("file://")
        val resolved = resolveSandboxPath(path)
        return if (hasFilePrefix && !resolved.startsWith("file://")) "file://$resolved" else resolved
    }

    actual fun showLocalNotification(title: String, body: String, workflowId: String?, nodeId: String?) {
        val content = platform.UserNotifications.UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
            val info = mutableMapOf<Any?, Any>()
            if (!workflowId.isNullOrEmpty()) {
                info["workflow_id"] = workflowId
            }
            if (!nodeId.isNullOrEmpty()) {
                info["node_id"] = nodeId
            }
            info["type"] = "generation_complete"
            setUserInfo(info)
        }
        val request = platform.UserNotifications.UNNotificationRequest.requestWithIdentifier(
            identifier = "local_notify_" + NSUUID().UUIDString,
            content = content,
            trigger = null
        )
        platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { _ -> }
    }

    actual fun startLiveActivity(
        workflowId: String,
        workflowName: String,
        nodeId: String,
        nodeTitle: String,
        nodeType: String,
        currentStep: Int,
        totalSteps: Int,
        stepNodeTypesJson: String
    ) {
        app.ak25.pocketflow.platform.LiveActivityBridgeHolder.current?.startLiveActivity(
            workflowId, workflowName, nodeId, nodeTitle, nodeType, currentStep, totalSteps, stepNodeTypesJson
        )
    }

    actual fun updateLiveActivity(
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
    ) {
        app.ak25.pocketflow.platform.LiveActivityBridgeHolder.current?.updateLiveActivity(
            nodeId, status, progress, message, isFinished, isSuccess, currentStep, totalSteps, completedSteps, nodeTitle, nodeType
        )
    }

    actual fun endLiveActivity(
        nodeId: String,
        isSuccess: Boolean,
        message: String,
        completedSteps: Int,
        totalSteps: Int
    ) {
        app.ak25.pocketflow.platform.LiveActivityBridgeHolder.current?.endLiveActivity(
            nodeId, isSuccess, message, completedSteps, totalSteps
        )
    }

    private var activeBgTaskId: platform.UIKit.UIBackgroundTaskIdentifier = platform.UIKit.UIBackgroundTaskInvalid

    actual fun beginBackgroundTask(name: String) {
        try {
            if (activeBgTaskId != platform.UIKit.UIBackgroundTaskInvalid) {
                endBackgroundTask()
            }
            activeBgTaskId = platform.UIKit.UIApplication.sharedApplication.beginBackgroundTaskWithName(name) {
                endBackgroundTask()
            }
            println("[iOS-Background] 🛡️ Started background task '$name' (id=$activeBgTaskId)")
        } catch (e: Exception) {
            println("[iOS-Background] ⚠️ beginBackgroundTask error: ${e.message}")
        }
    }

    actual fun endBackgroundTask() {
        try {
            if (activeBgTaskId != platform.UIKit.UIBackgroundTaskInvalid) {
                val id = activeBgTaskId
                activeBgTaskId = platform.UIKit.UIBackgroundTaskInvalid
                platform.UIKit.UIApplication.sharedApplication.endBackgroundTask(id)
                println("[iOS-Background] 🏁 Ended background task (id=$id)")
            }
        } catch (e: Exception) {
            println("[iOS-Background] ⚠️ endBackgroundTask error: ${e.message}")
        }
    }
}
