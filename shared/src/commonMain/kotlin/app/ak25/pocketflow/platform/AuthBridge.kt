package app.ak25.pocketflow.platform

/**
 * Platform auth actions used by the shared auth page.
 *
 * On iOS this is implemented by [AuthBridgeImpl] (Swift), which delegates every
 * call to the native `AuthViewModel` (URLSession + Appwrite REST). On Android it
 * stays null and [app.ak25.pocketflow.ui.auth.SharedAuthViewModel] performs the
 * same REST calls directly with Ktor.
 *
 * Network-y methods report the result through [onResult], receiving the error
 * message on failure or `null` on success.
 */
interface AuthBridge {
    /** Email/password login. [onResult] = error message, or null on success. */
    fun login(email: String, password: String, onResult: (String?) -> Unit)

    /** Create account + start a session. [onResult] = error message, or null on success. */
    fun signUp(name: String, email: String, password: String, onResult: (String?) -> Unit)

    /** Continue as guest — starts an offline guest session. */
    fun loginAsGuest()

    /** Flip the app to the native auth page (used by the guest "Sign In" sheet). */
    fun showNativeLoginPage()

    /** Sign in with Apple (native AuthenticationServices). [onResult] = error message, or null on success. */
    fun signInWithApple(onResult: (String?) -> Unit)

    /** Sign in with Google (native OAuth flow). [onResult] = error message, or null on success. */
    fun signInWithGoogle(onResult: (String?) -> Unit)

    /** Sign out — delete the server session and wipe stored credentials. */
    fun signOut()
}

/**
 * Holds the current native bridge. Registered from iOS during app startup;
 * stays null on Android so [app.ak25.pocketflow.ui.auth.SharedAuthViewModel]
 * uses its built-in Ktor implementation.
 */
object AuthBridgeHolder {
    var current: AuthBridge? = null
}