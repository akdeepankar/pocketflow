import Foundation
import ActivityKit

public struct PocketFlowActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var status: String            // e.g. "Generating Video...", "Step 1 of 3"
        public var nodeTitle: String         // e.g. "Image To Video"
        public var workflowName: String      // e.g. "Summer Campaign"
        public var currentStep: Int          // 1, 2, 3...
        public var totalSteps: Int           // 3
        public var completedSteps: Int       // 0, 1, 2, 3
        public var stepNodeTypes: [String]   // ["IMAGE_GENERATION", "IMAGE_TO_VIDEO", "TEXT_TO_SPEECH"]
        public var currentNodeType: String   // "IMAGE_TO_VIDEO"
        public var progress: Double          // 0.0 to 1.0 (-1.0 for indeterminate)
        public var isFinished: Bool
        public var isSuccess: Bool
        public var timestamp: Double

        public init(
            status: String,
            nodeTitle: String,
            workflowName: String,
            currentStep: Int = 1,
            totalSteps: Int = 1,
            completedSteps: Int = 0,
            stepNodeTypes: [String] = [],
            currentNodeType: String = "",
            progress: Double = -1.0,
            isFinished: Bool = false,
            isSuccess: Bool = false,
            timestamp: Double = Date().timeIntervalSince1970
        ) {
            self.status = status
            self.nodeTitle = nodeTitle
            self.workflowName = workflowName
            self.currentStep = currentStep
            self.totalSteps = totalSteps
            self.completedSteps = completedSteps
            self.stepNodeTypes = stepNodeTypes
            self.currentNodeType = currentNodeType
            self.progress = progress
            self.isFinished = isFinished
            self.isSuccess = isSuccess
            self.timestamp = timestamp
        }
    }

    public var workflowId: String
    public var activityId: String
    public var nodeType: String

    public init(workflowId: String, activityId: String, nodeType: String) {
        self.workflowId = workflowId
        self.activityId = activityId
        self.nodeType = nodeType
    }
}
