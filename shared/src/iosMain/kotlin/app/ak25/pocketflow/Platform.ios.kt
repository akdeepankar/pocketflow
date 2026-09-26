package app.ak25.pocketflow

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val appVersion: String = (platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "1.0.0"
}

actual fun getPlatform(): Platform = IOSPlatform()