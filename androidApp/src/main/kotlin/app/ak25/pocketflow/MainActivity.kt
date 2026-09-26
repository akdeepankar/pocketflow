package app.ak25.pocketflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.ak25.pocketflow.services.PurchasesInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.MainScope

import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class MainActivity : ComponentActivity() {
    private val mainScope = MainScope()
    private var pushObserver: com.onesignal.user.subscriptions.IPushSubscriptionObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app.ak25.pocketflow.storage.AndroidStorageContext.applicationContext = applicationContext

        // Initialize RevenueCat SDK
        PurchasesInitializer.configure(context = applicationContext)

        // Initialize OneSignal SDK (v5)
        OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.initWithContext(this, "7090ae90-1a87-4702-8cfd-2694e44301d9")

        // Retain observer as an Activity field to prevent premature garbage collection
        pushObserver = object : com.onesignal.user.subscriptions.IPushSubscriptionObserver {
            override fun onPushSubscriptionChange(state: com.onesignal.user.subscriptions.PushSubscriptionChangedState) {
                evaluateSubscription(state.current.id)
            }
        }
        OneSignal.User.pushSubscription.addObserver(pushObserver!!)

        // Listen for notification clicks and route deep links
        OneSignal.Notifications.addClickListener(object : com.onesignal.notifications.INotificationClickListener {
            override fun onClick(event: com.onesignal.notifications.INotificationClickEvent) {
                val customData = event.notification.additionalData
                println("[OneSignal-Android] 🔔 Notification clicked! data: $customData")
                if (customData != null) {
                    val workflowId = customData.optString("workflow_id", "")
                    val nodeId = customData.optString("node_id", "")
                    val type = customData.optString("type", "")
                    if (workflowId.isNotEmpty()) {
                        app.ak25.pocketflow.domain.DeepLinkRouter.onNotificationClicked(
                            workflowId = workflowId,
                            nodeId = nodeId.ifEmpty { null },
                            type = type.ifEmpty { null }
                        )
                    }
                }
            }
        })

        // Evaluate immediately on initialization
        evaluateSubscription(OneSignal.User.pushSubscription.id)

        // Auto-login existing user
        val storedUid = (app.ak25.pocketflow.storage.LocalStorage.loadString("supabase_user_id")
            ?: app.ak25.pocketflow.storage.LocalStorage.loadString("appwrite_user_id")).orEmpty()
        if (storedUid.isNotEmpty()) {
            OneSignal.login(storedUid)
            OneSignal.User.addAlias("external_id", storedUid)
            OneSignal.User.pushSubscription.optIn()
            println("[OneSignal-Android] 👤 Auto logged in stored user: $storedUid")
        }

        // Hook up AuthBridge for runtime login/logout
        app.ak25.pocketflow.platform.AndroidAuthBridge.onOneSignalLogin = { userId ->
            if (userId.isNotEmpty()) {
                OneSignal.login(userId)
                OneSignal.User.addAlias("external_id", userId)
                OneSignal.User.pushSubscription.optIn()
                println("[OneSignal-Android] 👤 Explicit login called for external_id: $userId")
            }
        }

        app.ak25.pocketflow.platform.AndroidAuthBridge.onOneSignalLogout = {
            OneSignal.logout()
            println("[OneSignal-Android] 👤 User logged out from OneSignal")
        }

        // Register the native Google Sign-in handler
        app.ak25.pocketflow.platform.AndroidAuthBridge.onGoogleSignIn = {
            launchNativeGoogleSignIn()
        }

        handleIntent(intent)

        setContent {
            App()
        }
    }

    private fun launchNativeGoogleSignIn() {
        val serverClientId = "295537294686-2flmchuj5pvr9322oh74aj53srcb42if.apps.googleusercontent.com"
        val credentialManager = CredentialManager.create(this)
        
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .addCredentialOption(googleIdOption)
            .build()

        mainScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@MainActivity,
                    request = request
                )
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    
                    // Call the shared ViewModel method to import this identity session
                    app.ak25.pocketflow.ui.auth.SharedAuthViewModel.signInWithGoogleIdToken(idToken)
                } else {
                    println("[RealtimeDebug] ❌ Native Google Sign-in returned unexpected credential type, falling back to browser")
                    launchBrowserGoogleOAuth()
                }
            } catch (e: Exception) {
                println("[RealtimeDebug] ❌ Native Google Sign-in failed (${e.message}), falling back to browser OAuth...")
                launchBrowserGoogleOAuth()
            }
        }
    }

    private fun launchBrowserGoogleOAuth() {
        try {
            val oAuthUrl = app.ak25.pocketflow.ui.auth.SharedAuthViewModel.getGoogleOAuthUrl()
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oAuthUrl)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
        } catch (e: Exception) {
            println("[RealtimeDebug] ❌ Failed to launch browser Google OAuth: ${e.message}")
        }
    }

    private fun evaluateSubscription(subscriptionId: String?) {
        val subId = subscriptionId ?: return
        if (subId.isEmpty() || subId.startsWith("local-")) return
        
        app.ak25.pocketflow.storage.LocalStorage.saveString("onesignal_subscription_id", subId)
        println("[OneSignal-Android] ✅ Registered server-assigned push subscription ID: $subId")
        showVerificationAlertIfNeeded()
    }

    private fun showVerificationAlertIfNeeded() {
        val key = "OneSignalAlertShown"
        val store = app.ak25.pocketflow.storage.LocalStorage
        if (store.loadString(key) == "true") return
        store.saveString(key, "true")

        runOnUiThread {
            android.app.AlertDialog.Builder(this)
                .setTitle("Your OneSignal SDK integration is complete!")
                .setMessage("You can now send Push Notifications & In-App Messages through OneSignal. Tap below to enable push notifications.")
                .setPositiveButton("Got it") { _, _ ->
                    mainScope.launch {
                        val accepted = OneSignal.Notifications.requestPermission(false)
                        println("[OneSignal-Android] 🔔 Push permission result: $accepted")
                    }
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent != null) {
            val workflowId = intent.getStringExtra("workflow_id")
            if (!workflowId.isNullOrEmpty()) {
                val nodeId = intent.getStringExtra("node_id")
                val type = intent.getStringExtra("type")
                println("[MainActivity] 🔗 Processing intent deep link: workflowId=$workflowId, nodeId=$nodeId, type=$type")
                app.ak25.pocketflow.domain.DeepLinkRouter.onNotificationClicked(
                    workflowId = workflowId,
                    nodeId = nodeId,
                    type = type
                )
            }
        }
        val data = intent?.data ?: return
        if (data.scheme == "supabase-callback-pocketflow") {
            val urlString = data.toString()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                app.ak25.pocketflow.ui.auth.SharedAuthViewModel.handleSupabaseOAuthCallback(urlString)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}