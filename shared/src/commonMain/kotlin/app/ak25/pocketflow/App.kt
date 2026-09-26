package app.ak25.pocketflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import app.ak25.pocketflow.domain.WorkflowController
import app.ak25.pocketflow.services.ExecutionEngine
import app.ak25.pocketflow.ui.editor.EditorScreen
import app.ak25.pocketflow.ui.home.HomeScreen
import app.ak25.pocketflow.ui.paywall.PaywallScreen
import app.ak25.pocketflow.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import pocketflow.shared.generated.resources.Res
import pocketflow.shared.generated.resources.EduAUVICWANTHand


import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import app.ak25.pocketflow.ui.editor.AppIcons
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager

enum class Screen {
    HOME, ASSETS, EDITOR, SETTINGS, PAYWALL, RATES, ACTIVITY
}

private val MinimalLightTheme = lightColorScheme(
    primary = Color(0xFF0EA5E9),
    onPrimary = Color.White,
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun App() {
    val controller = remember { WorkflowController() }
    val engine = remember { ExecutionEngine(controller) }
    val appScope = rememberCoroutineScope()
    
    // Automatically continue loading jobs that are running/pending across all workflows
    val workflowsList by controller.workflows.collectAsState()
    
    LaunchedEffect(workflowsList) {
        if (workflowsList.isNotEmpty()) {
            workflowsList.forEach { wf ->
                wf.nodes.forEach { node ->
                    if (node.status == app.ak25.pocketflow.models.NodeStatus.RUNNING || node.status == app.ak25.pocketflow.models.NodeStatus.PENDING) {
                        if (!engine.isRunning(node.id)) {
                            val jId = node.jobId ?: node.params["jobId"]
                            if (!jId.isNullOrEmpty() && node.outputUrl.isNullOrEmpty()) {
                                engine.engineScope.launch {
                                    val previousBalance = app.ak25.pocketflow.services.PocketFlowPurchases.getAvailableCreditsBalance()
                                    val expectedDeduction = app.ak25.pocketflow.services.PocketFlowPurchases.estimateNodeCredits(node)
                                    val success = engine.runNode(node.id)
                                    if (success) {
                                        val deductSuccess = app.ak25.pocketflow.services.PocketFlowPurchases.deductCredits(expectedDeduction)
                                        app.ak25.pocketflow.services.PocketFlowPurchases.refreshVirtualCurrenciesAfterRun(
                                            previousBalance = previousBalance,
                                            expectedDeduction = expectedDeduction
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
    
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    var isPaywallOpen by remember { mutableStateOf(false) }

    var showOnboarding by remember {
        mutableStateOf(app.ak25.pocketflow.storage.LocalStorage.loadString("onboarding_completed") != "true")
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.HOME && pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        } else if (currentScreen == Screen.ASSETS && pagerState.currentPage != 1) {
            pagerState.scrollToPage(1)
        } else if (currentScreen == Screen.SETTINGS && pagerState.currentPage != 2) {
            pagerState.scrollToPage(2)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0 && currentScreen != Screen.HOME) {
            currentScreen = Screen.HOME
        } else if (pagerState.currentPage == 1 && currentScreen != Screen.ASSETS) {
            currentScreen = Screen.ASSETS
        } else if (pagerState.currentPage == 2 && currentScreen != Screen.SETTINGS) {
            currentScreen = Screen.SETTINGS
        }
    }

    val authViewModel = app.ak25.pocketflow.ui.auth.SharedAuthViewModel
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    // Sync workflows with Supabase whenever the user becomes logged in
    LaunchedEffect(authViewModel.isLoggedIn) {
        if (authViewModel.isLoggedIn) {
            controller.syncWithCloud()
        }
    }

    // Observe push notification deep links
    val pendingDeepLink by app.ak25.pocketflow.domain.DeepLinkRouter.pendingDeepLink.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        println("[App] 🚀 Routing Deep Link: workflowId=${target.workflowId}, nodeId=${target.nodeId}, type=${target.type}")
        controller.loadWorkflow(target.workflowId)
        if (!target.nodeId.isNullOrEmpty()) {
            val isNote = target.type == "node_note"
            controller.focusNode(target.nodeId, isNote = isNote)
        }
        currentScreen = Screen.EDITOR
        app.ak25.pocketflow.domain.DeepLinkRouter.clearPendingDeepLink()
    }

    MaterialTheme(colorScheme = MinimalLightTheme) {
        // iOS gates auth natively (RootView swaps to the native LoginView); the
        // shared LoginScreen is Android-only.
        if (showOnboarding) {
            app.ak25.pocketflow.ui.onboarding.OnboardingScreen(
                onFinished = {
                    showOnboarding = false
                    app.ak25.pocketflow.storage.LocalStorage.saveString("onboarding_completed", "true")
                }
            )
        } else if (!authViewModel.isLoggedIn && app.ak25.pocketflow.platform.AuthBridgeHolder.current == null) {
            app.ak25.pocketflow.ui.auth.LoginScreen(
                viewModel = authViewModel,
                onGoogleSignInClick = {
                    val androidSignIn = app.ak25.pocketflow.platform.AndroidAuthBridge.onGoogleSignIn
                    if (androidSignIn != null) {
                        androidSignIn.invoke()
                    } else if (app.ak25.pocketflow.platform.AuthBridgeHolder.current != null) {
                        // iOS — handled by the native AuthViewModel through the bridge
                        appScope.launch { authViewModel.signInWithGoogle() }
                    } else {
                        // Android — open the Supabase OAuth URL, result handled in handleOAuthResult
                        uriHandler.openUri(authViewModel.getGoogleOAuthUrl())
                    }
                },
                onAppleSignInClick = {
                    if (app.ak25.pocketflow.platform.AuthBridgeHolder.current != null) {
                        // iOS — handled by the native AuthViewModel through the bridge
                        appScope.launch { authViewModel.signInWithApple() }
                    } else {
                        // Android — open the Supabase OAuth URL, result handled in handleOAuthResult
                        uriHandler.openUri(authViewModel.getAppleOAuthUrl())
                    }
                },
                onGuestClick = {
                    authViewModel.loginAsGuest()
                }
            )
        } else if (currentScreen == Screen.HOME || currentScreen == Screen.SETTINGS || currentScreen == Screen.ASSETS) {
            val coroutineScope = rememberCoroutineScope()
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            controller = controller,
                            engine = engine,
                            backgroundScope = appScope,
                            onNavigateToEditor = { currentScreen = Screen.EDITOR },
                            onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                            onNavigateToPaywall = { currentScreen = Screen.PAYWALL },
                            onPaywallStateChanged = { isPaywallOpen = it },
                            onSignOut = {
                                controller.clearLocalWorkflows()
                                appScope.launch {
                                    authViewModel.signOut()
                                }
                            },
                            onNavigateToLogin = {
                                authViewModel.requestSignIn()
                            }
                        )
                        1 -> app.ak25.pocketflow.ui.assets.AssetsScreen(
                            controller = controller
                        )
                        2 -> SettingsScreen(
                            onBack = { currentScreen = Screen.HOME },
                            onNavigateToPaywall = { currentScreen = Screen.PAYWALL },
                            onNavigateToRates = { currentScreen = Screen.RATES },
                            onNavigateToActivity = { currentScreen = Screen.ACTIVITY },
                            onShowOnboarding = { showOnboarding = true }
                        )
                    }
                }

                // Modern floating bottom navigation bar with transparent backside and reduced top spacing padding
                if (!isPaywallOpen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                    ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val totalWidth = maxWidth
                        val innerWidth = totalWidth - 8.dp
                        val tabWidth = innerWidth / 3

                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 12.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .padding(4.dp)
                            ) {
                                val fraction = pagerState.currentPageOffsetFraction
                                val page = pagerState.currentPage
                                val position = page + fraction

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(tabWidth)
                                        .offset(x = tabWidth * position)
                                        .background(Color(0xFFEFEFF4), RoundedCornerShape(28.dp))
                                )

                                Row(
                                     modifier = Modifier.fillMaxSize(),
                                     horizontalArrangement = Arrangement.SpaceEvenly,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     // Home Tab
                                     Box(
                                         modifier = Modifier
                                             .weight(1f)
                                             .fillMaxHeight()
                                             .clip(RoundedCornerShape(28.dp))
                                             .clickable(
                                                 interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                 indication = null
                                             ) {
                                                 coroutineScope.launch {
                                                     pagerState.animateScrollToPage(0)
                                                 }
                                             },
                                         contentAlignment = Alignment.Center
                                     ) {
                                         val isSelected = pagerState.currentPage == 0
                                         Icon(
                                             AppIcons.NavHome,
                                             contentDescription = "Home",
                                             tint = if (isSelected) Color.Black else Color.Gray,
                                             modifier = Modifier.size(22.dp)
                                         )
                                     }

                                     // Assets Tab
                                     Box(
                                         modifier = Modifier
                                             .weight(1f)
                                             .fillMaxHeight()
                                             .clip(RoundedCornerShape(28.dp))
                                             .clickable(
                                                 interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                 indication = null
                                             ) {
                                                 coroutineScope.launch {
                                                     pagerState.animateScrollToPage(1)
                                                 }
                                             },
                                         contentAlignment = Alignment.Center
                                     ) {
                                         val isSelected = pagerState.currentPage == 1
                                         Icon(
                                             AppIcons.NavAssets,
                                             contentDescription = "Assets",
                                             tint = if (isSelected) Color.Black else Color.Gray,
                                             modifier = Modifier.size(22.dp)
                                         )
                                     }

                                     // Settings Tab
                                     Box(
                                         modifier = Modifier
                                             .weight(1f)
                                             .fillMaxHeight()
                                             .clip(RoundedCornerShape(28.dp))
                                             .clickable(
                                                 interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                 indication = null
                                             ) {
                                                 coroutineScope.launch {
                                                     pagerState.animateScrollToPage(2)
                                                 }
                                             },
                                         contentAlignment = Alignment.Center
                                     ) {
                                          val isSelected = pagerState.currentPage == 2
                                          Icon(
                                              AppIcons.Settings,
                                              contentDescription = "Settings",
                                             tint = if (isSelected) Color.Black else Color.Gray,
                                             modifier = Modifier.size(22.dp)
                                         )
                                     }
                                 }
                            }
                        }
                    }
                }
            }
            }
        } else {
            when (currentScreen) {
                Screen.EDITOR -> {
                    EditorScreen(
                        controller = controller,
                        engine = engine,
                        backgroundScope = appScope,
                        onBack = { currentScreen = Screen.HOME }
                    )
                }
                Screen.PAYWALL -> {
                    PaywallScreen(
                        onDismiss = { currentScreen = Screen.HOME }
                    )
                }
                Screen.RATES -> {
                    app.ak25.pocketflow.ui.settings.ManageRatesScreen(
                        onBack = { currentScreen = Screen.SETTINGS }
                    )
                }
                Screen.ACTIVITY -> {
                    app.ak25.pocketflow.ui.settings.ActivityScreen(
                        onBack = { currentScreen = Screen.SETTINGS }
                    )
                }
                else -> {}
            }
        }
    }
}

