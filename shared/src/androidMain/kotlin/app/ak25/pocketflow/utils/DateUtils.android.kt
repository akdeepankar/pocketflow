package app.ak25.pocketflow.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun getCurrentDate(): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date())
}
