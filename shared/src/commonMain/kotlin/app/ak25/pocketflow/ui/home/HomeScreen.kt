package app.ak25.pocketflow.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.ak25.pocketflow.ui.editor.AppIcons
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import app.ak25.pocketflow.ui.editor.getNodeTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.ak25.pocketflow.services.PresenceUser
import app.ak25.pocketflow.services.WorkflowShareRepository

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.models.Workflow
import app.ak25.pocketflow.services.PocketFlowPurchases
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions
import pocketflow.shared.generated.resources.Res
import pocketflow.shared.generated.resources.EduAUVICWANTHand
import pocketflow.shared.generated.resources.pocketflow_logo
import org.jetbrains.compose.resources.painterResource

import app.ak25.pocketflow.services.ExecutionEngine
import kotlinx.serialization.json.*

fun parseHexColor(hex: String): Color {
    try {
        val clean = hex.removePrefix("#")
        val intVal = clean.toLong(16)
        return if (clean.length == 6) {
            Color(intVal or 0xFF000000)
        } else {
            Color(intVal)
        }
    } catch (e: Exception) {
        return Color.White
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    controller: WorkflowController,
    engine: ExecutionEngine,
    backgroundScope: kotlinx.coroutines.CoroutineScope,
    onNavigateToEditor: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    onPaywallStateChanged: (Boolean) -> Unit = {},
    onSignOut: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val workflows by controller.workflows.collectAsState()
    val virtualCurrencies by PocketFlowPurchases.virtualCurrencies.collectAsState()
    var workflowToDelete by remember { mutableStateOf<String?>(null) }
    var workflowToLeave by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }

    // User session state from SharedAuthViewModel and LocalStorage
    val authViewModel = app.ak25.pocketflow.ui.auth.SharedAuthViewModel
    val rawName = remember(authViewModel.userName) {
        authViewModel.userName.ifEmpty { app.ak25.pocketflow.storage.LocalStorage.loadString("user_name") ?: "" }
    }
    val userEmail = remember(authViewModel.userEmail) {
        authViewModel.userEmail.ifEmpty { app.ak25.pocketflow.storage.LocalStorage.loadString("user_email") ?: "" }
    }
    val userName = remember(rawName, userEmail) {
        if (rawName.isNotEmpty()) rawName else if (userEmail.isNotEmpty()) userEmail.substringBefore("@") else ""
    }
    val userInitial = remember(userName, userEmail) {
        (userName.ifEmpty { userEmail }).firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }
    val isGuest = authViewModel.isGuest
    var showUserMenu by remember { mutableStateOf(false) }

    LaunchedEffect(showPaywall) {
        onPaywallStateChanged(showPaywall)
    }
    
    var showRenameUserDialog by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }

    var newWorkflowName by remember { mutableStateOf("") }
    var workflowForOptions by remember { mutableStateOf<Workflow?>(null) }
    var workflowForShare by remember { mutableStateOf<Workflow?>(null) }
    var showJoinSheet by remember { mutableStateOf(false) }
    var showGuestJoinSheet by remember { mutableStateOf(false) }
    // derive the label at function level so bottom sheets can access it
    val availableCreditsLabel = PocketFlowPurchases.getAvailableCreditsLabel(virtualCurrencies)

    var hasAutoResumed by remember { mutableStateOf(false) }

    // Redundant auto-resume LaunchedEffect removed as it is now managed globally in App.kt


    androidx.compose.runtime.LaunchedEffect(Unit) {
        authViewModel.refreshAccountInfo()

        // Ensure Realtime is connected (handles case where user just logged in)
        app.ak25.pocketflow.services.SupabaseRealtimeService.connect()

        PocketFlowPurchases.configure()
        PocketFlowPurchases.refreshCustomerInfo()

        // After login (incl. guest → real account), push any local/guest workflows
        // up to the cloud owned by this user. refreshFromCloud() guards for guests.
        val userId = app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id").orEmpty()
        if (userId.isNotEmpty()) {
            controller.syncWithCloud()
        }
    }

    androidx.compose.runtime.LaunchedEffect(showUserMenu) {
        if (showUserMenu) {
            authViewModel.refreshAccountInfo()
        }
    }



    Scaffold(
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.pocketflow_logo),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "pocketflow",
                        fontFamily = FontFamily(org.jetbrains.compose.resources.Font(Res.font.EduAUVICWANTHand)),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 24.sp
                        )
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Credits chip (opens paywall)
                    Surface(
                        onClick = { showPaywall = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0EA5E9).copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp, 16.dp)) {
                                val path = androidx.compose.ui.graphics.Path()
                                val w = size.width
                                val h = size.height
                                path.moveTo(w / 2f, 0f)
                                path.lineTo(w,       h / 2f)
                                path.lineTo(w / 2f,  h)
                                path.lineTo(0f,      h / 2f)
                                path.close()
                                drawPath(path, color = Color(0xFF0EA5E9))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = availableCreditsLabel ?: "Credits",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0EA5E9)
                            )
                        }
                    }

                    // User avatar button — square with rounded corners, opens bottom sheet
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0EA5E9))
                            .clickable { showUserMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitial,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (showUserMenu) {
                        ModalBottomSheet(
                            onDismissRequest = { showUserMenu = false },
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
                                // Big avatar
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF0EA5E9)),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = userName.ifEmpty { "Signed In" },
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            newUserName = userName
                                            showRenameUserDialog = true
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Edit,
                                            contentDescription = "Rename User",
                                            tint = Color(0xFF0EA5E9),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (userEmail.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = userEmail,
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(Modifier.height(28.dp))
                                if (isGuest) {
                                    Text(
                                        text = "Collaborate with friends?",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Sign in to share workflows and collaborate in real time.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))
                                }
                                Surface(
                                    onClick = {
                                        showUserMenu = false
                                        if (isGuest) onNavigateToLogin() else onSignOut()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isGuest) Color(0xFF0EA5E9).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            if (isGuest) AppIcons.Login else AppIcons.SignOut,
                                            contentDescription = if (isGuest) "Sign In" else "Sign Out",
                                            tint = if (isGuest) Color(0xFF0EA5E9) else Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isGuest) "Sign In" else "Sign Out",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isGuest) Color(0xFF0EA5E9) else Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Dotted outline card for workflow creation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF9F9FB))
                                .dashedBorder(1.5.dp, Color.Gray.copy(alpha = 0.5f), 16.dp)
                                .clickable {
                                    newWorkflowName = ""
                                    showCreateSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = AppIcons.Add,
                                    contentDescription = "Create New",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Create Workflow",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        // Join by code button — same style as Create Workflow card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF9F9FB))
                                .dashedBorder(1.5.dp, Color.Gray.copy(alpha = 0.5f), 12.dp)
                                .clickable {
                                    if (isGuest) {
                                        showGuestJoinSheet = true
                                    } else {
                                        showJoinSheet = true
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Share,
                                    contentDescription = "Join",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Join with code",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                    val sortedWorkflows = workflows.reversed().sortedByDescending { it.isPinned }
                    items(sortedWorkflows) { workflow ->
                        // Per-card presence state
                        var activeUsers by remember(workflow.id) {
                            mutableStateOf<List<PresenceUser>>(emptyList())
                        }
                        LaunchedEffect(workflow.id) {
                            while (true) {
                                activeUsers = WorkflowShareRepository.fetchActivePresenceUsers(workflow.id)
                                delay(15_000)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(parseHexColor(workflow.cardColorHex))
                                .combinedClickable(
                                    onClick = {
                                        controller.loadWorkflow(workflow.id)
                                        onNavigateToEditor()
                                    },
                                    onLongClick = {
                                        workflowForOptions = workflow
                                    }
                                )
                                .padding(20.dp)
                        ) {
                            val isAnyNodeRunning = workflow.nodes.any { it.status == app.ak25.pocketflow.models.NodeStatus.RUNNING || it.status == app.ak25.pocketflow.models.NodeStatus.PENDING }
                            val runableNodes = workflow.nodes.filter { it.type != app.ak25.pocketflow.models.NodeType.TEXT_PROMPT && it.type != app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE }
                            val isCompleted = runableNodes.isNotEmpty() && runableNodes.all { it.status == app.ak25.pocketflow.models.NodeStatus.COMPLETED }

                            Row(
                                modifier = Modifier.align(Alignment.TopEnd),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (workflow.isPinned) {
                                    Icon(
                                        imageVector = AppIcons.Pin,
                                        contentDescription = "Pinned",
                                        tint = Color.Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (isAnyNodeRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (isCompleted) {
                                    Text(
                                        text = "✓",
                                        color = Color(0xFF34C759),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = workflow.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    if (workflow.createdAtDate.isNotEmpty()) {
                                        Text(
                                            text = workflow.createdAtDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                 if (workflow.nodes.isEmpty()) {
                                     Text(
                                         text = "No nodes",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = Color.Gray
                                     )
                                 } else {
                                     val grouped = workflow.nodes.groupBy { node ->
                                         if (node.type == app.ak25.pocketflow.models.NodeType.UPLOADED_IMAGE) {
                                             app.ak25.pocketflow.models.NodeType.IMAGE_GENERATION
                                         } else {
                                             node.type
                                         }
                                     }.toList()
                                     val chunks = grouped.chunked(6).reversed()
                                     Column(
                                         verticalArrangement = Arrangement.spacedBy(6.dp)
                                     ) {
                                         chunks.forEach { rowItems ->
                                             Row(
                                                 horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 rowItems.forEach { (type, nodesOfThisType) ->
                                                     val (color, icon) = getNodeTheme(type)
                                                     Row(
                                                         modifier = Modifier
                                                             .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                                             .padding(horizontal = 6.dp, vertical = 3.dp),
                                                         verticalAlignment = Alignment.CenterVertically
                                                     ) {
                                                         Icon(
                                                             imageVector = icon,
                                                             contentDescription = null,
                                                             tint = color,
                                                             modifier = Modifier.size(12.dp)
                                                         )
                                                         Spacer(modifier = Modifier.width(4.dp))
                                                         Text(
                                                             text = nodesOfThisType.size.toString(),
                                                             fontSize = 11.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             color = color
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                 }
                            }

                            // ── Presence avatars ─ bottom-right corner ────────────────
                            val myUid = remember { app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id") ?: "" }
                            val allMembers = remember(workflow.membersJson) {
                                try {
                                    val arr = Json.parseToJsonElement(workflow.membersJson).jsonArray
                                    arr.mapNotNull { el: JsonElement ->
                                        el.jsonObject["userId"]?.jsonPrimitive?.contentOrNull
                                    }
                                } catch (e: Exception) { emptyList<String>() }
                            }
                            val activeUserIds = activeUsers.map { it.userId.lowercase() }.toSet()
                            val displayMembers = activeUsers.map { it.userId }
                                .distinct()
                                .filter { !it.equals(myUid, ignoreCase = true) }
                                .take(4)
                            if (displayMembers.isNotEmpty()) {
                                val avatarSize = 34.dp
                                val overlap = 14.dp
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 6.dp, end = 6.dp)
                                ) {
                                    val totalWidth = avatarSize + (overlap * (displayMembers.size - 1))
                                    Box(modifier = Modifier.width(totalWidth).height(avatarSize)) {
                                        displayMembers.forEachIndexed { i, uid ->
                                            val isActive = uid.lowercase() in activeUserIds
                                            val bgColor = if (isActive) {
                                                val palette = listOf(
                                                    Color(0xFFFFCC00), Color(0xFFFF3B30),
                                                    Color(0xFF007AFF), Color(0xFFFF9500)
                                                )
                                                palette[kotlin.math.abs(uid.hashCode()) % palette.size]
                                            } else Color(0xFFBDBDBD)
                                            val activeUser = activeUsers.firstOrNull { it.userId.equals(uid, ignoreCase = true) }
                                            val initials = (activeUser?.userName ?: uid)
                                                .split(" ")
                                                .mapNotNull { word: String -> word.firstOrNull()?.uppercaseChar()?.toString() }
                                                .take(2).joinToString("").ifEmpty { "?" }
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = (i * overlap.value).dp)
                                                    .size(avatarSize)
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .clip(CircleShape)
                                                    .background(bgColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = initials,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        if (workflowForOptions != null) {
            val workflow = workflowForOptions!!
            val isPinned = workflow.isPinned
            ModalBottomSheet(
                onDismissRequest = { workflowForOptions = null },
                containerColor = Color.Transparent,
                dragHandle = null,
                scrimColor = Color.Black.copy(alpha = 0.3f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = workflow.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Card Color Picker Row
                        Text(
                            text = "card color",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Start).padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val crayonColors = listOf("#FFFFFF", "#FFE5EC", "#FFF2CC", "#E2F0D9", "#E8F0FE")
                            crayonColors.forEach { hex ->
                                val colorVal = parseHexColor(hex)
                                val isSelectedColor = workflow.cardColorHex == hex
                                Surface(
                                    onClick = {
                                        controller.updateWorkflowColor(workflow.id, hex)
                                        workflowForOptions = workflow.copy(cardColorHex = hex)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = colorVal,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelectedColor) 2.dp else 1.dp,
                                        color = if (isSelectedColor) Color(0xFF0EA5E9) else Color.Gray.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    if (isSelectedColor) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "✓",
                                                color = Color(0xFF0EA5E9),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Pin/Unpin option
                        Surface(
                            onClick = {
                                controller.togglePinWorkflow(workflow.id)
                                workflowForOptions = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF2F2F7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Pin,
                                    contentDescription = if (isPinned) "Unpin" else "Pin",
                                    tint = if (isPinned) Color(0xFF0EA5E9) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (isPinned) "Unpin from Top" else "Pin to Top",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Share option
                        Surface(
                            onClick = {
                                if (isGuest) {
                                    // Guests can't share — prompt them to sign in first
                                    workflowForOptions = null
                                    showGuestJoinSheet = true
                                } else {
                                    workflowForShare = workflow
                                    workflowForOptions = null
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF0F4FF),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Share,
                                    contentDescription = "Share",
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Share & Collaborate",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF6366F1)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Delete (owner) / Leave (member) option
                        val myUid = app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id").orEmpty()
                        val isOwner = workflow.ownerUserId.isBlank() || workflow.ownerUserId == myUid
                        Surface(
                            onClick = {
                                workflowForOptions = null
                                if (isOwner) workflowToDelete = workflow.id else workflowToLeave = workflow.id
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFEBEB),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Close,
                                    contentDescription = if (isOwner) "Delete" else "Leave",
                                    tint = Color(0xFFFF3B30),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (isOwner) "Delete Workflow" else "Leave Workflow",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFFF3B30)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (workflowToDelete != null) {
            val workflow = workflows.find { it.id == workflowToDelete }
            AlertDialog(
                onDismissRequest = { workflowToDelete = null },
                title = { Text("Delete Workflow") },
                text = { Text("Are you sure you want to delete '${workflow?.name}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        controller.deleteWorkflow(workflowToDelete!!)
                        workflowToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workflowToDelete = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface
            )
        }

        if (workflowToLeave != null) {
            val workflow = workflows.find { it.id == workflowToLeave }
            AlertDialog(
                onDismissRequest = { workflowToLeave = null },
                title = { Text("Leave Workflow") },
                text = { Text("Are you sure you want to leave '${workflow?.name}'? You will no longer be able to access this shared workflow.") },
                confirmButton = {
                    TextButton(onClick = {
                        controller.leaveWorkflow(workflowToLeave!!)
                        workflowToLeave = null
                    }) {
                        Text("Leave", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workflowToLeave = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface
            )
        }

        if (showPaywall) {
            // Ensure RevenueCat is configured
            remember {
                PocketFlowPurchases.configure()
            }
            
            val paywallOptions = remember {
                PaywallOptions(
                    dismissRequest = { 
                        showPaywall = false
                        // Refresh credits after paywall closes
                        PocketFlowPurchases.refreshVirtualCurrencies(forceRefresh = true)
                    }
                )
            }
            Paywall(options = paywallOptions)
        }

        if (showCreateSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCreateSheet = false },
                containerColor = Color.Transparent,
                dragHandle = null,
                scrimColor = Color.Black.copy(alpha = 0.3f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "New Workflow",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = newWorkflowName,
                            onValueChange = { if (it.length <= 20) newWorkflowName = it },
                            placeholder = { Text("Name your workflow...", color = Color.Gray) },
                            supportingText = {
                                Text(
                                    "${newWorkflowName.length}/20",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    fontSize = 11.sp,
                                    color = if (newWorkflowName.length >= 20) Color(0xFFFF3B30) else Color.Gray
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1A1A1A),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedTextColor = Color(0xFF1A1A1A),
                                unfocusedTextColor = Color(0xFF1A1A1A),
                                cursorColor = Color(0xFF1A1A1A)
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val name = if (newWorkflowName.isNotBlank()) newWorkflowName.trim().take(20) else "Untitled Workflow"
                                controller.createWorkflow(name)
                                showCreateSheet = false
                                onNavigateToEditor()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Create", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // ── Share sheet (owner view with code + member presences) ─────────────────
    workflowForShare?.let { wf ->
        WorkflowShareSheet(
            workflow = wf,
            onDismiss = { workflowForShare = null }
        )
    }

    // ── Join by code sheet ────────────────────────────────────────────────────
    if (showJoinSheet) {
        JoinWorkflowSheet(
            onDismiss = { showJoinSheet = false },
            onJoined = { shareInfo ->
                showJoinSheet = false
                // Fetch the shared workflow and add it to this user's dashboard
                controller.addSharedWorkflow(shareInfo.workflowId, shareInfo.workflowName)
            },
            onNeedSignIn = {
                // Stored session is stale/invalid → Appwrite treats us as a guest,
                // so we can't read any workflow docs. Route to the sign-in prompt.
                showJoinSheet = false
                showGuestJoinSheet = true
            }
        )
    }

    // ── Guest "Join with code" → user details + sign in sheet ────────────────
    if (showGuestJoinSheet) {
        GuestSignInSheet(
            onDismiss = { showGuestJoinSheet = false },
            onSignIn = {
                showGuestJoinSheet = false
                onNavigateToLogin()
            }
        )
    }

    if (showRenameUserDialog) {
        AlertDialog(
            onDismissRequest = { showRenameUserDialog = false },
            title = { Text("Update Name") },
            text = {
                OutlinedTextField(
                    value = newUserName,
                    onValueChange = { newUserName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.updateUserName(newUserName)
                        showRenameUserDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameUserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp
) = this.drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    )
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
    )
}
