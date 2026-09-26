package app.ak25.pocketflow.platform

object AndroidAuthBridge {
    var onGoogleSignIn: (() -> Unit)? = null
    var onOneSignalLogin: ((String) -> Unit)? = null
    var onOneSignalLogout: (() -> Unit)? = null
}
