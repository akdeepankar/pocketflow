package app.ak25.pocketflow.services

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

object PurchasesInitializer {
    /**
     * Configure RevenueCat on iOS.
     * Uses the KMP 3.x DSL-style configuration.
     */
    fun configure(apiKey: String = REVENUECAT_API_KEY) {
        Purchases.configure(PurchasesConfiguration(apiKey))
    }
}
