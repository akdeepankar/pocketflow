import Foundation
import Shared

/// Bridges the shared Compose auth page into the native AuthViewModel on iOS.
///
/// This is the "use the iOS auth logic from the shared module" glue: every auth
/// action the shared page performs on iOS is delegated to AuthViewModel, which
/// owns the URLSession cookie handling, session persistence and sign-out polling.
final class AuthBridgeImpl: NSObject, AuthBridge {


    private let auth: AuthViewModel

    init(auth: AuthViewModel) {
        self.auth = auth
    }

    func login(email: String, password: String, onResult: @escaping (String?) -> Void) {
        Task { @MainActor in
            await auth.login(email: email, password: password)
            onResult(auth.errorMessage)
        }
    }

    func signUp(name: String, email: String, password: String, onResult: @escaping (String?) -> Void) {
        Task { @MainActor in
            await auth.signUp(name: name, email: email, password: password)
            onResult(auth.errorMessage)
        }
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
