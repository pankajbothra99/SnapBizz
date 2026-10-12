plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Company login + cloud backup (Firebase) switch on automatically once the free Firebase
// setup file "google-services.json" is placed in this "app" folder. Without it the app
// works on the phone only, exactly as before.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.bothrametals.cardscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bothrametals.cardscanner"
        minSdk = 26          // Android 8.0 and newer
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    // One fixed signing key for every build, so a new version installs over the old one
    // (keeps all scanned cards). Keep this file private; it's for in-house use only.
    signingConfigs {
        create("team") {
            storeFile = file("cardscanner.keystore")
            storePassword = "bmalcards2026"
            keyAlias = "cardscanner"
            keyPassword = "bmalcards2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("team")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("team")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // On-device text recognition (works offline, free)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Reads QR codes printed on cards (bundled, works offline, free)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // Google's document scanner: auto edge detection, crop and straighten
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Free AI reading (Gemini API) – JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // Team setup: scan a QR code (Google Play services scanner, no camera permission) and draw one
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")

    // Company login (Google / email + password) and cloud backup – Firebase free "Spark" plan
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // "Continue with Google" button (Android Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
