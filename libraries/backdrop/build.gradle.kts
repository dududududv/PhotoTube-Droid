// Android-only build adapter; the vendored upstream sources remain editable.
plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
kotlin {
    android {
        namespace = "com.kyant.backdrop"
        compileSdk = 37
        minSdk = 36
    }
    sourceSets {
        commonMain {
            kotlin.srcDir("../../third_party/AndroidLiquidGlass/backdrop/src/commonMain/kotlin")
            dependencies {
                api("org.jetbrains.compose.foundation:foundation:1.12.0")
                api("org.jetbrains.compose.ui:ui:1.12.0")
                api("org.jetbrains.compose.ui:ui-graphics:1.12.0")
                api("io.github.kyant0:shapes:1.2.1")
                implementation("org.jetbrains:annotations:26.1.0")
            }
        }
        androidMain {
            kotlin.srcDir("../../third_party/AndroidLiquidGlass/backdrop/src/androidMain/kotlin")
        }
    }
}
