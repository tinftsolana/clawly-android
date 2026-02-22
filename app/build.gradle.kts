plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization")
    kotlin("kapt")
    id("com.google.dagger.hilt.android") version "2.57.2"
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Force atlassian commonmark version (required by markwon)
configurations.all {
    resolutionStrategy {
        // Replace org.commonmark with com.atlassian.commonmark
        dependencySubstitution {
            substitute(module("org.commonmark:commonmark"))
                .using(module("com.atlassian.commonmark:commonmark:0.13.0"))
            substitute(module("org.commonmark:commonmark-ext-gfm-strikethrough"))
                .using(module("com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:0.13.0"))
        }
    }
}

android {
    namespace = "ai.clawly.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.clawly.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "platform"
    productFlavors {
        create("web2") {
            dimension = "platform"
            isDefault = true
            buildConfigField("Boolean", "IS_WEB2", "true")
            buildConfigField("Boolean", "IS_WEB3", "false")
        }
        create("web3") {
            dimension = "platform"
            buildConfigField("Boolean", "IS_WEB2", "false")
            buildConfigField("Boolean", "IS_WEB3", "true")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(enforcedPlatform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(enforcedPlatform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    val shimmer = "com.facebook.shimmer:shimmer:0.5.0"
    implementation(shimmer)

    // Lottie for animations
    implementation("com.airbnb.android:lottie-compose:6.6.9")

    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation(libs.mobile.wallet.adapter.clientlib.ktx)


// Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-android-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Palette for color extraction
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Haze - Backdrop blur library
    implementation("dev.chrisbanes.haze:haze:0.9.0-beta01")

    // Ktor
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")

    // BouncyCastle for Curve25519 device signing
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Markdown rendering - jeziellago/compose-markdown (via JitPack)
    implementation("com.github.jeziellago:compose-markdown:0.5.8")
    implementation("com.solanamobile:web3-solana:0.2.5")
    implementation("com.solanamobile:rpc-core:0.2.7")
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("androidx.datastore:datastore-preferences-core:1.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Accompanist
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    implementation("org.sol4k:sol4k:0.5.3")

    // Amplitude
    implementation("com.amplitude:analytics-android:1.22.0")
    implementation("com.amplitude:experiment-android-client:1.13.1")

    // Install Referrer for tracking where users come from
    implementation("com.android.installreferrer:installreferrer:2.2")

    // AppsFlyer
    implementation("com.appsflyer:af-android-sdk:6.14.0")

    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-auth-ktx")

    implementation("androidx.media3:media3-transformer:1.2.0")

    // Video Compressor
    implementation("com.github.AbedElazizShe:LightCompressor:1.3.2")

    // RevenueCat
    implementation("com.revenuecat.purchases:purchases:9.14.1")
    implementation("com.revenuecat.purchases:purchases-ui:9.14.1")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Play In-App Reviews
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
}