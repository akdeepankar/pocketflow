import Foundation
import ActivityKit
import Shared
import OneSignalFramework

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
        nodeType: String,
        currentStep: Int32,
        totalSteps: Int32,
        stepNodeTypesJson: String
    ) {
        guard #available(iOS 16.2, *) else {
            print("[LiveActivity] ActivityKit is only available on iOS 16.2+")
            return
        }

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("[LiveActivity] ⚠️ Live Activities are disabled by the user.")
            return
        }

        // End any existing activity for this activityId / nodeId
        endLiveActivity(nodeId: nodeId, isSuccess: false, message: "", completedSteps: 0, totalSteps: totalSteps)

        var stepTypes: [String] = []
        if let data = stepNodeTypesJson.data(using: .utf8),
           let list = try? JSONDecoder().decode([String].self, from: data), !list.isEmpty {
            stepTypes = list
        } else {
            stepTypes = [nodeType]
        }

        let attributes = PocketFlowActivityAttributes(
            workflowId: workflowId,
            activityId: nodeId,
            nodeType: nodeType
        )

        let initialStatus = totalSteps > 1
            ? "Step \(currentStep)/\(totalSteps): Generating \(nodeTitle)..."
            : "Generating \(nodeTitle)..."

        let initialContentState = PocketFlowActivityAttributes.ContentState(
            status: initialStatus,
            nodeTitle: nodeTitle,
            workflowName: workflowName,
            currentStep: Int(currentStep),
            totalSteps: Int(totalSteps),
            completedSteps: 0,
            stepNodeTypes: stepTypes,
            currentNodeType: nodeType,
            progress: -1.0,
            isFinished: false,
            isSuccess: false,
            timestamp: Date()
        )

        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: .init(state: initialContentState, staleDate: Date().addingTimeInterval(900)),
                pushType: .token
            )
            activeActivities[nodeId] = activity
            print("[LiveActivity] 🚀 Started Live Activity for \(nodeId) (id: \(activity.id), steps: \(currentStep)/\(totalSteps))")

            Task {
                for await pushToken in activity.pushTokenUpdates {
                    let tokenString = pushToken.map { String(format: "%02.2hhx", $0) }.joined()
                    print("[LiveActivity] 🔑 Push token generated: \(tokenString)")
                    self.registerLiveActivityWithOneSignal(activityId: nodeId, pushToken: tokenString)
                }
            }
        } catch {
            print("[LiveActivity] ❌ Failed to start Live Activity: \(error.localizedDescription)")
        }
    }

    private func registerLiveActivityWithOneSignal(activityId: String, pushToken: String) {
        OneSignal.LiveActivities.enter(activityId, withToken: pushToken) { result in
            print("[LiveActivity] 📡 OneSignal Live Activity registered successfully for \(activityId), result: \(String(describing: result))")
        } withFailure: { error in
            print("[LiveActivity] ❌ Failed to register Live Activity token with OneSignal: \(String(describing: error))")
        }
    }

    @available(iOS 16.2, *)
    private func findActivity(for key: String) -> Activity<PocketFlowActivityAttributes>? {
        if let act = activeActivities[key] as? Activity<PocketFlowActivityAttributes> {
            return act
        }
        if let act = Activity<PocketFlowActivityAttributes>.activities.first(where: {
            $0.attributes.activityId == key || $0.attributes.workflowId == key
        }) {
            activeActivities[key] = act
            return act
        }
        return nil
    }

    func updateLiveActivity(
        nodeId: String,
        status: String,
        progress: Double,
        message: String,
        isFinished: Bool,
        isSuccess: Bool,
        currentStep: Int32,
        totalSteps: Int32,
        completedSteps: Int32,
        nodeTitle: String,
        nodeType: String
    ) {
        guard #available(iOS 16.2, *) else { return }
        guard let activity = findActivity(for: nodeId) else {
            return
        }

        let title = nodeTitle.isEmpty ? activity.content.state.nodeTitle : nodeTitle
        let type = nodeType.isEmpty ? activity.content.state.currentNodeType : nodeType

        let updatedStatus: String
        if !status.isEmpty {
            updatedStatus = status
        } else if !message.isEmpty {
            updatedStatus = message
        } else if isFinished {
            updatedStatus = isSuccess ? "\(title) Completed! ✓" : "Generation Failed"
        } else {
            updatedStatus = activity.content.state.status
        }

        let updatedState = PocketFlowActivityAttributes.ContentState(
            status: updatedStatus,
            nodeTitle: title,
            workflowName: activity.content.state.workflowName,
            currentStep: Int(currentStep),
            totalSteps: Int(totalSteps),
            completedSteps: Int(completedSteps),
            stepNodeTypes: activity.content.state.stepNodeTypes,
            currentNodeType: type,
            progress: progress,
            isFinished: isFinished,
            isSuccess: isSuccess,
            timestamp: Date()
        )

        Task {
            await activity.update(
                .init(state: updatedState, staleDate: Date().addingTimeInterval(300))
            )
            print("[LiveActivity] 🔄 Updated Live Activity for \(nodeId): \(updatedStatus) (step: \(currentStep)/\(totalSteps), completed: \(completedSteps))")
        }
    }

    func endLiveActivity(
        nodeId: String,
        isSuccess: Bool,
        message: String,
        completedSteps: Int32,
        totalSteps: Int32
    ) {
        guard #available(iOS 16.2, *) else { return }
        guard let activity = activeActivities.removeValue(forKey: nodeId) as? Activity<PocketFlowActivityAttributes> ?? findActivity(for: nodeId) else {
            return
        }
        activeActivities.removeValue(forKey: nodeId)

        let finalStatus: String
        if isSuccess {
            finalStatus = totalSteps > 1 ? "All \(totalSteps) nodes completed! ✓" : "\(activity.content.state.nodeTitle) Completed! ✓"
        } else {
            finalStatus = message.isEmpty ? "Generation Failed" : message
        }

        let finalState = PocketFlowActivityAttributes.ContentState(
            status: finalStatus,
            nodeTitle: activity.content.state.nodeTitle,
            workflowName: activity.content.state.workflowName,
            currentStep: Int(totalSteps),
            totalSteps: Int(totalSteps),
            completedSteps: Int(completedSteps),
            stepNodeTypes: activity.content.state.stepNodeTypes,
            currentNodeType: activity.content.state.currentNodeType,
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
            print("[LiveActivity] 🏁 Ended Live Activity for \(nodeId) (isSuccess: \(isSuccess), completed: \(completedSteps)/\(totalSteps))")
        }
    }
}
