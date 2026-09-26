package app.ak25.pocketflow.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.NodeNote
import app.ak25.pocketflow.models.WorkflowNode
import app.ak25.pocketflow.storage.LocalStorage
import app.ak25.pocketflow.ui.home.MemberAvatar
import app.ak25.pocketflow.services.WorkflowMember
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

    val workflow by controller.currentWorkflow.collectAsState()
    val node = workflow?.nodes?.find { it.id == nodeId }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                text = "Node Notes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1C1E)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (node != null) {
                    val label = if (node.type.nodeName.contains("Generation")) node.type.nodeName.replace(" Generation", "") else node.type.nodeName
                    "$label — notes for this node"
                } else "Notes for this node",
                fontSize = 13.sp,
                color = Color(0xFF8E8E93)
            )
            Spacer(Modifier.height(16.dp))

            val notes = node?.notes?.toList().orEmpty().sortedByDescending { it.createdAt }
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF2F2F7))
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No notes yet. Add a note for your team below.",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            isMine = note.authorUserId.isNotEmpty() && note.authorUserId == myUserId,
                            onDelete = { controller.removeNodeNote(nodeId, note.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val workflowMembers = remember(workflow?.membersJson) {
                controller.getWorkflowMembers(workflow?.id).filter { it.userId != myUserId }
            }

            if (workflowMembers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mention:", fontSize = 11.sp, color = Color(0xFF8E8E93))
                    workflowMembers.take(4).forEach { member ->
                        val handle = member.userName.ifEmpty { "member" }.replace(" ", "_")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F2FF),
                            modifier = Modifier.clickable {
                                val prefix = if (newNote.isNotEmpty() && !newNote.endsWith(" ")) "$newNote " else newNote
                                newNote = "$prefix@$handle "
                            }
                        ) {
                            Text(
                                text = "@$handle",
                                fontSize = 11.sp,
                                color = Color(0xFF0A84FF),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newNote,
                    onValueChange = { newNote = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a note…", color = Color(0xFFC7C7CC)) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0A84FF),
                        unfocusedBorderColor = Color(0xFFE5E5EA),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                Button(
                    onClick = {
                        val text = newNote.trim()
                        if (text.isNotEmpty()) {
                            controller.addNodeNote(nodeId, text)
                            newNote = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = newNote.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Send", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: NodeNote,
    isMine: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMine) Color(0xFFE8F2FF) else Color(0xFFF2F2F7))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        MemberAvatar(
            member = WorkflowMember(userId = note.authorUserId, userName = note.authorName),
            isOnline = false,
            isMe = false,
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = note.authorName.ifEmpty { "Member" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatNoteTime(note.createdAt),
                        fontSize = 11.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
                if (isMine) {
                    Text(
                        text = "Remove",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF5350),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = note.text,
                fontSize = 13.sp,
                color = Color(0xFF3A3A3C)
            )
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