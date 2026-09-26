package app.ak25.pocketflow.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.models.Workflow
import app.ak25.pocketflow.services.JoinResult
import app.ak25.pocketflow.services.WorkflowMember
import app.ak25.pocketflow.services.WorkflowShareInfo
import app.ak25.pocketflow.services.WorkflowShareRepository
import app.ak25.pocketflow.storage.LocalStorage
import app.ak25.pocketflow.ui.editor.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet shown when the user taps the share button on a workflow card.
 *
 * Owner view:
 *   - Displays the 5-char join code (tap to copy)
 *   - Shows up to MAX_MEMBERS avatar chips with green-outline if that user is
 *     currently active (online via Appwrite Presences API, 30 s TTL)
 *
 * Guest view (opened via "Join workflow" from a separate entry):
 *   - Text field to enter a code
 *   - Joins the workflow and merges it into WorkflowController
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowShareSheet(
    workflow: Workflow,
    onDismiss: () -> Unit,
    onWorkflowJoined: (Workflow) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val myUserId = remember { LocalStorage.loadString("appwrite_user_id") ?: "" }

    // Share state
    var shareInfo by remember { mutableStateOf<WorkflowShareInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }  // background refresh while cache shown
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var codeCopied by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }  // increment to trigger retry

    // Active presence set (userId → online)
    var activeUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun loadShare() {
        // 1. Show cached value instantly if available
        val cached = WorkflowShareRepository.getCachedSharePublic(workflow.id)
        if (cached != null) {
            shareInfo = cached
            isLoading = false
            isRefreshing = true  // silently refresh in background
        }
        // 2. Fetch from network (pass full workflow so a local-only workflow gets uploaded on share)
        val fresh = WorkflowShareRepository.getOrCreateShare(workflow.id, workflow.name, workflow)
        isLoading = false
        isRefreshing = false
        if (fresh != null) {
            shareInfo = fresh
            errorMsg = null
        } else if (cached == null) {
            errorMsg = "Could not load share info. Check your connection."
        }
    }

    // Load on open + on retry
    LaunchedEffect(workflow.id, retryKey) {
        loadShare()
        // Poll presences every 10s
        while (true) {
            delay(10_000)
            if (shareInfo != null) {
                val actives = WorkflowShareRepository.fetchActivePresences(workflow.id)
                activeUserIds = actives.toSet()
            }
        }
    }

    // Heartbeat MY presence while sheet is open
    LaunchedEffect(workflow.id) {
        while (true) {
            WorkflowShareRepository.upsertPresence(workflow.id)
            delay(20_000)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { WorkflowShareRepository.clearPresence(workflow.id) }
            onDismiss()
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Share Workflow",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        text = workflow.name,
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                }
                IconButton(onClick = {
                    scope.launch { WorkflowShareRepository.clearPresence(workflow.id) }
                    onDismiss()
                }) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF0F0F0))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF1A1A1A)
                    )
                }
            } else if (shareInfo == null && errorMsg != null) {
                // Error state with Retry
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = errorMsg ?: "Unable to load share info",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = { retryKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retry", color = Color.White, fontSize = 14.sp)
                    }
                }
            } else if (shareInfo != null) {
                val share = shareInfo!!
                val isOwner = share.ownerUserId == myUserId
                val seatsFull = share.members.size >= WorkflowShareRepository.MAX_MEMBERS

                // ── Join Code Card ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "INVITE CODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFAAAAAA),
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF7F7F9))
                            .clickable {
                                codeCopied = true
                                scope.launch { delay(1500); codeCopied = false }
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = share.joinCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = Color(0xFF1A1A1A)
                        )
                        AnimatedContent(
                            targetState = codeCopied,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { copied ->
                            if (copied) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("✓", fontSize = 14.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                                    Text("Copied", fontSize = 12.sp, color = Color(0xFF22C55E))
                                }
                            } else {
                                Icon(
                                    imageVector = AppIcons.Share,
                                    contentDescription = "Copy code",
                                    tint = Color(0xFFAAAAAA),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Share this code with collaborators. Up to ${WorkflowShareRepository.MAX_MEMBERS} people can join.",
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA),
                        lineHeight = 16.sp
                    )
                }

                // ── Members ────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MEMBERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFAAAAAA),
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "${share.members.size} / ${WorkflowShareRepository.MAX_MEMBERS}",
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }

                    // Member avatars row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        share.members.forEach { member ->
                            MemberAvatar(
                                member = member,
                                isOnline = member.userId in activeUserIds,
                                isMe = member.userId == myUserId
                            )
                        }

                        // Empty seat slots
                        repeat(WorkflowShareRepository.MAX_MEMBERS - share.members.size) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color(0xFFE0E0E0), CircleShape)
                                    .background(Color(0xFFF7F7F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Add,
                                    contentDescription = "Empty seat",
                                    tint = Color(0xFFCCCCCC),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Seats-full banner
                    if (seatsFull) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFF8E1))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Text(
                                text = "All seats are filled. Remove a member to invite someone new.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }

                errorMsg?.let {
                    Text(it, fontSize = 12.sp, color = Color(0xFFEF4444))
                }
            } else {
                Text(
                    text = "Unable to load share info. Check your connection.",
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Member avatar circle with initials + green presence ring.
 */
@Composable
fun MemberAvatar(
    member: WorkflowMember,
    isOnline: Boolean,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    val initial = member.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColors = listOf(
        Color(0xFF6366F1), Color(0xFF0EA5E9), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6)
    )
    val avatarColor = avatarColors[member.userId.hashCode().and(0x7FFFFFFF) % avatarColors.size]

    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(CircleShape)
                // Green ring when online, subtle border when offline
                .border(
                    width = if (isOnline) 2.5.dp else 1.5.dp,
                    color = if (isOnline) Color(0xFF22C55E) else Color(0xFFE8E8E8),
                    shape = CircleShape
                )
                .padding(if (isOnline) 2.5.dp else 1.5.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Green dot indicator
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
            )
        }

        // "You" label
        if (isMe) {
            Box(
                modifier = Modifier
                    .offset(y = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Text(
                    text = "You",
                    fontSize = 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Small standalone sheet for joining a workflow by code.
 * Shown from a "Join Workflow" button on the Home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinWorkflowSheet(
    onDismiss: () -> Unit,
    onJoined: (WorkflowShareInfo) -> Unit,
    onNeedSignIn: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Join Workflow",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "Enter the 5-character invite code shared with you.",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 5) code = it.uppercase() },
                placeholder = { Text("e.g. A3B7K", fontFamily = FontFamily.Monospace) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1A1A1A),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )

            error?.let {
                Text(it, fontSize = 12.sp, color = Color(0xFFEF4444))
            }

            Button(
                onClick = {
                    if (code.length != 5) { error = "Code must be 5 characters"; return@Button }
                    scope.launch {
                        isLoading = true
                        error = null
                        when (val result = WorkflowShareRepository.joinByCode(code)) {
                            is JoinResult.Success       -> onJoined(result.info)
                            // Already a member (e.g. joined before but workflow vanished from
                            // the dashboard) — re-add it instead of just showing an error.
                            is JoinResult.AlreadyMember -> onJoined(result.info)
                            is JoinResult.InvalidCode   -> error = "Code not found. Double-check and try again."
                            is JoinResult.SeatsFull     -> error = "This workflow is full (max 3 members)."
                            is JoinResult.GuestNotAllowed -> {
                                error = "Your session expired. Please sign in and try again."
                                onNeedSignIn()
                            }
                            is JoinResult.NetworkError  -> error = "Connection error. Check your internet and try again."
                            else                        -> error = "Something went wrong. Please try again."
                        }
                        isLoading = false
                    }
                },
                enabled = code.length == 5 && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Join Workflow", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * Bottom sheet shown for guest users who tap "Join with code".
 * Shows the user's details and a Sign In button so they can create a real
 * session before joining workflows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestSignInSheet(
    onDismiss: () -> Unit,
    onSignIn: () -> Unit
) {
    val authViewModel = app.ak25.pocketflow.ui.auth.SharedAuthViewModel
    val rawName = authViewModel.userName.ifEmpty { LocalStorage.loadString("user_name") ?: "" }
    val userEmail = authViewModel.userEmail.ifEmpty { LocalStorage.loadString("user_email") ?: "" }
    val userName = if (rawName.isNotEmpty()) rawName else if (userEmail.isNotEmpty()) userEmail.substringBefore("@") else "Guest"
    val userInitial = (userName.ifEmpty { userEmail }).firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userInitial,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = userName.ifEmpty { "Guest" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            if (userEmail.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = userEmail,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Collaborate with friends?",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in to join workflows and collaborate in real time.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = {
                    onDismiss()
                    onSignIn()
                },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0EA5E9).copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        AppIcons.Login,
                        contentDescription = "Sign In",
                        tint = Color(0xFF0EA5E9),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sign In",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0EA5E9)
                    )
                }
            }
        }
    }
}
