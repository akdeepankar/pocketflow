# R8 / ProGuard rules for PocketFlow (KMP + Supabase + Ktor + OneSignal + Compose)

# Preserve annotations and generics for reflection/serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# KotlinX Serialization
-keepclassmembers class **$$serializer {
    *** INSTANCE;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }

# Supabase & Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# OneSignal SDK
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**

# PocketFlow Core Models & DTOs
-keep class app.ak25.pocketflow.models.** { *; }
-keep class app.ak25.pocketflow.services.** { *; }

# Compose Multiplatform UI
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# General Networking & Logging
-dontwarn okio.**
-dontwarn org.slf4j.**
