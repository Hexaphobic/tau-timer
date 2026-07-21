plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chrispoole.intervaltimer.wear"
    compileSdk = 36

    defaultConfig {
        // Same applicationId as the phone app so the Wearable Data Layer pairs them automatically.
        applicationId = "com.chrispoole.intervaltimer"
        minSdk = 30          // Wear OS 3+ (Galaxy Watch 4 and up)
        targetSdk = 35       // Wear OS requirement from Aug 31, 2026 (phone stays 36)
        versionCode = 1000   // own range so it never collides with the phone's code under one Play listing
        versionName = "0.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { compose = true }

    lint {
        // False positive: registerForActivityResult is on a ComponentActivity, not a Fragment.
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
