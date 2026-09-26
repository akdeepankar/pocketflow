import SwiftUI
import Shared

// MARK: - Root View (handles auth gate)

struct RootView: View {
    @EnvironmentObject private var auth: AuthViewModel

    var body: some View {
        // Native auth gate: show the original native LoginView when signed out,
        // and the shared Compose app when signed in (guest counts as signed in).
        Group {
            if auth.isLoggedIn {
                ContentView()
            } else {
                LoginView()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)) { _ in
            guard auth.isLoggedIn else { return }
            let signOutRequested = UserDefaults.standard.string(forKey: "sign_out_requested") ?? ""
            guard signOutRequested == "true" else { return }
            UserDefaults.standard.set("", forKey: "sign_out_requested")
            UserDefaults.standard.synchronize()
            Task { await auth.signOut() }
        }
    }
}

// MARK: - Content View

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
