import SwiftUI
import WidgetKit
import ActivityKit

@available(iOS 16.2, *)
struct PocketFlowLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PocketFlowActivityAttributes.self) { context in
            // Lock screen & Notification banner UI
            LockScreenLiveActivityView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded UI
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: iconForNodeType(context.attributes.nodeType))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(accentColorForNodeType(context.attributes.nodeType))
                        Text(context.state.nodeTitle)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .padding(.leading, 8)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if context.state.isFinished {
                        Image(systemName: context.state.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(context.state.isSuccess ? .green : .red)
                            .padding(.trailing, 8)
                    } else {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.7)
                            .padding(.trailing, 8)
                    }
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.state.workflowName)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.gray)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.state.status)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white.opacity(0.9))
                        
                        if !context.state.isFinished {
                            ProgressView()
                                .progressViewStyle(LinearProgressViewStyle(tint: accentColorForNodeType(context.attributes.nodeType)))
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 6)
                }
            } compactLeading: {
                Image(systemName: iconForNodeType(context.attributes.nodeType))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(accentColorForNodeType(context.attributes.nodeType))
                    .padding(.leading, 4)
            } compactTrailing: {
                if context.state.isFinished {
                    Image(systemName: context.state.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(context.state.isSuccess ? .green : .red)
                        .padding(.trailing, 4)
                } else {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: accentColorForNodeType(context.attributes.nodeType)))
                        .scaleEffect(0.7)
                        .padding(.trailing, 4)
                }
            } minimal: {
                Image(systemName: context.state.isFinished ? (context.state.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill") : iconForNodeType(context.attributes.nodeType))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(context.state.isFinished ? (context.state.isSuccess ? .green : .red) : accentColorForNodeType(context.attributes.nodeType))
            }
        }
    }

    private func iconForNodeType(_ type: String) -> String {
        switch type {
        case "IMAGE_GENERATION", "AD_LOCALIZATION", "MARKETING_STOCK_IMAGE", "PRODUCT_CAMPAIGN":
            return "photo.fill"
        case "IMAGE_TO_VIDEO", "PRODUCT_AD", "PRODUCT_SWAP", "MULTI_SHOT_VIDEO", "PRODUCT_UGC":
            return "video.fill"
        case "TEXT_TO_SPEECH":
            return "waveform"
        case "MODEL3D_GENERATION":
            return "cube.fill"
        default:
            return "sparkles"
        }
    }

    private func accentColorForNodeType(_ type: String) -> Color {
        switch type {
        case "IMAGE_GENERATION", "AD_LOCALIZATION", "MARKETING_STOCK_IMAGE", "PRODUCT_CAMPAIGN":
            return Color(red: 0.73, green: 0.41, blue: 0.78)
        case "IMAGE_TO_VIDEO", "PRODUCT_AD", "PRODUCT_SWAP", "MULTI_SHOT_VIDEO", "PRODUCT_UGC":
            return Color(red: 0.94, green: 0.38, blue: 0.57)
        case "TEXT_TO_SPEECH":
            return Color(red: 1.0, green: 0.72, blue: 0.3)
        case "MODEL3D_GENERATION":
            return Color(red: 0.51, green: 0.78, blue: 0.52)
        default:
            return Color(red: 0.05, green: 0.65, blue: 0.98)
        }
    }
}

@available(iOS 16.2, *)
struct LockScreenLiveActivityView: View {
    let context: ActivityViewContext<PocketFlowActivityAttributes>

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color(red: 0.1, green: 0.1, blue: 0.14))
                    .frame(width: 44, height: 44)
                Image(systemName: iconForNodeType(context.attributes.nodeType))
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(accentColorForNodeType(context.attributes.nodeType))
            }

            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(context.state.nodeTitle)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.primary)
                    Spacer()
                    Text(context.state.workflowName)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)
                }

                Text(context.state.status)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(.secondary)

                if !context.state.isFinished {
                    ProgressView()
                        .progressViewStyle(LinearProgressViewStyle(tint: accentColorForNodeType(context.attributes.nodeType)))
                        .padding(.top, 2)
                }
            }
        }
        .padding(14)
        .background(Color(UIColor.secondarySystemBackground))
    }

    private func iconForNodeType(_ type: String) -> String {
        switch type {
        case "IMAGE_GENERATION", "AD_LOCALIZATION", "MARKETING_STOCK_IMAGE", "PRODUCT_CAMPAIGN":
            return "photo.fill"
        case "IMAGE_TO_VIDEO", "PRODUCT_AD", "PRODUCT_SWAP", "MULTI_SHOT_VIDEO", "PRODUCT_UGC":
            return "video.fill"
        case "TEXT_TO_SPEECH":
            return "waveform"
        case "MODEL3D_GENERATION":
            return "cube.fill"
        default:
            return "sparkles"
        }
    }

    private func accentColorForNodeType(_ type: String) -> Color {
        switch type {
        case "IMAGE_GENERATION", "AD_LOCALIZATION", "MARKETING_STOCK_IMAGE", "PRODUCT_CAMPAIGN":
            return Color(red: 0.73, green: 0.41, blue: 0.78)
        case "IMAGE_TO_VIDEO", "PRODUCT_AD", "PRODUCT_SWAP", "MULTI_SHOT_VIDEO", "PRODUCT_UGC":
            return Color(red: 0.94, green: 0.38, blue: 0.57)
        case "TEXT_TO_SPEECH":
            return Color(red: 1.0, green: 0.72, blue: 0.3)
        case "MODEL3D_GENERATION":
            return Color(red: 0.51, green: 0.78, blue: 0.52)
        default:
            return Color(red: 0.05, green: 0.65, blue: 0.98)
        }
    }
}

@available(iOS 16.2, *)
@main
struct PocketFlowWidgetsBundle: WidgetBundle {
    var body: some Widget {
        PocketFlowLiveActivityWidget()
    }
}
