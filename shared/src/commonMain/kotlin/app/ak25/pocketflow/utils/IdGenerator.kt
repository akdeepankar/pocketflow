package app.ak25.pocketflow.utils

object IdGenerator {
    private const val ALLOWED_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    fun generate(): String {
        return (1..16)
            .map { ALLOWED_CHARS.random() }
            .joinToString("")
    }
}
