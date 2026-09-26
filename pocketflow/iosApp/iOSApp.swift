import AuthenticationServices
import Shared
import SwiftUI
import UserNotifications

// Provides a valid UIWindow anchor for ASWebAuthenticationSession (required iOS 17+)
class PresentationContextProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}


class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        OneSignalManager.shared.initialize(launchOptions: launchOptions)
        UNUserNotificationCenter.current().delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let tokenParts = deviceToken.map { data in String(format: "%02.2hhx", data) }
        let token = tokenParts.joined()
        print("[iOS-APNs] ✅ Registered with device token: \(token)")
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("[iOS-APNs] ❌ Failed to register for remote notifications: \(error.localizedDescription)")
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        print("[iOS-LocalNotification] 🔔 Notification tapped with userInfo: \(userInfo)")
        if let workflowId = userInfo["workflow_id"] as? String, !workflowId.isEmpty {
            let nodeId = userInfo["node_id"] as? String
            let type = userInfo["type"] as? String
            DeepLinkRouter.shared.onNotificationClicked(workflowId: workflowId, nodeId: nodeId, type: type)
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var authViewModel: AuthViewModel
    private let bridge: AuthBridgeImpl

    init() {
        let vm = MainActor.assumeIsolated { AuthViewModel() }
        _authViewModel = StateObject(wrappedValue: vm)
        bridge = AuthBridgeImpl(auth: vm)
        // The shared Compose layer delegates native auth actions (guest "Sign In",
        // social sign-in, sign-out) to AuthViewModel through this bridge.
        AuthBridgeHolder.shared.current = bridge
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(authViewModel)
        }
    }
}
