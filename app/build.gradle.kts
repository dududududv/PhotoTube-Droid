plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.yunai.phototube"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yunai.phototube"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
