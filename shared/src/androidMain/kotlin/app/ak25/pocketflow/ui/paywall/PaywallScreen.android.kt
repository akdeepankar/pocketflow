package app.ak25.pocketflow.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import app.ak25.pocketflow.services.PocketFlowPurchases
import app.ak25.pocketflow.ui.editor.AppIcons

@Composable
actual fun PaywallScreen(onDismiss: () -> Unit) {
    RevenueCatPaywall(onDismiss = onDismiss)
}

@Composable
fun RevenueCatPaywall(onDismiss: () -> Unit) {
    val offerings by PocketFlowPurchases.offerings.collectAsState()
    val virtualCurrencies by PocketFlowPurchases.virtualCurrencies.collectAsState()
    val isLoading by PocketFlowPurchases.isLoading.collectAsState()
    val error by PocketFlowPurchases.error.collectAsState()
    var purchaseResult by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        PocketFlowPurchases.fetchOfferings()
        PocketFlowPurchases.refreshVirtualCurrencies()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Color.White,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(20.dp))

            // Title
            Text(
                text = "PocketFlow Credits",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Power your AI generations with credits",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            PocketFlowPurchases.getAvailableCreditsLabel(virtualCurrencies)?.let { credits ->
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Available credits: $credits",
                        color = Color(0xFF1A1A1A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // Features
            listOf(
                Triple(AppIcons.Video, "AI Video Generation", "Create stunning videos with Runway ML"),
                Triple(AppIcons.Image, "Image Generation", "Generate images with Google Gemini"),
                Triple(AppIcons.Audio, "Audio Processing", "Text-to-speech and audio analysis")
            ).forEach { (icon, title, subtitle) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF6C63FF).copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = Color(0xFF6C63FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(text = subtitle, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Products from RC offerings
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF6C63FF))
                Spacer(Modifier.height(16.dp))
            } else {
                if (PocketFlowPurchases.isMockModeEnabled()) {
                    Text(
                        text = "Local Sandbox Active",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    listOf(
                        "100 Credits" to 100,
                        "500 Credits" to 500,
                        "1000 Credits" to 1000
                    ).forEach { (label, credits) ->
                        Button(
                            onClick = {
                                PocketFlowPurchases.purchaseMockCredits(credits)
                                purchaseResult = "Purchase successful! $credits credits added."
                                toastMessage = "Purchase successful! $credits credits added."
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Purchase $label – Mock",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    val currentOffering = PocketFlowPurchases.getPreferredOffering(offerings)
                    if (currentOffering?.availablePackages?.isNotEmpty() == true) {
                        currentOffering.availablePackages.forEach { pkg ->
                            val storeProduct = pkg.storeProduct
                            Button(
                                onClick = {
                                    PocketFlowPurchases.purchaseProduct(
                                        rcPackage = pkg,
                                        onSuccess = { _ ->
                                            purchaseResult = "Purchase successful! Credits added."
                                            toastMessage = "Purchase successful! Credits added."
                                        },
                                        onError = { msg ->
                                            purchaseResult = "Purchase failed: $msg"
                                        },
                                        onUserCancelled = {}
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6C63FF)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = "${storeProduct.title} – ${storeProduct.price.formatted}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        // Fallback if no offering configured yet
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "No products available yet.\nConfigure your offering in the RevenueCat dashboard.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Purchase result message
            purchaseResult?.let { result ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = result,
                    color = if (result.startsWith("Purchase successful")) Color(0xFF43A047) else Color(0xFFE53935),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Error: $err",
                    color = Color(0xFFE53935),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { PocketFlowPurchases.clearError() }) {
                    Text("Dismiss", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Restore purchases
            TextButton(
                onClick = {
                    PocketFlowPurchases.restorePurchases(
                        onSuccess = { _ -> purchaseResult = "Purchases restored!" },
                        onError = { msg -> purchaseResult = "Restore failed: $msg" }
                    )
                }
            ) {
                Text("Restore Purchases", color = Color.Gray, fontSize = 12.sp)
            }

            // Dismiss
            TextButton(onClick = onDismiss) {
                Text("Not now", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
        }

        if (toastMessage != null) {
            LaunchedEffect(toastMessage) {
                kotlinx.coroutines.delay(2500)
                toastMessage = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 24.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .background(Color(0xFF34C759), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(toastMessage!!, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}
