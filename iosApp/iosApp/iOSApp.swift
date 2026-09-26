import SwiftUI
import UserNotifications
import Shared
#if canImport(OneSignalFramework)
import OneSignalFramework
#endif

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        #if canImport(OneSignalFramework)
        OneSignal.initialize("7090ae90-1a87-4702-8cfd-2694e44301d9", withLaunchOptions: launchOptions)
        OneSignal.Notifications.requestPermission({ accepted in
            print("[OneSignal] Notification permission granted: \(accepted)")
        }, fallbackToSettings: false)
        #endif

        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("[iOSApp] Notification authorization error: \(error)")
            }
        }
        return true
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

