import Foundation
import Shared

/// Bridges the shared Compose auth layer into the native AuthViewModel on iOS.
///
/// The shared guest sheets (e.g. "Sign In" under the Collaborate-with-friends
/// sheet) call into this so iOS always lands on the native SignInView instead of
/// the Android-only shared LoginScreen.
final class AuthBridgeImpl: NSObject, AuthBridge {

    private let auth: AuthViewModel

    init(auth: AuthViewModel) {
        self.auth = auth
    }

    func login(email: String, password: String, onResult: @escaping (String?) -> Void) {
        // The native flow uses OAuth (Google / Apple) and guest login only.
        onResult("Email login is not available on iOS.")
    }

    func signUp(name: String, email: String, password: String, onResult: @escaping (String?) -> Void) {
        onResult("Email sign-up is not available on iOS.")
    }

    func loginAsGuest() {
        Task { @MainActor in
            auth.loginAsGuest()
        }
    }

    func showNativeLoginPage() {
        Task { @MainActor in
            auth.showLoginPage()
        }
    }

    func signInWithApple(onResult: @escaping (String?) -> Void) {
        Task { @MainActor in
            await auth.signInWithApple()
            onResult(auth.errorMessage)
        }
    }

    func signInWithGoogle(onResult: @escaping (String?) -> Void) {
        Task { @MainActor in
            await auth.signInWithGoogle()
            onResult(auth.errorMessage)
        }
    }

    func signOut() {
        Task { @MainActor in
            await auth.signOut()
        }
    }
}