import SwiftUI
import WidgetKit
import ActivityKit

@available(iOS 16.2, *)
struct PocketFlowLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PocketFlowActivityAttributes.self) { context in
            // Lock screen & Notification banner UI
            LockScreenLiveActivityView(context: context)
                .activityBackgroundTint(Color.black)
                .activitySystemActionForegroundColor(Color.white)
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded UI
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: iconForNodeType(context.state.currentNodeType))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(accentColorForNodeType(context.state.currentNodeType))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(context.state.workflowName)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Text(context.state.nodeTitle)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.gray)
                                .lineLimit(1)
                        }
                    }
                    .padding(.leading, 8)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if context.state.isFinished {
                        Image(systemName: context.state.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(context.state.isSuccess ? .green : .red)
                            .padding(.trailing, 8)
                    } else if context.state.totalSteps > 1 {
                        Text("\(context.state.currentStep)/\(context.state.totalSteps)")
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundColor(accentColorForNodeType(context.state.currentNodeType))
                            .padding(.trailing, 8)
                    }
                }
                DynamicIslandExpandedRegion(.center) {
                    if context.state.totalSteps > 1 {
                        Text("Step \(context.state.currentStep) of \(context.state.totalSteps)")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.gray)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        if context.state.totalSteps > 1 {
                            StepProgressTickView(
                                totalSteps: context.state.totalSteps,
                                completedSteps: context.state.completedSteps,
                                stepNodeTypes: context.state.stepNodeTypes,
                                isFinished: context.state.isFinished,
                                isSuccess: context.state.isSuccess,
                                compact: true
                            )
                        }
                        Text(context.state.status)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white.opacity(0.85))
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 10)
                    .padding(.bottom, 6)
                }
            } compactLeading: {
                HStack(spacing: 4) {
                    Image(systemName: iconForNodeType(context.state.currentNodeType))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(accentColorForNodeType(context.state.currentNodeType))
                    if context.state.totalSteps > 1 && !context.state.isFinished {
                        Text("\(context.state.currentStep)/\(context.state.totalSteps)")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                    }
                }
                .padding(.leading, 4)
            } compactTrailing: {
                if context.state.isFinished {
                    Image(systemName: context.state.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(context.state.isSuccess ? .green : .red)
                        .padding(.trailing, 4)
                }
            } minimal: {
                if context.state.isFinished {
                    Image(systemName: context.state.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(context.state.isSuccess ? .green : .red)
                } else if context.state.totalSteps > 1 {
                    HStack(spacing: 2) {
                        Image(systemName: iconForNodeType(context.state.currentNodeType))
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(accentColorForNodeType(context.state.currentNodeType))
                        Text("\(context.state.currentStep)/\(context.state.totalSteps)")
                            .font(.system(size: 9, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                    }
                } else {
                    Image(systemName: iconForNodeType(context.state.currentNodeType))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(accentColorForNodeType(context.state.currentNodeType))
                }
            }
        }
    }
}

// ── Progress Tick View (shows checkmark for completed nodes, active node, and pending nodes) ──
@available(iOS 16.2, *)
struct StepProgressTickView: View {
    let totalSteps: Int
    let completedSteps: Int
    let stepNodeTypes: [String]
    let isFinished: Bool
    let isSuccess: Bool
    var compact: Bool = false

    var body: some View {
        let count = max(totalSteps, 1)
        HStack(spacing: 0) {
            ForEach(0..<count, id: \.self) { index in
                let isCompleted = index < completedSteps
                let isCurrent = index == completedSteps && !isFinished
                let nodeType = index < stepNodeTypes.count ? stepNodeTypes[index] : ""

                HStack(spacing: 0) {
                    ZStack {
                        if isCompleted || (isFinished && isSuccess) {
                            Circle()
                                .fill(Color.green)
                                .frame(width: compact ? 18 : 24, height: compact ? 18 : 24)
                            Image(systemName: "checkmark")
                                .font(.system(size: compact ? 9 : 12, weight: .bold))
                                .foregroundColor(.white)
                        } else if isCurrent {
                            ZStack {
                                Circle()
                                    .fill(accentColorForNodeType(nodeType).opacity(0.18))
                                    .frame(width: compact ? 18 : 24, height: compact ? 18 : 24)
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: accentColorForNodeType(nodeType)))
                                    .scaleEffect(compact ? 0.65 : 0.85)
                                Image(systemName: iconForNodeType(nodeType))
                                    .font(.system(size: compact ? 7 : 9, weight: .bold))
                                    .foregroundColor(accentColorForNodeType(nodeType))
                            }
                        } else if isFinished && !isSuccess && index == completedSteps {
                            Circle()
                                .fill(Color.red)
                                .frame(width: compact ? 18 : 24, height: compact ? 18 : 24)
                            Image(systemName: "xmark")
                                .font(.system(size: compact ? 9 : 11, weight: .bold))
                                .foregroundColor(.white)
                        } else {
                            Circle()
                                .fill(Color.white.opacity(0.12))
                                .frame(width: compact ? 18 : 24, height: compact ? 18 : 24)
                            Circle()
                                .stroke(Color.white.opacity(0.3), lineWidth: 1.2)
                                .frame(width: compact ? 18 : 24, height: compact ? 18 : 24)
                            Text("\(index + 1)")
                                .font(.system(size: compact ? 9 : 10, weight: .semibold))
                                .foregroundColor(Color.gray)
                        }
                    }

                    if index < count - 1 {
                        Rectangle()
                            .fill(index < completedSteps ? Color.green : Color.white.opacity(0.2))
                            .frame(height: compact ? 2 : 2.5)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

@available(iOS 16.2, *)
struct LockScreenLiveActivityView: View {
    let context: ActivityViewContext<PocketFlowActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header: Icon + Titles + Step Badge
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(accentColorForNodeType(context.state.currentNodeType).opacity(0.22))
                        .frame(width: 38, height: 38)
                    Image(systemName: iconForNodeType(context.state.currentNodeType))
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(accentColorForNodeType(context.state.currentNodeType))
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(context.state.workflowName)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text(context.state.nodeTitle)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                        .lineLimit(1)
                }

                Spacer()

                // Step indicator badge / pill
                if context.state.isFinished {
                    HStack(spacing: 4) {
                        Image(systemName: context.state.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text(context.state.isSuccess ? "Done" : "Failed")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(context.state.isSuccess ? .green : .red)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background((context.state.isSuccess ? Color.green : Color.red).opacity(0.18))
                    .cornerRadius(12)
                } else if context.state.totalSteps > 1 {
                    Text("\(min(context.state.currentStep, context.state.totalSteps))/\(context.state.totalSteps)")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundColor(accentColorForNodeType(context.state.currentNodeType))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(accentColorForNodeType(context.state.currentNodeType).opacity(0.2))
                        .cornerRadius(12)
                }
            }

            // Step Progress Tick View (Shows green checkmarks for completed nodes)
            if context.state.totalSteps > 1 {
                StepProgressTickView(
                    totalSteps: context.state.totalSteps,
                    completedSteps: context.state.completedSteps,
                    stepNodeTypes: context.state.stepNodeTypes,
                    isFinished: context.state.isFinished,
                    isSuccess: context.state.isSuccess
                )
                .padding(.vertical, 2)
            } else if !context.state.isFinished {
                ProgressView()
                    .progressViewStyle(LinearProgressViewStyle(tint: accentColorForNodeType(context.state.currentNodeType)))
            }

            // Status message
            HStack(spacing: 6) {
                if !context.state.isFinished {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: accentColorForNodeType(context.state.currentNodeType)))
                        .scaleEffect(0.6)
                        .frame(width: 12, height: 12)
                }
                Text(context.state.status)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.7))
                    .lineLimit(1)
            }
        }
        .padding(14)
        .background(Color.black)
    }
}

// ── Shared Helpers ──
private func iconForNodeType(_ type: String) -> String {
    switch type {
    case "IMAGE_GENERATION", "AD_LOCALIZATION", "MARKETING_STOCK_IMAGE", "PRODUCT_CAMPAIGN":
        return "photo.fill"
    case "IMAGE_TO_VIDEO", "PRODUCT_AD", "PRODUCT_SWAP", "MULTI_SHOT_VIDEO", "PRODUCT_UGC", "VIDEO_GENERATION":
        return "video.fill"
    case "TEXT_TO_SPEECH", "AUDIO_GENERATION":
        return "waveform"
    case "MODEL3D_GENERATION":
        return "cube.fill"
    case "TEXT_GENERATION", "TEXT_PROMPT":
        return "text.quote"
    case "NOTE":
        return "note.text"
    default:
        return "sparkles"
    }
}

private func accentColorForNodeType(_ type: String) -> Color {
    switch type {
    case "IMAGE_GENERATION", "AD_LOCALIZATION", "MARKETING_STOCK_IMAGE", "PRODUCT_CAMPAIGN":
        return Color(red: 0.73, green: 0.41, blue: 0.78)
    case "IMAGE_TO_VIDEO", "PRODUCT_AD", "PRODUCT_SWAP", "MULTI_SHOT_VIDEO", "PRODUCT_UGC", "VIDEO_GENERATION":
        return Color(red: 0.94, green: 0.38, blue: 0.57)
    case "TEXT_TO_SPEECH", "AUDIO_GENERATION":
        return Color(red: 1.0, green: 0.72, blue: 0.3)
    case "MODEL3D_GENERATION":
        return Color(red: 0.51, green: 0.78, blue: 0.52)
    default:
        return Color(red: 0.05, green: 0.65, blue: 0.98)
    }
}

