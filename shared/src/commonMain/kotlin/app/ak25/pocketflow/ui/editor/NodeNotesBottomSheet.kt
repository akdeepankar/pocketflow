package app.ak25.pocketflow.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.NodeNote
import app.ak25.pocketflow.models.NodeType
import app.ak25.pocketflow.models.WorkflowNode
import app.ak25.pocketflow.services.WorkflowMember
import app.ak25.pocketflow.storage.LocalStorage
import app.ak25.pocketflow.ui.home.MemberAvatar
import app.ak25.pocketflow.utils.getCurrentTimeMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeNotesBottomSheet(
    nodeId: String,
    controller: WorkflowController,
    onDismissRequest: () -> Unit
) {
    val myUserId = (LocalStorage.loadString("supabase_user_id") ?: LocalStorage.loadString("appwrite_user_id")).orEmpty()
    var newNote by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val workflow by controller.currentWorkflow.collectAsState()
    val node = workflow?.nodes?.find { it.id == nodeId }
    val (nodeColor, nodeIcon) = getNodeTheme(node?.type ?: NodeType.TEXT_PROMPT)
    val notes = node?.notes?.toList().orEmpty().sortedBy { it.createdAt }

    var noteToDelete by remember { mutableStateOf<NodeNote?>(null) }

    LaunchedEffect(notes.size) {
        if (notes.isNotEmpty()) {
            listState.animateScrollToItem(notes.size - 1)
        }
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = {
                Text(
                    "Delete Note",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this note? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = noteToDelete
                        if (toDelete != null) {
                            controller.removeNodeNote(nodeId, toDelete.id)
                        }
                        noteToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(nodeColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(nodeIcon, contentDescription = null, tint = nodeColor, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = if (node != null) {
                                val label = if (node.type.nodeName.contains("Generation")) node.type.nodeName.replace(" Generation", "") else node.type.nodeName
                                "$label Notes"
                            } else "Node Notes",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val promptSnippet = node?.params?.get("prompt") ?: node?.params?.get("text")
                        Text(
                            text = if (!promptSnippet.isNullOrBlank()) "\"${promptSnippet.take(32)}...\"" else "Team discussion & feedback",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val notesList = node?.notes?.toList().orEmpty()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(AppIcons.Message, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
                        Text(
                            text = "${notesList.size} ${if (notesList.size == 1) "note" else "notes"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(Modifier.height(14.dp))

            // Message List / Empty State
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FAFC))
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFE0F2FE), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Message, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                        }
                        Text(
                            text = "Start the conversation",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Leave notes, feedback, or use @name to mention team members on this node.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        ChatBubbleRow(
                            note = note,
                            isMine = note.authorUserId.isNotEmpty() && note.authorUserId == myUserId,
                            onDelete = { noteToDelete = note }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Quick Mention Bar
            val workflowMembers = remember(workflow?.membersJson) {
                controller.getWorkflowMembers(workflow?.id).filter { it.userId != myUserId }
            }

            if (workflowMembers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mention:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    workflowMembers.take(4).forEach { member ->
                        val handle = member.userName.ifEmpty { "member" }.replace(" ", "_")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(0.5.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.clickable {
                                val prefix = if (newNote.isNotEmpty() && !newNote.endsWith(" ")) "$newNote " else newNote
                                newNote = "$prefix@$handle "
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "@",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = handle,
                                    fontSize = 11.sp,
                                    color = Color(0xFF1D4ED8),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Composer Input Bar
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick @ icon insert button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                            .clickable {
                                if (workflowMembers.isNotEmpty()) {
                                    val firstHandle = workflowMembers.first().userName.ifEmpty { "member" }.replace(" ", "_")
                                    val prefix = if (newNote.isNotEmpty() && !newNote.endsWith(" ")) "$newNote " else newNote
                                    newNote = "$prefix@$firstHandle "
                                } else {
                                    newNote = if (newNote.endsWith(" ") || newNote.isEmpty()) "${newNote}@" else "$newNote @"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "@",
                            color = Color(0xFF475569),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    OutlinedTextField(
                        value = newNote,
                        onValueChange = { newNote = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Add a note... (@ to mention)",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp
                        ),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    val isSendActive = newNote.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSendActive) Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF0056D2)))
                                else Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1)))
                            )
                            .clickable(enabled = isSendActive) {
                                val text = newNote.trim()
                                if (text.isNotEmpty()) {
                                    controller.addNodeNote(nodeId, text)
                                    newNote = ""
                                    focusManager.clearFocus()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↑",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = (-1).dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Chat Bubble Row with full @mention badges and sender info.
 */
@Composable
private fun ChatBubbleRow(
    note: NodeNote,
    isMine: Boolean,
    onDelete: () -> Unit
) {
    if (isMine) {
        // Current User Message (Aligned Right)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
                color = Color(0xFF007AFF),
                shadowElevation = 1.dp,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MentionMessageContent(
                        text = note.text,
                        isMine = true,
                        textColor = Color.White,
                        fontSize = 13.5.sp
                    )
                }
            }

            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "You • ${formatNoteTime(note.createdAt)}",
                    fontSize = 10.5.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "Delete",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEF4444),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    } else {
        // Collaborator Message (Aligned Left)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MemberAvatar(
                member = WorkflowMember(userId = note.authorUserId, userName = note.authorName),
                isOnline = false,
                isMe = false,
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.widthIn(max = 280.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = note.authorName.ifEmpty { "Member" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = formatNoteTime(note.createdAt),
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        MentionMessageContent(
                            text = note.text,
                            isMine = false,
                            textColor = Color(0xFF0F172A),
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatNoteTime(createdAt: Long): String {
    if (createdAt <= 0L) return ""
    val now = getCurrentTimeMillis()
    val diffMs = now - createdAt
    val minutes = diffMs / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}