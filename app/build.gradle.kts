import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val ciKeystorePath = System.getenv("NOVA_ANDROID_KEYSTORE_PATH")
val ciKeystorePassword = System.getenv("NOVA_ANDROID_KEYSTORE_PASSWORD")
val ciKeyAlias = System.getenv("NOVA_ANDROID_KEY_ALIAS")
val ciKeyPassword = System.getenv("NOVA_ANDROID_KEY_PASSWORD")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.novapro.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.novapro.android"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.2"
    }

    signingConfigs {
        if (!ciKeystorePath.isNullOrBlank()) {
            create("ciRelease") {
                storeFile = file(ciKeystorePath)
                storePassword = ciKeystorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Tag-based GitHub releases supply a stable signing keystore through repository secrets.
            // Local builds without these variables keep using Android's default unsigned release config.
            if (!ciKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Google AI Edge local inference runtime. Model files are selected/imported in-app.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
}
