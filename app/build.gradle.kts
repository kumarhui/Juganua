plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cvam.dignity.juganua"
    compileSdk = 35

    defaultConfig {
        applicationId = "cvam.dignity.juganua"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // FIX: Migrated from deprecated kotlinOptions to compilerOptions
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // --- Core Android & Compose ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // --- GOOGLE MOBILE ADS (Missing Library Fixed) ---
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Coil for Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Document Processing
    implementation("com.itextpdf:itext7-core:8.0.2")
    implementation("com.tom-roush:pdfbox-android:2.0.25.0")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.7.5")

    // AI & ML Kit
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // AI Background Remover
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    implementation("androidx.compose.ui:ui-text")
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.foundation)

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Utilities
    implementation("com.google.zxing:core:3.5.1")

    // FIX: Updated ucrop to 2.2.11
    implementation("com.github.yalantis:ucrop:2.2.11")

    // FIX: Added missing transition library required by uCrop 2.2.11
    implementation("androidx.transition:transition:1.5.1")

    implementation("com.google.code.gson:gson:2.10.1")

    // System Libs
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.print:print:1.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}