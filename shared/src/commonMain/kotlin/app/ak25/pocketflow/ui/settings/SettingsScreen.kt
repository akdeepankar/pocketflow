package app.ak25.pocketflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import pocketflow.shared.generated.resources.Res
import pocketflow.shared.generated.resources.EduAUVICWANTHand
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.ui.editor.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    onNavigateToRates: () -> Unit = {},
    onNavigateToActivity: () -> Unit = {},
    onShowOnboarding: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val customerInfo by app.ak25.pocketflow.services.PocketFlowPurchases.customerInfo.collectAsState()
    val isLoading by app.ak25.pocketflow.services.PocketFlowPurchases.isLoading.collectAsState()
    var restoreMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        app.ak25.pocketflow.services.PocketFlowPurchases.refreshCustomerInfo()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, bottom = 112.dp, top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "settings",
                fontFamily = FontFamily(org.jetbrains.compose.resources.Font(Res.font.EduAUVICWANTHand)),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // About
            SettingsSectionHeader("About")
            SettingsCard {
                SettingsRow(
                    icon = AppIcons.Info,
                    iconTint = Color(0xFF0EA5E9),
                    title = "PocketFlow",
                    subtitle = "Version ${app.ak25.pocketflow.getPlatform().appVersion}"
                )
            }



            // Storage
            SettingsSectionHeader("Storage")
            SettingsCard {
                val isGuest = app.ak25.pocketflow.ui.auth.SharedAuthViewModel.isGuest
                if (isGuest) {
                    SettingsRow(
                        icon = AppIcons.StorageIcon,
                        iconTint = Color(0xFF43A047),
                        title = "Data Storage",
                        subtitle = "Local · stored only on this device"
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = AppIcons.LockIcon,
                        iconTint = Color(0xFF43A047),
                        title = "Guest Mode",
                        subtitle = "Sign in to sync your workflows to the cloud"
                    )
                } else {
                    SettingsRow(
                        icon = AppIcons.CloudIcon,
                        iconTint = Color(0xFF43A047),
                        title = "Cloud Sync",
                        subtitle = "Workflows, edits & uploaded images sync to the PocketFlow cloud"
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = AppIcons.LockIcon,
                        iconTint = Color(0xFF43A047),
                        title = "Data Privacy",
                        subtitle = "Only your account can access your synced data"
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Collaboration
            SettingsSectionHeader("Collaboration")
            SettingsCard {
                val isGuest = app.ak25.pocketflow.ui.auth.SharedAuthViewModel.isGuest
                SettingsRow(
                    icon = AppIcons.Share,
                    iconTint = Color(0xFF8E24AA),
                    title = "Share Workflows",
                    subtitle = if (isGuest) "Sign in to share via a join code" else "Invite teammates with a 5-character join code"
                )
                SettingsDivider()
                SettingsRow(
                    icon = AppIcons.Refresh,
                    iconTint = Color(0xFF0EA5E9),
                    title = "Real-time Sync",
                    subtitle = "Edits to a shared workflow appear live for everyone"
                )
                SettingsDivider()
                SettingsRow(
                    icon = AppIcons.Compass,
                    iconTint = Color(0xFFFF9800),
                    title = "Live Presence",
                    subtitle = "See who's online and which node each member is editing"
                )
            }

            Spacer(Modifier.height(4.dp))

            // Workflow
            SettingsSectionHeader("Workflow")
            SettingsCard {
                SettingsRow(
                    icon = AppIcons.GridIcon,
                    iconTint = Color(0xFFFF9800),
                    title = "Canvas Grid",
                    subtitle = "Visible"
                )
                SettingsDivider()
                SettingsRow(
                    icon = AppIcons.TouchIcon,
                    iconTint = Color(0xFFFF9800),
                    title = "Gesture Navigation",
                    subtitle = "Pinch to zoom · Pan to scroll"
                )
            }

            Spacer(Modifier.height(4.dp))

            // Credits & Subscriptions
            SettingsSectionHeader("Credits & Subscriptions")
            SettingsCard {
                SettingsRow(
                    icon = AppIcons.Info,
                    iconTint = Color(0xFF0EA5E9),
                    title = "Activity History",
                    subtitle = "View detailed usage and credit spend logs",
                    showArrow = true,
                    onClick = onNavigateToActivity
                )
                SettingsDivider()
                SettingsRow(
                    icon = AppIcons.Settings,
                    iconTint = Color(0xFF0EA5E9),
                    title = "Credit Deductions",
                    subtitle = "Manage credit costs for each node type",
                    showArrow = true,
                    onClick = onNavigateToRates
                )
            }

            Spacer(Modifier.height(4.dp))

            // Billing & Subscriptions
            SettingsSectionHeader("Billing & Subscriptions")
            SettingsCard {
                val activeEntitlements = customerInfo?.entitlements?.active?.keys?.joinToString(", ") ?: "None"
                SettingsRow(
                    icon = AppIcons.LockIcon,
                    iconTint = Color(0xFF43A047),
                    title = "Active Purchases",
                    subtitle = if (activeEntitlements.isEmpty() || activeEntitlements == "None") "No active subscriptions or credits" else activeEntitlements
                )
                SettingsDivider()
                SettingsRow(
                    icon = AppIcons.ChevronRight,
                    iconTint = Color(0xFF0EA5E9),
                    title = "Restore Purchases",
                    subtitle = "To restore your credits, please purchase a minimum credit plan to restore the credits along with it.",
                    showArrow = false
                )
            }

            restoreMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { restoreMessage = null }) {
                            Text("Dismiss", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Help & Onboarding
            SettingsSectionHeader("Help")
            SettingsCard {
                SettingsRow(
                    icon = AppIcons.Info,
                    iconTint = Color(0xFF0EA5E9),
                    title = "Show Onboarding",
                    subtitle = "Replay the interactive setup tutorial",
                    showArrow = true,
                    onClick = onShowOnboarding
                )
                SettingsDivider()
                SettingsRow(
                    icon = AppIcons.TwitterX,
                    iconTint = Color(0xFF000000),
                    title = "Twitter / X",
                    subtitle = "@ak_deepankar",
                    showArrow = true,
                    onClick = {
                        uriHandler.openUri("https://x.com/ak_deepankar")
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = AppIcons.Mail,
                    iconTint = Color(0xFFEA4335),
                    title = "Email Support",
                    subtitle = "akdeepaknyc@gmail.com",
                    showArrow = true,
                    onClick = {
                        val userId = app.ak25.pocketflow.services.PocketFlowPurchases.getAppUserID()
                        uriHandler.openUri("mailto:akdeepaknyc@gmail.com?subject=PocketFlow%20Support%20-%20$userId&body=User%20ID:%20$userId%0A%0APlease%20describe%20your%20issue%20below:%0A")
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Gray,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp),
        letterSpacing = 1.sp
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        thickness = 0.5.dp
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        if (showArrow) {
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
