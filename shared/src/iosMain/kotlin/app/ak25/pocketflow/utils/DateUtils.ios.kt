package app.ak25.pocketflow.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun getCurrentDate(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "MMM d, yyyy"
    return formatter.stringFromDate(NSDate())
}
