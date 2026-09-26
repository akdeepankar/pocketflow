package app.ak25.pocketflow.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ak25.pocketflow.platform.AuthBridgeHolder
import app.ak25.pocketflow.storage.LocalStorage
import app.ak25.pocketflow.services.supabaseClient
import app.ak25.pocketflow.services.SupabaseRepository
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.Apple
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.parseSessionFromUrl
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import app.ak25.pocketflow.getPlatform


object SharedAuthViewModel {

    var isLoggedIn by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        set

    var userName by mutableStateOf(LocalStorage.loadString("user_name") ?: "")
        private set

    var userEmail by mutableStateOf(LocalStorage.loadString("user_email") ?: "")
        private set

    var isGuest by mutableStateOf(LocalStorage.loadString("is_guest") == "true")
        private set

    private fun setGuestState(guest: Boolean) {
        LocalStorage.saveString("is_guest", if (guest) "true" else "")
        isGuest = guest
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    init {
        val jwt = LocalStorage.loadString("supabase_jwt") ?: LocalStorage.loadString("appwrite_jwt") ?: ""
        val sessionId = LocalStorage.loadString("supabase_session_id") ?: LocalStorage.loadString("appwrite_session_id") ?: ""
        val isGuest = LocalStorage.loadString("is_guest") == "true"

        isLoggedIn = jwt.isNotEmpty() || sessionId.isNotEmpty() || isGuest
        userName = LocalStorage.loadString("user_name") ?: ""
        userEmail = LocalStorage.loadString("user_email") ?: ""
        LocalStorage.saveString("sign_out_requested", "")

        if (!isLoggedIn && AuthBridgeHolder.current == null && !getPlatform().name.contains("iOS", ignoreCase = true)) {
            setGuestState(true)
            LocalStorage.saveString("user_name", "Guest")
            isLoggedIn = true
        }


        if (jwt.isNotEmpty() || sessionId.isNotEmpty()) {
            refreshAccountInfo()
        }
    }

    fun refreshAccountInfo() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val localJwt = LocalStorage.loadString("supabase_jwt") ?: LocalStorage.loadString("appwrite_jwt") ?: ""
                val localRefresh = LocalStorage.loadString("supabase_session_id") ?: LocalStorage.loadString("appwrite_session_id") ?: ""
                
                var session = supabaseClient.auth.currentSessionOrNull()
                if (session == null && localJwt.isNotEmpty()) {
                    try {
                        val userSession = UserSession(
                            accessToken = localJwt,
                            refreshToken = localRefresh,
                            expiresIn = 3600L,
                            tokenType = "bearer",
                            user = null
                        )
                        // Import native credentials to sync KMP auth state with Swift/Android native flows
                        supabaseClient.auth.importSession(userSession)
                        session = supabaseClient.auth.currentSessionOrNull()
                    } catch (e: Exception) {
                        println("[AuthViewModel] importSession failed: ${e.message}")
                    }
                }
                
                val user = session?.user ?: supabaseClient.auth.currentUserOrNull()
                if (session != null && user != null) {
                    val token = session.accessToken
                    val userId = user.id
                    val email = user.email ?: ""
                    val meta = user.userMetadata
                    val name = meta?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                    
                    LocalStorage.saveString("supabase_jwt", token)
                    LocalStorage.saveString("supabase_session_id", session.refreshToken)
                    LocalStorage.saveString("supabase_user_id", userId)
                    LocalStorage.saveString("appwrite_jwt", token)
                    LocalStorage.saveString("appwrite_session_id", session.refreshToken)
                    LocalStorage.saveString("appwrite_user_id", userId)
                    LocalStorage.saveString("user_email", email)
                    app.ak25.pocketflow.platform.AndroidAuthBridge.onOneSignalLogin?.invoke(userId)
                    
                    withContext(Dispatchers.Main) {
                        userEmail = email
                    }
                    val finalName = name.ifEmpty { email.substringBefore("@") }
                    LocalStorage.saveString("user_name", finalName)
                    withContext(Dispatchers.Main) {
                        userName = finalName
                        isLoggedIn = true
                    }
                }
            } catch (e: Exception) {
                println("[AuthViewModel] refreshAccountInfo error: ${e.message}")
            }
        }
    }

    suspend fun login(email: String, password: String) {
        isLoading = true
        errorMessage = null
        try {
            LocalStorage.saveString("sign_out_requested", "")

            val bridge = AuthBridgeHolder.current
            if (bridge != null) {
                val err = awaitNativeAuth { cb -> bridge.login(email, password, cb) }
                if (err != null) {
                    errorMessage = err
                    return
                }
                notifyNativeAuthSuccess()
                return
            }

            withContext(Dispatchers.IO) {
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }

            setGuestState(false)
            fetchAndPersistJWTAndAccount()
            withContext(Dispatchers.Main) {
                isLoggedIn = true
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Login failed"
        } finally {
            isLoading = false
        }
    }

    suspend fun signUp(name: String, email: String, password: String) {
        isLoading = true
        errorMessage = null
        try {
            LocalStorage.saveString("sign_out_requested", "")

            val bridge = AuthBridgeHolder.current
            if (bridge != null) {
                val err = awaitNativeAuth { cb -> bridge.signUp(name, email, password, cb) }
                if (err != null) {
                    errorMessage = err
                    return
                }
                notifyNativeAuthSuccess()
                return
            }

            withContext(Dispatchers.IO) {
                supabaseClient.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    this.data = buildJsonObject {
                        put("name", name)
                    }
                }
            }

            setGuestState(false)
            fetchAndPersistJWTAndAccount()
            withContext(Dispatchers.Main) {
                isLoggedIn = true
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Sign up failed"
        } finally {
            isLoading = false
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        isLoading = true
        try {
            try {
                supabaseClient.auth.signOut()
            } catch (_: Exception) {}
            
            LocalStorage.saveString("supabase_jwt", "")
            LocalStorage.saveString("supabase_session_id", "")
            LocalStorage.saveString("supabase_user_id", "")
            LocalStorage.saveString("appwrite_jwt", "")
            LocalStorage.saveString("appwrite_session_id", "")
            LocalStorage.saveString("appwrite_user_id", "")
            LocalStorage.saveString("user_name", "")
            LocalStorage.saveString("user_email", "")
            LocalStorage.saveString("sign_out_requested", "true")
            setGuestState(false)
            LocalStorage.saveString("workflows", "")
            app.ak25.pocketflow.platform.AndroidAuthBridge.onOneSignalLogout?.invoke()
            withContext(Dispatchers.Main) {
                userName = ""
                userEmail = ""
                isLoggedIn = false
            }
        } finally {
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    fun loginAsGuest() {
        LocalStorage.saveString("sign_out_requested", "")
        setGuestState(true)
        LocalStorage.saveString("user_name", "Guest")
        LocalStorage.saveString("user_email", "")
        LocalStorage.saveString("supabase_jwt", "")
        LocalStorage.saveString("supabase_session_id", "")
        LocalStorage.saveString("supabase_user_id", "")
        LocalStorage.saveString("appwrite_jwt", "")
        LocalStorage.saveString("appwrite_session_id", "")
        LocalStorage.saveString("appwrite_user_id", "")
        app.ak25.pocketflow.platform.AndroidAuthBridge.onOneSignalLogout?.invoke()
        userName = "Guest"
        userEmail = ""
        isLoggedIn = true
    }

    suspend fun signInWithApple() {
        val bridge = AuthBridgeHolder.current
        if (bridge != null) {
            isLoading = true
            errorMessage = null
            try {
                val err = awaitNativeAuth { cb -> bridge.signInWithApple(onResult = cb) }
                if (err != null) {
                    errorMessage = err
                } else {
                    notifyNativeAuthSuccess()
                }
            } finally {
                isLoading = false
            }
        }
    }

    suspend fun signInWithGoogle() {
        val bridge = AuthBridgeHolder.current
        if (bridge != null) {
            isLoading = true
            errorMessage = null
            try {
                val err = awaitNativeAuth { cb -> bridge.signInWithGoogle(onResult = cb) }
                if (err != null) {
                    errorMessage = err
                } else {
                    notifyNativeAuthSuccess()
                }
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun awaitNativeAuth(start: (callback: (String?) -> Unit) -> Unit): String? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            start { err ->
                if (cont.isActive) cont.resume(err)
            }
        }

    fun notifyNativeAuthSuccess() {
        println("[AuthViewModel] notifyNativeAuthSuccess() called from iOS native auth bridge")
        LocalStorage.saveString("sign_out_requested", "")
        setGuestState(false)
        
        val jwt = LocalStorage.loadString("appwrite_jwt") ?: ""
        val email = LocalStorage.loadString("user_email") ?: ""
        val name = LocalStorage.loadString("user_name") ?: ""
        
        userEmail = email
        userName = name
        isLoggedIn = true

        if (jwt.isNotEmpty()) {
            refreshAccountInfo()
        }
    }

    fun updateUserName(newName: String) {
        if (newName.isNotBlank()) {
            userName = newName
            LocalStorage.saveString("user_name", newName)
            
            // Push update to Supabase Auth user metadata asynchronously
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    val session = supabaseClient.auth.currentSessionOrNull()
                    if (session != null) {
                        supabaseClient.auth.updateUser {
                            data = buildJsonObject {
                                put("full_name", newName)
                                put("name", newName)
                            }
                        }
                        println("[AuthViewModel] Successfully updated full_name metadata on Supabase Auth server")
                    }
                } catch (e: Exception) {
                    println("[AuthViewModel] ❌ Failed to update Supabase Auth user metadata: ${e.message}")
                }
            }
        }
    }

    fun requestSignIn() {
        println("[AuthViewModel] requestSignIn() called — redirecting to login page")
        setGuestState(false)
        LocalStorage.saveString("user_name", "")
        LocalStorage.saveString("user_email", "")
        LocalStorage.saveString("sign_out_requested", "true")
        AuthBridgeHolder.current?.showNativeLoginPage()
        isLoggedIn = false
    }

    fun getGoogleOAuthUrl(): String {
        return supabaseClient.auth.getOAuthUrl(
            provider = Google,
            redirectUrl = "supabase-callback-pocketflow://auth/oauth2/success"
        )
    }

    fun getAppleOAuthUrl(): String {
        return supabaseClient.auth.getOAuthUrl(
            provider = Apple,
            redirectUrl = "supabase-callback-pocketflow://auth/oauth2/success"
        )
    }

    suspend fun handleSupabaseOAuthCallback(uriString: String) = withContext(Dispatchers.IO) {
        println("[RealtimeDebug] 📥 handleSupabaseOAuthCallback received URI: $uriString")
        try {
            val session = supabaseClient.auth.parseSessionFromUrl(uriString)
            supabaseClient.auth.importSession(session)
            setGuestState(false)
            fetchAndPersistJWTAndAccount(session)
            withContext(Dispatchers.Main) {
                isLoggedIn = true
            }
            println("[RealtimeDebug] ✅ handleSupabaseOAuthCallback session imported successfully")
        } catch (e: Exception) {
            println("[RealtimeDebug] ❌ handleSupabaseOAuthCallback error: ${e.message}")
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                errorMessage = "Sign-In failed: ${e.message}"
            }
        }
    }

    suspend fun signInWithGoogleIdToken(idTokenString: String) = withContext(Dispatchers.IO) {
        println("[RealtimeDebug] 🔑 signInWithGoogleIdToken called with token length: ${idTokenString.length}")
        withContext(Dispatchers.Main) {
            isLoading = true
            errorMessage = null
        }
        try {
            supabaseClient.auth.signInWith(IDToken) {
                idToken = idTokenString
                provider = Google
            }
            val session = supabaseClient.auth.currentSessionOrNull()
            if (session != null) {
                setGuestState(false)
                fetchAndPersistJWTAndAccount(session)
                withContext(Dispatchers.Main) {
                    isLoggedIn = true
                }
                println("[RealtimeDebug] ✅ signInWithGoogleIdToken authenticated successfully")
            } else {
                throw Exception("No session created after native token validation")
            }
        } catch (e: Exception) {
            println("[RealtimeDebug] ❌ signInWithGoogleIdToken failed: ${e.message}")
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                errorMessage = "Google native sign-in failed: ${e.message}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    private suspend fun fetchAndPersistJWTAndAccount() = withContext(Dispatchers.IO) {
        val session = supabaseClient.auth.currentSessionOrNull()
        if (session != null) {
            fetchAndPersistJWTAndAccount(session)
        }
    }

    private suspend fun fetchAndPersistJWTAndAccount(session: UserSession) = withContext(Dispatchers.IO) {
        LocalStorage.saveString("sign_out_requested", "")
        
        // Fetch the up-to-date user details from the server because parseSessionFromUrl only returns tokens
        val user = try {
            supabaseClient.auth.retrieveUser(session.accessToken)
        } catch (e: Exception) {
            println("[RealtimeDebug] ⚠️ retrieveUser failed: ${e.message}")
            session.user
        }
        
        val token = session.accessToken
        val uid = user?.id ?: ""
        val email = user?.email ?: ""
        val metadata = user?.userMetadata
        val name = metadata?.get("full_name")?.jsonPrimitive?.contentOrNull 
            ?: metadata?.get("name")?.jsonPrimitive?.contentOrNull 
            ?: ""
        
        LocalStorage.saveString("supabase_jwt", token)
        LocalStorage.saveString("supabase_session_id", session.refreshToken ?: "")
        LocalStorage.saveString("supabase_user_id", uid)
        LocalStorage.saveString("appwrite_jwt", token)
        LocalStorage.saveString("appwrite_session_id", session.refreshToken ?: "")
        LocalStorage.saveString("appwrite_user_id", uid)
        LocalStorage.saveString("user_email", email)
        app.ak25.pocketflow.platform.AndroidAuthBridge.onOneSignalLogin?.invoke(uid)
        
        withContext(Dispatchers.Main) {
            userEmail = email
        }
        val finalName = name.ifEmpty { email.substringBefore("@") }
        LocalStorage.saveString("user_name", finalName)
        withContext(Dispatchers.Main) {
            userName = finalName
        }

        if (name.isBlank() && finalName.isNotBlank()) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    supabaseClient.auth.updateUser {
                        data = buildJsonObject {
                            put("full_name", finalName)
                            put("name", finalName)
                        }
                    }
                    println("[AuthViewModel] Pushed fallback full_name metadata to Supabase Auth server successfully")
                } catch (e: Exception) {
                    println("[AuthViewModel] ❌ Failed to push fallback user name metadata: ${e.message}")
                }
            }
        }
    }
}
