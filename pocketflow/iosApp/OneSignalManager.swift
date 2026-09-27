import Foundation
import UIKit
import UserNotifications
import OneSignalFramework
import Shared

@objc final class OneSignalManager: NSObject, OSPushSubscriptionObserver, OSNotificationLifecycleListener, OSNotificationClickListener, OSInAppMessageLifecycleListener, OSInAppMessageClickListener {
    @objc static let shared = OneSignalManager()
    
    private let appId = "7090ae90-1a87-4702-8cfd-2694e44301d9"
    private override init() {
        super.init()
    }
    
    @objc func initialize(launchOptions: [UIApplication.LaunchOptionsKey: Any]?) {
        // Enable verbose logging for debugging in-app messages and push registrations
        OneSignal.Debug.setLogLevel(.LL_VERBOSE)
        
        // Initialize OneSignal
        OneSignal.initialize(appId, withLaunchOptions: launchOptions)
        
        // Ensure push subscription is opted in
        OneSignal.User.pushSubscription.optIn()
        
        // Start paused to prevent premature IAM display attempt when window is not ready
        OneSignal.InAppMessages.paused = true
        
        // Add push subscription observer
        OneSignal.User.pushSubscription.addObserver(self)
        
        // Listen for incoming notifications while app is in foreground
        OneSignal.Notifications.addForegroundLifecycleListener(self)
        
        // Listen for notification clicks and route deep links
        OneSignal.Notifications.addClickListener(self)

        // Listen for In-App Message display events and clicks for diagnostics
        OneSignal.InAppMessages.addLifecycleListener(self)
        OneSignal.InAppMessages.addClickListener(self)
        
        // Auto-login existing user if session is already saved
        let storedUid = UserDefaults.standard.string(forKey: "supabase_user_id") ?? UserDefaults.standard.string(forKey: "appwrite_user_id")
        if let storedUid = storedUid, !storedUid.isEmpty {
            OneSignal.login(storedUid)
            OneSignal.User.addAlias(label: "external_id", id: storedUid)
            OneSignal.User.pushSubscription.optIn()
            print("[OneSignal-iOS] 👤 Auto logged in stored user: \(storedUid)")
        }
        
        // Evaluate subscription state immediately
        evaluateSubscription(OneSignal.User.pushSubscription.id)
        
        // Request/check push permission shortly after UI is ready
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.promptForPushPermissionIfNeeded()
        }
        
        // Listen for app coming back to foreground to re-verify permissions
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
        
        // Register OneSignalBridge for KMP triggers and tags
        OneSignalBridgeHolder.shared.current = self

        // Unpause in-app messages after a 2.0s delay to allow SwiftUI / UIKit window to become key and visible
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            print("[OneSignal-iOS] Unpausing In-App Messages now that UI window is ready.")
            OneSignal.InAppMessages.paused = false
        }
    }
    
    @objc private func handleAppWillEnterForeground() {
        promptForPushPermissionIfNeeded()
    }
    
    @objc func promptForPushPermissionIfNeeded() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                switch settings.authorizationStatus {
                case .notDetermined:
                    print("[OneSignal-iOS] 🔔 Notification permission not determined. Prompting system dialog...")
                    OneSignal.Notifications.requestPermission({ accepted in
                        print("[OneSignal-iOS] User accepted notifications: \(accepted)")
                        if accepted {
                            OneSignal.User.pushSubscription.optIn()
                        }
                    }, fallbackToSettings: false)
                    
                case .denied:
                    print("[OneSignal-iOS] ⚠️ Notification permission is denied in Settings.")
                    self.showSettingsAlertIfNeeded()
                    
                case .authorized, .provisional, .ephemeral:
                    print("[OneSignal-iOS] ✅ Notification permission is authorized.")
                    OneSignal.User.pushSubscription.optIn()
                    
                @unknown default:
                    break
                }
            }
        }
    }
    
    private func showSettingsAlertIfNeeded() {
        // Prevent spamming the alert every time
        let lastPromptKey = "OneSignalSettingsPromptTime"
        let lastTime = UserDefaults.standard.double(forKey: lastPromptKey)
        let now = Date().timeIntervalSince1970
        // Prompt at most once per 60 seconds
        if now - lastTime < 60 { return }
        UserDefaults.standard.set(now, forKey: lastPromptKey)
        
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
            return
        }
        
        var topVC = rootVC
        while let presentedVC = topVC.presentedViewController {
            topVC = presentedVC
        }
        
        let alert = UIAlertController(
            title: "Enable Notifications",
            message: "PocketFlow needs notification access so workflow members can ping you and collaborate in real-time.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Later", style: .cancel, handler: nil))
        alert.addAction(UIAlertAction(title: "Settings", style: .default, handler: { _ in
            if let settingsUrl = URL(string: UIApplication.openSettingsURLString), UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl)
            }
        }))
        
        topVC.present(alert, animated: true)
    }
    
    @objc func login(userId: String) {
        guard !userId.isEmpty else { return }
        OneSignal.login(userId)
        OneSignal.User.addAlias(label: "external_id", id: userId)
        OneSignal.User.pushSubscription.optIn()
        print("[OneSignal-SDK] 👤 Explicit login called for external_id: '\(userId)'")
        print("[OneSignal-SDK]    - OneSignal User ID: \(OneSignal.User.onesignalId ?? "nil")")
        print("[OneSignal-SDK]    - External ID: \(OneSignal.User.externalId ?? "nil")")
        print("[OneSignal-SDK]    - Push Subscription ID: \(OneSignal.User.pushSubscription.id ?? "nil")")
        print("[OneSignal-SDK]    - Opted In: \(OneSignal.User.pushSubscription.optedIn)")
    }
    
    @objc func logout() {
        OneSignal.logout()
        print("[OneSignal-SDK] 👤 User logged out from OneSignal")
    }
    
    // OSPushSubscriptionObserver protocol method
    func onPushSubscriptionDidChange(state: OSPushSubscriptionChangedState) {
        evaluateSubscription(state.current.id)
        print("[OneSignal-SDK] 🔄 Push Subscription Changed:")
        print("[OneSignal-SDK]    - Subscription ID: \(state.current.id ?? "nil")")
        print("[OneSignal-SDK]    - Push Token (APNs): \(state.current.token ?? "nil")")
        print("[OneSignal-SDK]    - Opted In: \(state.current.optedIn)")
        print("[OneSignal-SDK]    - External User ID: \(OneSignal.User.externalId ?? "nil")")
        
        let storedUid = UserDefaults.standard.string(forKey: "supabase_user_id") ?? UserDefaults.standard.string(forKey: "appwrite_user_id")
        if let storedUid = storedUid, !storedUid.isEmpty {
            if OneSignal.User.externalId != storedUid {
                OneSignal.login(storedUid)
                OneSignal.User.addAlias(label: "external_id", id: storedUid)
                OneSignal.User.pushSubscription.optIn()
                print("[OneSignal-SDK] 👤 Auto linked stored user on push subscription update: \(storedUid)")
            }
        }
    }
    
    // OSNotificationLifecycleListener protocol method
    func onWillDisplay(event: OSNotificationWillDisplayEvent) {
        print("[OneSignal-iOS] 🔔 Received push notification in foreground: '\(event.notification.title ?? "")' - '\(event.notification.body ?? "")'")
        // Prevent default suppression: show the notification banner even when foregrounded
        event.preventDefault()
        event.notification.display()
    }
    
    // OSNotificationClickListener protocol method
    func onClick(event: OSNotificationClickEvent) {
        let additionalData = event.notification.additionalData
        print("[OneSignal-iOS] 🔔 Notification clicked! data: \(String(describing: additionalData))")
        if let data = additionalData {
            let workflowId = data["workflow_id"] as? String ?? ""
            let nodeId = data["node_id"] as? String
            let type = data["type"] as? String
            if !workflowId.isEmpty {
                DeepLinkRouter.shared.onNotificationClicked(workflowId: workflowId, nodeId: nodeId, type: type)
            }
        }
    }

    // OSInAppMessageLifecycleListener protocol methods
    func onWillDisplayInAppMessage(event: OSInAppMessageWillDisplayEvent) {
        print("[OneSignal-IAM-iOS] 💬 In-App Message WILL display: messageId=\(event.message.messageId)")
    }
    
    func onDidDisplayInAppMessage(event: OSInAppMessageDidDisplayEvent) {
        print("[OneSignal-IAM-iOS] 📺 In-App Message DID display: messageId=\(event.message.messageId)")
    }
    
    func onWillDismissInAppMessage(event: OSInAppMessageWillDismissEvent) {
        print("[OneSignal-IAM-iOS] 🚪 In-App Message WILL dismiss: messageId=\(event.message.messageId)")
    }
    
    func onDidDismissInAppMessage(event: OSInAppMessageDidDismissEvent) {
        print("[OneSignal-IAM-iOS] ✅ In-App Message DID dismiss: messageId=\(event.message.messageId)")
    }
    
    // OSInAppMessageClickListener protocol method
    func onClick(event: OSInAppMessageClickEvent) {
        print("[OneSignal-IAM-iOS] 👆 In-App Message clicked: actionId=\(event.result.actionId ?? "nil"), urlTarget=\(String(describing: event.result.urlTarget))")
    }
    
    private func evaluateSubscription(_ subscriptionId: String?) {
        guard let subId = subscriptionId, !subId.isEmpty, !subId.hasPrefix("local-") else { return }
        UserDefaults.standard.set(subId, forKey: "onesignal_subscription_id")
        print("[OneSignal-SDK] ✅ Registered server-assigned push subscription ID: \(subId)")
    }
}

extension OneSignalManager: OneSignalBridge {
    func addTrigger(key: String, value: String) {
        OneSignal.InAppMessages.addTrigger(key, withValue: value)
        print("[OneSignal-iOS] 🎯 Added InApp trigger: '\(key)' = '\(value)'")
    }
    
    func removeTrigger(key: String) {
        OneSignal.InAppMessages.removeTrigger(key)
        print("[OneSignal-iOS] 🗑️ Removed InApp trigger: '\(key)'")
    }
    
    func addTag(key: String, value: String) {
        OneSignal.User.addTag(key: key, value: value)
        print("[OneSignal-iOS] 🏷️ Added User tag: '\(key)' = '\(value)'")
    }
}
