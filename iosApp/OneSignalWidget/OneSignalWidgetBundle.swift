//
//  OneSignalWidgetBundle.swift
//  OneSignalWidget
//
//  Created by AK DEEPANKAR on 26/09/26.
//

import WidgetKit
import SwiftUI

@main
struct OneSignalWidgetBundle: WidgetBundle {
    var body: some Widget {
        PocketFlowLiveActivityWidget()
        OneSignalWidget()
        OneSignalWidgetControl()
        OneSignalWidgetLiveActivity()
    }
}
