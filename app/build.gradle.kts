import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Point the app at another server with -PperidotApiUrl=... (the Android emulator reaches
// the host's `bun run dev` server at http://10.0.2.2:3001/api)
val apiUrl = (findProperty("peridotApiUrl") as String?) ?: "https://peridot.ai/api"
val webUrl = (findProperty("peridotWebUrl") as String?) ?: "https://peridot.ai"

// Release signing is read from the environment, so no key ever lives in the repo
val releaseKeystore = System.getenv("PERIDOT_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "ai.peridot.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.peridot.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "WEB_URL", "\"$webUrl\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PERIDOT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PERIDOT_KEY_ALIAS")
                keyPassword = System.getenv("PERIDOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Lets people pick the app's language in the system settings (Android 13+)
    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
