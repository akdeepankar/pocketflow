package app.ak25.pocketflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pocketflow.shared.generated.resources.Res
import pocketflow.shared.generated.resources.EduAUVICWANTHand
import app.ak25.pocketflow.ui.editor.AppIcons
import app.ak25.pocketflow.storage.ActivityTracker
import app.ak25.pocketflow.models.ActivityType
import app.ak25.pocketflow.models.ActivityLog
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatTimestamp(epochMs: Long): String {
    try {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs)
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = dt.hour.toString().padStart(2, '0')
        val minute = dt.minute.toString().padStart(2, '0')
        val monthStr = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        return "${dt.dayOfMonth} $monthStr ${dt.year}, $hour:$minute"
    } catch (e: Exception) {
        return "Just now"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    onBack: () -> Unit
) {
    val logs by ActivityTracker.logs.collectAsState()
    var showClearConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "activity log",
                        fontFamily = FontFamily(org.jetbrains.compose.resources.Font(pocketflow.shared.generated.resources.Res.font.EduAUVICWANTHand)),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.W600,
                            color = Color.Black,
                            fontSize = 24.sp
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = {
                            val sb = StringBuilder()
                            sb.append("=== POCKETFLOW ACTIVITY LOG ===\n")
                            sb.append("User ID: ${app.ak25.pocketflow.services.PocketFlowPurchases.getAppUserID()}\n")
                            sb.append("Exported: ${formatTimestamp(app.ak25.pocketflow.utils.getCurrentTimeMillis())}\n\n")
                            logs.forEach { log ->
                                sb.append("[${formatTimestamp(log.timestamp)}] ")
                                sb.append("${log.type.name} - ${log.title}\n")
                                sb.append("Details: ${log.details}\n")
                                if (log.creditsSpent != null) {
                                    sb.append("Credits Spent: ${log.creditsSpent}\n")
                                }
                                if (log.workflowName != null) {
                                    sb.append("Workflow: ${log.workflowName}\n")
                                }
                                sb.append("--------------------------------------------------\n\n")
                            }
                            val textBytes = sb.toString().encodeToByteArray()
                            try {
                                val tempPath = app.ak25.pocketflow.storage.LocalStorage.saveMediaToTemp(textBytes, "txt")
                                app.ak25.pocketflow.storage.LocalStorage.shareMedia(tempPath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }) {
                            Icon(
                                imageVector = AppIcons.Download,
                                contentDescription = "Download Log",
                                tint = Color.Black
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { showClearConfirmation = true }) {
                            Icon(
                                imageVector = AppIcons.Trash,
                                contentDescription = "Clear All Logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (showClearConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearConfirmation = false },
                title = { Text("Clear Activity Log") },
                text = { Text("Are you sure you want to clear all activity logs? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            ActivityTracker.clearLogs()
                            showClearConfirmation = false
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmation = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color.White
            )
        }
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No activity logs yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Actions you perform will be logged here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                items(logs.size) { index ->
                    val log = logs[index]
                    ActivityItem(
                        log = log,
                        isFirst = index == 0,
                        isLast = index == logs.size - 1
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityItem(
    log: ActivityLog,
    isFirst: Boolean,
    isLast: Boolean
) {
    val iconColor = when (log.type) {
        ActivityType.CREATE_WORKFLOW -> Color(0xFF34C759)
        ActivityType.DELETE_WORKFLOW -> Color(0xFFFF3B30)
        ActivityType.LEAVE_WORKFLOW -> Color(0xFFFF9500)
        ActivityType.ADD_NODE -> Color(0xFF5856D6)
        ActivityType.RUN_NODE -> Color(0xFF007AFF)
        ActivityType.SPEND_CREDITS -> Color(0xFFFF9500)
        ActivityType.ADD_CREDITS -> Color(0xFF34C759)
        ActivityType.RENAME_WORKFLOW -> Color(0xFF007AFF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Timeline line connecting items
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .padding(
                        top = if (isFirst) 14.dp else 0.dp,
                        bottom = if (isLast) 14.dp else 0.dp
                    )
                    .background(Color.Gray.copy(alpha = 0.2f))
            )
            // Bullet dot in the middle of line
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(8.dp)
                    .background(iconColor, CircleShape)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Text(
                    text = formatTimestamp(log.timestamp),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            if (!log.workflowName.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = log.workflowName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color(0xFF0EA5E9)
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = log.details,
                fontSize = 13.sp,
                color = Color.DarkGray
            )

            if (log.creditsSpent != null && log.creditsSpent > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "-${log.creditsSpent} credits spent",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF9500)
                )
            }
        }
    }
}
