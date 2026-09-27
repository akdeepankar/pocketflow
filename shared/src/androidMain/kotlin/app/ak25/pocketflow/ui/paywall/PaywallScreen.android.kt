package app.ak25.pocketflow.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.ak25.pocketflow.services.PocketFlowPurchases
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions

@Composable
actual fun PaywallScreen(onDismiss: () -> Unit) {
    val paywallOptions = remember {
        PaywallOptions(
            dismissRequest = {
                onDismiss()
                PocketFlowPurchases.refreshVirtualCurrenciesAfterCreditChange()
            }
        )
    }
    Paywall(options = paywallOptions)
}
