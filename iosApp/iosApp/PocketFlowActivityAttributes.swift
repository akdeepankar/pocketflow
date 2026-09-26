import Foundation
import ActivityKit

public struct PocketFlowActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var status: String        // "Generating...", "Rendering video...", "Completed", "Failed"
        public var nodeTitle: String     // "Image", "Video", etc.
        public var workflowName: String  // e.g. "Summer Campaign"
        public var progress: Double      // 0.0 to 1.0 (-1.0 for indeterminate)
        public var isFinished: Bool
        public var isSuccess: Bool
        public var timestamp: Date

        public init(
            status: String,
            nodeTitle: String,
            workflowName: String,
            progress: Double = -1.0,
            isFinished: Bool = false,
            isSuccess: Bool = false,
            timestamp: Date = Date()
        ) {
            self.status = status
            self.nodeTitle = nodeTitle
            self.workflowName = workflowName
            self.progress = progress
            self.isFinished = isFinished
            self.isSuccess = isSuccess
            self.timestamp = timestamp
        }
    }

    public var workflowId: String
    public var nodeId: String
    public var nodeType: String

    public init(workflowId: String, nodeId: String, nodeType: String) {
        self.workflowId = workflowId
        self.nodeId = nodeId
        self.nodeType = nodeType
    }
}
