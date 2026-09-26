package app.ak25.pocketflow.services

import android.content.Context
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

object PurchasesInitializer {
    /**
     * Configure RevenueCat on Android.
     * In KMP 3.x, the Android Context is injected automatically via AndroidX App Startup.
     * The context parameter is kept for backwards compatibility but is not passed to RC directly.
     */
    fun configure(context: Context, apiKey: String = REVENUECAT_API_KEY) {
        Purchases.configure(PurchasesConfiguration(apiKey))
    }
}
