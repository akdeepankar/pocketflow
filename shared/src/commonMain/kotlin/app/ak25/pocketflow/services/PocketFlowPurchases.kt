package app.ak25.pocketflow.services

import app.ak25.pocketflow.models.NodeStatus
import app.ak25.pocketflow.models.NodeType
import app.ak25.pocketflow.models.Workflow
import app.ak25.pocketflow.models.WorkflowNode
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.VirtualCurrency
import com.revenuecat.purchases.kmp.models.VirtualCurrencies
import com.revenuecat.purchases.kmp.models.PurchasesError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

const val REVENUECAT_API_KEY_ANDROID = "goog_lpdDQaEQrgKUJLUypodeKYagukU"
const val REVENUECAT_API_KEY_IOS = "appl_LWSKjWzQSPESgyrFUjfAPhzWeEQ"
const val REVENUECAT_API_KEY = REVENUECAT_API_KEY_ANDROID
const val REVENUECAT_API_SECRET_KEY = "sk_jxfDTBfohPNieveOWVySgtZcepkmk"
const val REVENUECAT_PROJECT_ID = "proj1a0efa1d"
const val ENTITLEMENT_POCKETFLOW_CREDITS = "PocketFlow Credits"
const val VIRTUAL_CURRENCY_NAME = "CREDIT"

object PocketFlowPurchases {
    private var isConfigured = false
    private val scope = MainScope()

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    private val _hasCredits = MutableStateFlow(false)
    val hasCredits: StateFlow<Boolean> = _hasCredits.asStateFlow()

    private val _offerings = MutableStateFlow<Offerings?>(null)
    val offerings: StateFlow<Offerings?> = _offerings.asStateFlow()

    private val _virtualCurrencies = MutableStateFlow<VirtualCurrencies?>(null)
    val virtualCurrencies: StateFlow<VirtualCurrencies?> = _virtualCurrencies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun isMockModeEnabled(): Boolean {
        return app.ak25.pocketflow.storage.LocalStorage.loadString("use_mock_mode") == "true"
    }

    fun setMockModeEnabled(enabled: Boolean) {
        app.ak25.pocketflow.storage.LocalStorage.saveString("use_mock_mode", enabled.toString())
        if (enabled) {
            _hasCredits.value = true
            if (app.ak25.pocketflow.storage.LocalStorage.loadString("mock_credits_balance") == null) {
                app.ak25.pocketflow.storage.LocalStorage.saveString("mock_credits_balance", "1000")
            }
        } else {
            _hasCredits.value = false
            refreshCustomerInfo()
        }
    }

    fun getMockCreditsBalance(): Int {
        return app.ak25.pocketflow.storage.LocalStorage.loadString("mock_credits_balance")?.toIntOrNull() ?: 1000
    }

    fun saveMockCreditsBalance(balance: Int) {
        app.ak25.pocketflow.storage.LocalStorage.saveString("mock_credits_balance", balance.toString())
        syncCreditBalanceTags(balance)
    }

    /**
     * Synchronizes personalization tags (e.g. {{ credits | default: '0' }}) without firing IAM triggers.
     */
    fun syncCreditBalanceTags(balance: Int?) {
        if (balance == null) return
        val balanceStr = balance.toString()
        // Message personalization tags for OneSignal Liquid syntax:
        // e.g. {{ credits | default: '0' }}, {{ credits_count }}, {{ credits_balance }}
        app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("credits", balanceStr)
        app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("credits_count", balanceStr)
        app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("credit_count", balanceStr)
        app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("credits_balance", balanceStr)
        app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("credit_balance", balanceStr)
        app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("available_credits", balanceStr)

        if (balance >= 50) {
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("credits_less_than_50")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("low_credits")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("credits_low")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("action")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("has_low_credits", "false")
        }
    }

    /**
     * Evaluates credit balance specifically after a node run completes and credits are deducted.
     * Only triggers the In-App Message if balance is strictly less than 50.
     */
    fun evaluateCreditBalanceAfterNodeRun(balance: Int?) {
        if (balance == null) return
        val balanceStr = balance.toString()
        syncCreditBalanceTags(balance)

        if (balance < 50) {
            println("""
            [OneSignal-IAM] ══════════════════════════════════════════════════
            [OneSignal-IAM] ⚠️ Node Completed -> Low Credits Trigger Satisfied (balance = $balance < 50)!
            [OneSignal-IAM] Setting Trigger: 'credits_less_than_50' = 'true'
            [OneSignal-IAM] Setting Trigger: 'low_credits' = 'true'
            [OneSignal-IAM] Setting Trigger: 'credits_low' = 'true'
            [OneSignal-IAM] Setting Trigger: 'action' = 'low_credits'
            [OneSignal-IAM] Setting Trigger: 'node_completed' = 'true'
            [OneSignal-IAM] Setting Tag: 'has_low_credits' = 'true'
            [OneSignal-IAM] Setting Personalization Tag: 'credits' = '$balanceStr'
            [OneSignal-IAM] ══════════════════════════════════════════════════
            """.trimIndent())

            scope.launch {
                val triggerMap = mapOf(
                    "credits_less_than_50" to "true",
                    "low_credits" to "true",
                    "credits_low" to "true",
                    "action" to "low_credits",
                    "node_completed" to "true"
                )

                // Pulse 1: Immediate
                triggerMap.forEach { (k, v) ->
                    app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTrigger(k, v)
                }

                // Pulse 2: 600ms (ensures UI rendering has completed)
                delay(600)
                triggerMap.forEach { (k, v) ->
                    app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTrigger(k, v)
                }

                // Pulse 3: 1500ms
                delay(900)
                triggerMap.forEach { (k, v) ->
                    app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTrigger(k, v)
                }
            }
        } else {
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("credits_less_than_50")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("low_credits")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("credits_low")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.removeTrigger("action")
            app.ak25.pocketflow.platform.OneSignalBridgeHolder.addTag("has_low_credits", "false")
        }
    }

    fun purchaseMockCredits(amount: Int) {
        val current = getMockCreditsBalance()
        saveMockCreditsBalance(current + amount)
        _hasCredits.value = true
        app.ak25.pocketflow.storage.ActivityTracker.log(
            type = app.ak25.pocketflow.models.ActivityType.ADD_CREDITS,
            title = "Credits Added",
            details = "Added $amount credits successfully (Mock Purchase)."
        )
        // trigger updates
        _virtualCurrencies.value = null
    }

    /**
     * Configure RevenueCat SDK. Call this once during app startup.
     * In KMP 3.x the Android Context is injected automatically via AndroidX App Startup.
     */
    fun configure(apiKey: String? = null) {
        if (isConfigured) return
        val selectedKey = apiKey ?: run {
            val name = app.ak25.pocketflow.getPlatform().name.lowercase()
            if (name.contains("ios") || name.contains("iphone") || name.contains("ipad")) {
                REVENUECAT_API_KEY_IOS
            } else {
                REVENUECAT_API_KEY_ANDROID
            }
        }
        try {
            Purchases.configure(PurchasesConfiguration(selectedKey))
        } catch (e: Exception) {
            println("PocketFlowPurchases: Failed to configure RevenueCat SDK, enabling local mock mode: ${e.message}")
            setMockModeEnabled(true)
        }
        isConfigured = true
        
        // Ensure mock mode is initialized if active
        if (isMockModeEnabled()) {
            setMockModeEnabled(true)
        }
        
        // Sync initial credits personalization tags without firing IAM trigger
        syncCreditBalanceTags(getAvailableCreditsBalance())
    }

    private fun ensureConfigured() {
        if (!isConfigured) {
            configure()
        }
    }

    /**
     * Fetch current customer info and refresh entitlement status.
     */
    fun refreshCustomerInfo() {
        if (isMockModeEnabled()) {
            _hasCredits.value = true
            return
        }
        ensureConfigured()
        _isLoading.value = true
        Purchases.sharedInstance.getCustomerInfo(
            onError = { error: PurchasesError ->
                scope.launch {
                    _isLoading.value = false
                    _error.value = error.message
                    setMockModeEnabled(true)
                }
            },
            onSuccess = { info: CustomerInfo ->
                scope.launch {
                    _isLoading.value = false
                    _customerInfo.value = info
                    _hasCredits.value = info.entitlements.active[ENTITLEMENT_POCKETFLOW_CREDITS] != null
                    refreshVirtualCurrencies()
                }
            }
        )
    }

    /**
     * Fetch available offerings/products from RevenueCat.
     */
    fun fetchOfferings() {
        if (isMockModeEnabled()) {
            _offerings.value = null
            return
        }
        ensureConfigured()
        _isLoading.value = true
        Purchases.sharedInstance.getOfferings(
            onError = { error: PurchasesError ->
                scope.launch {
                    _isLoading.value = false
                    _error.value = error.message
                    setMockModeEnabled(true)
                }
            },
            onSuccess = { offerings: Offerings ->
                scope.launch {
                    _isLoading.value = false
                    _offerings.value = offerings
                }
            }
        )
    }

    /**
     * Fetch the customer's virtual currency balances.
     */
    fun refreshVirtualCurrencies() {
        refreshVirtualCurrencies(forceRefresh = false)
    }

    /**
     * Fetch the customer's virtual currency balances.
     * When forceRefresh is true, the cached values are invalidated first.
     */
    fun refreshVirtualCurrencies(forceRefresh: Boolean) {
        if (isMockModeEnabled()) return
        ensureConfigured()
        scope.launch {
            fetchVirtualCurrencies(forceRefresh)
        }
    }

    private fun refreshVirtualCurrenciesAfterDelay(delayMillis: Long = 750L) {
        scope.launch {
            delay(delayMillis)
            refreshVirtualCurrencies(forceRefresh = true)
        }
    }

    /**
     * Force-refresh virtual currencies multiple times to catch delayed backend updates.
     */
    fun refreshVirtualCurrenciesAfterCreditChange() {
        if (isMockModeEnabled()) return
        scope.launch {
            // Force refresh immediately
            fetchVirtualCurrencies(forceRefresh = true)
            // Poll rapidly every 500ms for 5 seconds (10 iterations) to reflect purchase immediately
            for (i in 1..10) {
                delay(500)
                fetchVirtualCurrencies(forceRefresh = true)
            }
        }
    }

    /**
     * After a generation run, poll forced refreshes until balance reflects the expected spend
     * (or at least changes from the previous value).
     */
    suspend fun refreshVirtualCurrenciesAfterRun(previousBalance: Int?, expectedDeduction: Int): Int? {
        if (isMockModeEnabled()) {
            return getMockCreditsBalance()
        }
        val targetBalance = previousBalance?.let { (it - expectedDeduction).coerceAtLeast(0) }
        val retryDelaysMs = listOf(0L, 1200L, 2500L, 5000L, 8000L, 12000L)
        var latestBalance: Int? = previousBalance

        retryDelaysMs.forEach { waitMs ->
            if (waitMs > 0) delay(waitMs)
            val currencies = fetchVirtualCurrencies(forceRefresh = true)
            latestBalance = getAvailableCreditsBalance(currencies)

            val prev = previousBalance
            if (prev == null) {
                if (latestBalance != null) return latestBalance
            } else {
                if (latestBalance != null && (latestBalance != prev || (targetBalance != null && latestBalance <= targetBalance))) {
                    return latestBalance
                }
            }
        }

        return latestBalance
    }

    suspend fun fetchVirtualCurrencies(forceRefresh: Boolean = false): VirtualCurrencies? {
        if (isMockModeEnabled()) return null
        ensureConfigured()
        if (forceRefresh) {
            Purchases.sharedInstance.invalidateVirtualCurrenciesCache()
        }
        return suspendCoroutine { continuation ->
            Purchases.sharedInstance.getVirtualCurrencies(
                onError = { error: PurchasesError ->
                    _error.value = error.message
                    continuation.resume(null)
                },
                onSuccess = { currencies: VirtualCurrencies ->
                    _virtualCurrencies.value = currencies
                    syncCreditBalanceTags(getAvailableCreditsBalance(currencies))
                    continuation.resume(currencies)
                }
            )
        }
    }

    /**
     * Prefer the configured current offering, but fall back to the first available
     * offering so the paywall still works when RevenueCat has no default offering.
     */
    fun getPreferredOffering(offerings: Offerings?): Offering? {
        if (isMockModeEnabled()) return null
        return offerings?.current ?: offerings?.all?.values?.firstOrNull()
    }

    /**
     * Purchase a specific package (consumable).
     * Passes the full Package so RevenueCat can track offering/experiment context.
     */
    fun purchaseProduct(
        rcPackage: Package,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (String) -> Unit,
        onUserCancelled: () -> Unit = {}
    ) {
        ensureConfigured()
        _isLoading.value = true
        Purchases.sharedInstance.purchase(
            packageToPurchase = rcPackage,
            onError = { error: PurchasesError, userCancelled: Boolean ->
                scope.launch {
                    _isLoading.value = false
                    if (userCancelled) {
                        onUserCancelled()
                    } else {
                        _error.value = error.message
                        onError(error.message)
                    }
                }
            },
            onSuccess = { _: com.revenuecat.purchases.kmp.models.StoreTransaction, customerInfo: CustomerInfo ->
                scope.launch {
                    _isLoading.value = false
                    _customerInfo.value = customerInfo
                    _hasCredits.value = customerInfo.entitlements.active[ENTITLEMENT_POCKETFLOW_CREDITS] != null
                    app.ak25.pocketflow.storage.ActivityTracker.log(
                        type = app.ak25.pocketflow.models.ActivityType.ADD_CREDITS,
                        title = "Credits Purchased",
                        details = "Successfully purchased package: ${rcPackage.storeProduct.title}"
                    )
                    refreshVirtualCurrenciesAfterCreditChange()
                    onSuccess(customerInfo)
                }
            }
        )
    }

    /**
     * Restore purchases for the current user.
     */
    fun restorePurchases(
        onSuccess: (CustomerInfo) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isMockModeEnabled()) {
            val lastInfo = _customerInfo.value
            if (lastInfo != null) {
                onSuccess(lastInfo)
            } else {
                onError("No active sandbox session to restore.")
            }
            return
        }
        ensureConfigured()
        _isLoading.value = true
        Purchases.sharedInstance.restorePurchases(
            onError = { error: PurchasesError ->
                scope.launch {
                    _isLoading.value = false
                    _error.value = error.message
                    onError(error.message)
                }
            },
            onSuccess = { customerInfo: CustomerInfo ->
                scope.launch {
                    _isLoading.value = false
                    _customerInfo.value = customerInfo
                    val active = customerInfo.entitlements.active[ENTITLEMENT_POCKETFLOW_CREDITS] != null
                    _hasCredits.value = active
                    if (active) {
                        app.ak25.pocketflow.storage.ActivityTracker.log(
                            type = app.ak25.pocketflow.models.ActivityType.ADD_CREDITS,
                            title = "Purchases Restored",
                            details = "Restored active subscriptions/credits successfully."
                        )
                    }
                    refreshVirtualCurrenciesAfterCreditChange()
                    onSuccess(customerInfo)
                }
            }
        )
    }

    /**
     * Check if the user currently has the PocketFlow Credits entitlement.
     */
    fun hasActiveCreditsEntitlement(): Boolean {
        if (isMockModeEnabled()) return true
        return _hasCredits.value
    }

    /**
     * Return the first available virtual currency, preferring a PocketFlow-named code.
     */
    fun getPrimaryVirtualCurrency(virtualCurrencies: VirtualCurrencies? = _virtualCurrencies.value): VirtualCurrency? {
        return virtualCurrencies?.all?.get(ENTITLEMENT_POCKETFLOW_CREDITS)
            ?: virtualCurrencies?.all?.values?.firstOrNull()
    }

    /**
     * Return a short label for the user's available credits, if known.
     */
    fun getAvailableCreditsLabel(virtualCurrencies: VirtualCurrencies? = _virtualCurrencies.value): String? {
        if (isMockModeEnabled()) {
            val balance = getMockCreditsBalance()
            val suffix = if (balance == 1) "credit" else "credits"
            return "$balance $suffix"
        }
        val currency = getPrimaryVirtualCurrency(virtualCurrencies) ?: return null
        val suffix = if (currency.balance == 1) "credit" else "credits"
        return "${currency.balance} $suffix"
    }

    /**
     * Return the available credit balance for the primary virtual currency.
     */
    fun getAvailableCreditsBalance(virtualCurrencies: VirtualCurrencies? = _virtualCurrencies.value): Int? {
        if (isMockModeEnabled()) {
            return getMockCreditsBalance()
        }
        return getPrimaryVirtualCurrency(virtualCurrencies)?.balance
    }

    /**
     * Estimate the credit cost for a single node run.
     */
    fun estimateNodeCredits(node: WorkflowNode): Int {
        return when (node.type) {
            NodeType.TEXT_PROMPT, NodeType.UPLOADED_IMAGE -> 0
            NodeType.IMAGE_GENERATION -> {
                val model = node.params["model"] ?: "gemini_image3_pro"
                if (model == "gpt_image_2") {
                    val quality = node.params["quality"] ?: "medium"
                    when (quality) {
                        "low" -> NodeCreditRatesManager.getRate("gpt_image_2_low", 2)
                        "high" -> NodeCreditRatesManager.getRate("gpt_image_2_high", 30)
                        else -> NodeCreditRatesManager.getRate("gpt_image_2_medium", 8)
                    }
                } else {
                    val res = node.params["resolution"] ?: "1K"
                    when (res) {
                        "2K" -> NodeCreditRatesManager.getRate("gemini_image3_pro_2K", 30)
                        "4K" -> NodeCreditRatesManager.getRate("gemini_image3_pro_4K", 60)
                        else -> NodeCreditRatesManager.getRate("gemini_image3_pro_1K", 30)
                    }
                }
            }
            NodeType.IMAGE_TO_VIDEO -> {
                val model = node.params["model"] ?: "veo3.1_fast"
                val audio = (node.params["audio"] ?: "true").toBoolean()
                val duration = node.params["duration"]?.toIntOrNull() ?: 5
                
                val ratePerSec = when (model) {
                    "veo3.1" -> {
                        if (audio) NodeCreditRatesManager.getRate("veo_3.1_audio", 60)
                        else NodeCreditRatesManager.getRate("veo_3.1_no_audio", 30)
                    }
                    "seedance2" -> {
                        NodeCreditRatesManager.getRate("seedance_2_1080p", 60)
                    }
                    else -> {
                        if (audio) NodeCreditRatesManager.getRate("veo_3.1_fast_audio", 22)
                        else NodeCreditRatesManager.getRate("veo_3.1_fast_no_audio", 15)
                    }
                }
                ratePerSec * duration
            }
            NodeType.PRODUCT_AD -> {
                val res = node.params["resolution"] ?: "720p"
                val duration = node.params["duration"]?.toIntOrNull() ?: 10
                val rate = if (res == "1080p") NodeCreditRatesManager.getRate("product_ad_1080p", 150)
                           else NodeCreditRatesManager.getRate("product_ad_720p", 140)
                rate * duration
            }
            NodeType.PRODUCT_SWAP -> {
                val res = node.params["resolution"] ?: "720p"
                val duration = node.params["duration"]?.toIntOrNull() ?: 10
                val rate = if (res == "1080p") NodeCreditRatesManager.getRate("product_swap_1080p", 160)
                           else NodeCreditRatesManager.getRate("product_swap_720p", 150)
                rate * duration
            }
            NodeType.PRODUCT_UGC -> {
                val res = node.params["resolution"] ?: "720p"
                val duration = node.params["duration"]?.toIntOrNull() ?: 15
                val rate = if (res == "1080p") NodeCreditRatesManager.getRate("product_ugc_1080p", 140)
                           else NodeCreditRatesManager.getRate("product_ugc_720p", 130)
                rate * duration
            }
            NodeType.MULTI_SHOT_VIDEO -> {
                val res = node.params["resolution"] ?: "720p"
                val duration = node.params["duration"]?.toIntOrNull() ?: 10
                val ratePerSec = if (res == "1080p") NodeCreditRatesManager.getRate("multi_shot_video_1080p", 26)
                                 else NodeCreditRatesManager.getRate("multi_shot_video_720p", 20)
                ratePerSec * duration
            }
            NodeType.AD_LOCALIZATION -> NodeCreditRatesManager.getRate("ad_localization", 18)
            NodeType.MARKETING_STOCK_IMAGE -> {
                val quality = (node.params["quality"] ?: "medium").lowercase()
                val count = node.params["outputCount"]?.toIntOrNull() ?: 4
                val finalQuality = if (quality == "low" || quality == "high") quality else "medium"
                val finalCount = count.coerceIn(1, 4)
                
                val defaultVal = when (finalCount) {
                    1 -> if (finalQuality == "low") 21 else if (finalQuality == "high") 35 else 24
                    2 -> if (finalQuality == "low") 22 else if (finalQuality == "high") 50 else 28
                    3 -> if (finalQuality == "low") 23 else if (finalQuality == "high") 65 else 32
                    else -> if (finalQuality == "low") 24 else if (finalQuality == "high") 80 else 36
                }
                
                NodeCreditRatesManager.getRate("marketing_stock_${finalCount}_$finalQuality", defaultVal)
            }
            NodeType.PRODUCT_CAMPAIGN -> NodeCreditRatesManager.getRate("product_campaign", 100)
            else -> 1 // 1 credit for other node types as default
        }
    }

    /**
     * Estimate the credit cost for a workflow run.
     */
    fun estimateWorkflowCredits(workflow: Workflow?, onlyEmpty: Boolean = false): Int {
        if (workflow == null) return 0
        if (!onlyEmpty) {
            return workflow.nodes.sumOf { estimateNodeCredits(it) }
        }
        return getNodesNeedingRun(workflow).sumOf { estimateNodeCredits(it) }
    }

    /**
     * Determine which generative nodes need to be run in a branch-wise empty run.
     */
    fun getNodesNeedingRun(workflow: Workflow?): List<WorkflowNode> {
        if (workflow == null) return emptyList()
        val inDegree = mutableMapOf<String, Int>()
        val adjList = mutableMapOf<String, MutableList<String>>()
        workflow.nodes.forEach { inDegree[it.id] = 0 }
        workflow.edges.forEach {
            inDegree[it.targetNodeId] = (inDegree[it.targetNodeId] ?: 0) + 1
            adjList.getOrPut(it.sourceNodeId) { mutableListOf() }.add(it.targetNodeId)
        }
        val queue = mutableListOf<String>()
        inDegree.forEach { (id, deg) -> if (deg == 0) queue.add(id) }
        val sorted = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            sorted.add(current)
            adjList[current]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 1) - 1
                if (inDegree[neighbor] == 0) queue.add(neighbor)
            }
        }
        val nodesMap = workflow.nodes.associateBy { it.id }
        val needRunSet = mutableSetOf<String>()
        for (nodeId in sorted) {
            val node = nodesMap[nodeId] ?: continue
            if (node.type == NodeType.TEXT_PROMPT) continue
            val isEmpty = node.outputUrl.isNullOrEmpty() || node.status != NodeStatus.COMPLETED
            val upstreamRan = workflow.edges.any { it.targetNodeId == nodeId && it.sourceNodeId in needRunSet }
            if (isEmpty || upstreamRan) {
                needRunSet.add(nodeId)
            }
        }
        return workflow.nodes.filter { it.id in needRunSet }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Deduct credits from the customer's account via RevenueCat API.
     */
    suspend fun deductCredits(amount: Int, currencyName: String = VIRTUAL_CURRENCY_NAME): Boolean {
        if (isMockModeEnabled()) {
            val current = getMockCreditsBalance()
            val newBalance = (current - amount).coerceAtLeast(0)
            saveMockCreditsBalance(newBalance)
            // Trigger flow update
            _virtualCurrencies.value = null
            evaluateCreditBalanceAfterNodeRun(newBalance)
            return true
        }
        val customerId = Purchases.sharedInstance.appUserID
        if (customerId.isEmpty()) {
            _error.value = "Customer ID not available"
            return false
        }

        // Get available currencies to debug
        val currencies = _virtualCurrencies.value
        if (currencies != null) {
            println("DEBUG: Available currencies: ${currencies.all.keys}")
            currencies.all.forEach { (name, currency) ->
                println("DEBUG: Currency '$name' -> ${currency.balance}")
            }
        }

        return try {
            val httpClient = HttpClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            // URL encode customer ID - replace special characters
            val encodedCustomerId = customerId
                .replace("$", "%24")
                .replace(":", "%3A")
                .replace("/", "%2F")
            val url = "https://api.revenuecat.com/v2/projects/$REVENUECAT_PROJECT_ID/customers/$encodedCustomerId/virtual_currencies/transactions"
            val body = buildJsonObject {
                put("adjustments", buildJsonObject {
                    put(currencyName, -amount)
                })
            }

            println("DEBUG: Deducting $amount credits for customer $customerId")
            println("DEBUG: Encoded customer ID: $encodedCustomerId")
            println("DEBUG: Using currency name: '$currencyName'")
            println("DEBUG: URL: $url")
            println("DEBUG: Body: $body")

            val idempotencyKey = generateIdempotencyKey()
            println("DEBUG: Idempotency-Key: $idempotencyKey")
            val response = httpClient.post(url) {
                headers {
                    append("Authorization", "Bearer $REVENUECAT_API_SECRET_KEY")
                    append("Content-Type", "application/json")
                    append("Idempotency-Key", idempotencyKey)
                }
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            println("DEBUG: Response status: ${response.status.value}")
            httpClient.close()
            
            val success = response.status.value in 200..299
            if (!success) {
                _error.value = "Credit deduction failed: HTTP ${response.status.value}"
            }
            success
        } catch (e: Exception) {
            println("DEBUG: Exception in deductCredits: ${e.message}")
            e.printStackTrace()
            _error.value = "Failed to deduct credits: ${e.message}"
            false
        }
    }

    fun getAppUserID(): String {
        if (isMockModeEnabled()) return "Sandbox-User"
        return if (isConfigured) Purchases.sharedInstance.appUserID else "Not Configured"
    }
}

/**
 * Generates a UUID v4 string using pure Kotlin common code.
 * Used as an idempotency key so each Virtual Currency transaction is
 * executed at most once by RevenueCat, even if the request is retried.
 */
fun generateIdempotencyKey(): String {
    val bytes = Random.nextBytes(16)
    // Set version 4 bits (0100xxxx) in byte 6
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    // Set variant bits (10xxxxxx) in byte 8
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val h = bytes.joinToString("") { it.toInt().and(0xFF).toString(16).padStart(2, '0') }
    return "${h.substring(0,8)}-${h.substring(8,12)}-${h.substring(12,16)}-${h.substring(16,20)}-${h.substring(20)}"
}
