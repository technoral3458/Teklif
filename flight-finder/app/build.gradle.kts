import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Signing: CI can inject a real keystore through env vars. If none is provided we fall
// back to the repository keystore (see keystore/README.md) so that every build produces
// an APK with the *same* signature and updates install over the previous version.
val fallbackStore = rootProject.file("keystore/ucusbul.jks")
val envStore: String? = System.getenv("FF_KEYSTORE_FILE")

android {
    namespace = "com.technoral.ucusbul"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.technoral.ucusbul"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("FF_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("FF_VERSION_NAME") ?: "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("sideload") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("FF_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FF_KEY_ALIAS")
                keyPassword = System.getenv("FF_KEY_PASSWORD")
            } else if (fallbackStore.exists()) {
                storeFile = fallbackStore
                storePassword = "ucusbul"
                keyAlias = "ucusbul"
                keyPassword = "ucusbul"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.getByName("sideload").storeFile != null) {
                signingConfig = signingConfigs.getByName("sideload")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
}
