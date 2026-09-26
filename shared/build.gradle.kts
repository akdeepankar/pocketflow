import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    androidLibrary {
        namespace = "app.ak25.pocketflow.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation("io.ktor:ktor-client-okhttp:2.3.11")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.peekaboo.image.picker)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation("io.ktor:ktor-client-websockets:2.3.11")
            implementation("network.chaintech:compose-multiplatform-media-player:1.0.22")
            implementation(libs.revenuecat.kmp.core)
            implementation(libs.revenuecat.kmp.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            implementation("io.github.jan-tennert.supabase:postgrest-kt:2.6.1")
            implementation("io.github.jan-tennert.supabase:gotrue-kt:2.6.1")
            implementation("io.github.jan-tennert.supabase:realtime-kt:2.6.1")
            implementation("io.github.jan-tennert.supabase:storage-kt:2.6.1")
            implementation("io.github.jan-tennert.supabase:functions-kt:2.6.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    }
}