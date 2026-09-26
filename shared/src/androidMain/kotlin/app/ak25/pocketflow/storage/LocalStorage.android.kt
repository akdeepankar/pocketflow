package app.ak25.pocketflow.storage

import android.content.Context
import java.io.File
import java.util.UUID

object AndroidStorageContext {
    var applicationContext: Context? = null
}

actual object LocalStorage {
    actual fun saveString(key: String, value: String) {
        val context = AndroidStorageContext.applicationContext ?: return
        val prefs = context.getSharedPreferences("PocketFlowStorage", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }
    actual fun loadString(key: String): String? {
        val context = AndroidStorageContext.applicationContext ?: return null
        val prefs = context.getSharedPreferences("PocketFlowStorage", Context.MODE_PRIVATE)
        return prefs.getString(key, null)
    }

    actual fun saveMediaToTemp(bytes: ByteArray, extension: String): String {
        val context = AndroidStorageContext.applicationContext ?: return ""
        val fileName = "media_${UUID.randomUUID()}.$extension"
        val file = File(context.filesDir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    actual fun readMediaFromTemp(path: String): ByteArray? {
        val context = AndroidStorageContext.applicationContext
        val file = if (context != null) {
            val filename = path.substringAfterLast('/')
            val resolvedFile = File(context.filesDir, filename)
            if (resolvedFile.exists()) resolvedFile else File(path)
        } else {
            File(path)
        }
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    actual fun exportMediaToGallery(path: String): Boolean {
        val context = AndroidStorageContext.applicationContext ?: return false
        val sourceFile = File(path)
        if (!sourceFile.exists()) return false

        return try {
            val contentResolver = context.contentResolver
            val isVideo = path.endsWith(".mp4", ignoreCase = true)
            val isAudio = path.endsWith(".mp3", ignoreCase = true) || path.endsWith(".wav", ignoreCase = true) || path.endsWith(".m4a", ignoreCase = true)
            
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else if (isAudio) "audio/mpeg" else "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) android.os.Environment.DIRECTORY_MOVIES else if (isAudio) android.os.Environment.DIRECTORY_DOWNLOADS else android.os.Environment.DIRECTORY_PICTURES)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collectionUri = if (isVideo) {
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else if (isAudio) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = contentResolver.insert(collectionUri, contentValues) ?: return false
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual fun shareMedia(path: String): Boolean {
        val context = AndroidStorageContext.applicationContext ?: return false
        val file = java.io.File(path)
        if (!file.exists()) return false
        
        return try {
            val builder = android.os.StrictMode.VmPolicy.Builder()
            android.os.StrictMode.setVmPolicy(builder.build())
            
            val uri = android.net.Uri.fromFile(file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = if (path.endsWith(".mp4", ignoreCase = true)) "video/mp4" else if (path.endsWith(".mp3", ignoreCase = true)) "audio/mp3" else "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share Media").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual fun resolveLocalPath(path: String): String {
        val context = AndroidStorageContext.applicationContext ?: return path
        val filename = path.substringAfterLast('/')
        val resolvedFile = File(context.filesDir, filename)
        return if (resolvedFile.exists()) resolvedFile.absolutePath else path
    }

    actual fun showLocalNotification(title: String, body: String, workflowId: String?, nodeId: String?) {
        val context = AndroidStorageContext.applicationContext ?: return
        try {
            val channelId = "pocketflow_notifications"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "PocketFlow Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Generation and workflow notifications"
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, channelId)
            } else {
                android.app.Notification.Builder(context)
            }

            // Create PendingIntent for deep linking
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!workflowId.isNullOrEmpty()) {
                    putExtra("workflow_id", workflowId)
                }
                if (!nodeId.isNullOrEmpty()) {
                    putExtra("node_id", nodeId)
                }
                putExtra("type", "generation_complete")
            }

            val pendingIntent = if (launchIntent != null) {
                val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                android.app.PendingIntent.getActivity(
                    context,
                    kotlin.math.abs((workflowId?.hashCode() ?: 0) xor (nodeId?.hashCode() ?: 0)),
                    launchIntent,
                    flags
                )
            } else null

            builder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setDefaults(android.app.Notification.DEFAULT_ALL)

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
            }

            val notifId = kotlin.math.abs((workflowId?.hashCode() ?: 0) * 31 + (nodeId?.hashCode() ?: java.util.UUID.randomUUID().hashCode()))
            notificationManager.notify(notifId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun startLiveActivity(workflowId: String, workflowName: String, nodeId: String, nodeTitle: String, nodeType: String) {}
    actual fun updateLiveActivity(nodeId: String, status: String, progress: Double, message: String, isFinished: Boolean, isSuccess: Boolean) {}
    actual fun endLiveActivity(nodeId: String, isSuccess: Boolean, message: String) {}

    actual fun beginBackgroundTask(name: String) {
        // Android background execution is managed by ExecutionEngine.engineScope
    }

    actual fun endBackgroundTask() {
        // Android background execution is managed by ExecutionEngine.engineScope
    }
}
