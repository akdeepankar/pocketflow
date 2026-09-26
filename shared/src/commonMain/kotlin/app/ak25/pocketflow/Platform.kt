package app.ak25.pocketflow

interface Platform {
    val name: String
    val appVersion: String
}

expect fun getPlatform(): Platform