import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Same contract as the phone module: a gitignored keystore.properties at the repo root signs the
// release for Play; absent, release falls back to the debug key so it still builds and installs.
// Duplicated rather than hoisted into the root script — twelve lines twice beats a shared helper
// plus the two build files that would have to reach into it.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}

android {
    namespace = "com.chrispoole.intervaltimer.wear"
    compileSdk = 36

    defaultConfig {
        // Same applicationId as the phone app so the Wearable Data Layer pairs them automatically.
        applicationId = "com.chrispoole.intervaltimer"
        minSdk = 30          // Wear OS 3+ (Galaxy Watch 4 and up)
        targetSdk = 35       // Wear OS requirement from Aug 31, 2026 (phone stays 36)
        versionCode = 1002   // own range so it never collides with the phone's code under one Play listing
        versionName = "1.1.0" // matches the phone: one listing, one version the user sees
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Was off, which shipped an 18MB watch APK against the phone's 1.4MB — and Wear has a
            // far tighter size budget than a phone. proguard-rules.pro keeps the one class that is
            // persisted by name.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePropertiesFile.exists())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
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
        // False positive: see the app module's note — AAPT2 needs the -v26 on an adaptive icon.
        disable += "ObsoleteSdkInt"
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
