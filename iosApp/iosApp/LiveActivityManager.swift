import Foundation
import ActivityKit
import Shared

@MainActor
final class LiveActivityManager: NSObject, LiveActivityBridge {
    static let shared = LiveActivityManager()

    private var activeActivities: [String: Any] = [:]

    private override init() {
        super.init()
    }

    func startLiveActivity(
        workflowId: String,
        workflowName: String,
        nodeId: String,
        nodeTitle: String,
        nodeType: String
    ) {
        guard #available(iOS 16.2, *) else {
            print("[LiveActivity] ActivityKit is only available on iOS 16.2+")
            return
        }

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("[LiveActivity] ⚠️ Live Activities are disabled by the user.")
            return
        }

        // End any existing activity for this node
        endLiveActivity(nodeId: nodeId, isSuccess: false, message: "")

        let attributes = PocketFlowActivityAttributes(
            workflowId: workflowId,
            nodeId: nodeId,
            nodeType: nodeType
        )

        let initialContentState = PocketFlowActivityAttributes.ContentState(
            status: "Generating \(nodeTitle)...",
            nodeTitle: nodeTitle,
            workflowName: workflowName,
            progress: -1.0,
            isFinished: false,
            isSuccess: false,
            timestamp: Date()
        )

        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: .init(state: initialContentState, staleDate: Date().addingTimeInterval(900)),
                pushType: nil
            )
            activeActivities[nodeId] = activity
            print("[LiveActivity] 🚀 Started Live Activity for node \(nodeId) (id: \(activity.id))")

            Task {
                for await pushToken in activity.pushTokenUpdates {
                    let tokenString = pushToken.map { String(format: "%02.2hhx", $0) }.joined()
                    print("[LiveActivity] 🔑 Push token generated: \(tokenString)")
                }
            }
        } catch {
            print("[LiveActivity] ❌ Failed to start Live Activity: \(error.localizedDescription)")
        }
    }

    func updateLiveActivity(
        nodeId: String,
        status: String,
        progress: Double,
        message: String,
        isFinished: Bool,
        isSuccess: Bool
    ) {
        guard #available(iOS 16.2, *) else { return }
        guard let activity = activeActivities[nodeId] as? Activity<PocketFlowActivityAttributes> else {
            return
        }

        let updatedState = PocketFlowActivityAttributes.ContentState(
            status: status,
            nodeTitle: activity.content.state.nodeTitle,
            workflowName: activity.content.state.workflowName,
            progress: progress,
            isFinished: isFinished,
            isSuccess: isSuccess,
            timestamp: Date()
        )

        Task {
            await activity.update(
                .init(state: updatedState, staleDate: Date().addingTimeInterval(300))
            )
            print("[LiveActivity] 🔄 Updated Live Activity for node \(nodeId): \(status)")
        }
    }

    func endLiveActivity(nodeId: String, isSuccess: Bool, message: String) {
        guard #available(iOS 16.2, *) else { return }
        guard let activity = activeActivities.removeValue(forKey: nodeId) as? Activity<PocketFlowActivityAttributes> else {
            return
        }

        let finalState = PocketFlowActivityAttributes.ContentState(
            status: isSuccess ? "\(activity.content.state.nodeTitle) Completed!" : (message.isEmpty ? "Generation Failed" : message),
            nodeTitle: activity.content.state.nodeTitle,
            workflowName: activity.content.state.workflowName,
            progress: 1.0,
            isFinished: true,
            isSuccess: isSuccess,
            timestamp: Date()
        )

        Task {
            await activity.end(
                .init(state: finalState, staleDate: nil),
                dismissalPolicy: .after(Date().addingTimeInterval(4.0))
            )
            print("[LiveActivity] 🏁 Ended Live Activity for node \(nodeId) (isSuccess: \(isSuccess))")
        }
    }
}
