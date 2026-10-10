plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yunai.phototube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yunai.phototube"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

android { buildFeatures { compose = true }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
dependencies {
    implementation(project(":liquid-ui"))
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.3")
    implementation("androidx.media3:media3-ui:1.9.3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
}

dependencies {
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.0")
}

dependencies { androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0") }

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("androidx.paging:paging-compose:3.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.0")
    implementation("io.coil-kt.coil3:coil-gif:3.6.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:5.3.0")
}
dependencies { androidTestImplementation("com.squareup.okhttp3:okhttp-tls:5.3.0") }

dependencies { testImplementation("junit:junit:4.13.2") }
