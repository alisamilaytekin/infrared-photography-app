plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alisamilaytekin.spectras"
    compileSdk = 35

    val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.alisamilaytekin.spectras"
        minSdk = 21
        targetSdk = 35
        versionCode = 200 + ciRunNumber
        versionName = "5.0.$ciRunNumber"
    }

    signingConfigs {
        create("spectrasRelease") {
            val envStorePath = System.getenv("PLAY_KEYSTORE_PATH")
            val envStoreFile = envStorePath?.takeIf { it.isNotBlank() }?.let { file(it) }
            if (envStoreFile != null && envStoreFile.exists()) {
                storeFile = envStoreFile
                storePassword = System.getenv("PLAY_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("PLAY_KEY_ALIAS") ?: "spectras"
                keyPassword = System.getenv("PLAY_KEY_PASSWORD") ?: ""
            } else {
                storeFile = file("kameras-release.jks")
                storePassword = "kameras123"
                keyAlias = "kameras"
                keyPassword = "kameras123"
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../AndroidManifest.xml")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("spectrasRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("spectrasRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
}
