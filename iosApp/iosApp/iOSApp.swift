import AVFoundation
import SwiftUI
import UserNotifications
import Shared

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers, .defaultToSpeaker])
        try? AVAudioSession.sharedInstance().setActive(true)

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
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge, .list])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        print("[iOSApp] 🔔 Notification tapped with userInfo: \(userInfo)")
        
        let workflowId = (userInfo["workflow_id"] as? String) ?? ""
        let nodeId = userInfo["node_id"] as? String
        let type = userInfo["type"] as? String
        
        if !workflowId.isEmpty {
            DeepLinkRouter.shared.onNotificationClicked(
                workflowId: workflowId,
                nodeId: nodeId,
                type: type
            )
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var auth: AuthViewModel
    private let bridge: AuthBridgeImpl

    init() {
        let authViewModel = AuthViewModel()
        _auth = StateObject(wrappedValue: authViewModel)
        bridge = AuthBridgeImpl(auth: authViewModel)
        // The shared Compose auth page delegates to AuthViewModel on iOS.
        AuthBridgeHolder.shared.current = bridge
        LiveActivityBridgeHolder.shared.current = LiveActivityManager.shared
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(auth)
        }
    }
}

