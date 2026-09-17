plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// İmzalama: CI gerçek bir keystore'u ortam değişkenleriyle verebilir. Verilmezse depodaki
// sideload anahtarı kullanılır; böylece her derleme aynı imzayla çıkar ve yeni sürüm
// telefondaki uygulamanın üzerine sorunsuz kurulur (bkz. keystore/README.md).
val fallbackStore = rootProject.file("keystore/teknoservis.jks")
val envStore: String? = System.getenv("TS_KEYSTORE_FILE")

android {
    namespace = "com.technoral.servis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.technoral.servis"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("TS_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("TS_VERSION_NAME") ?: "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("sideload") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("TS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TS_KEY_ALIAS")
                keyPassword = System.getenv("TS_KEY_PASSWORD")
            } else if (fallbackStore.exists()) {
                storeFile = fallbackStore
                storePassword = "teknoservis"
                keyAlias = "teknoservis"
                keyPassword = "teknoservis"
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
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/INDEX.LIST"
            pickFirsts += "META-INF/mailcap*"
            pickFirsts += "META-INF/javamail*"
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.javamail.android)
    implementation(libs.javamail.activation)
    debugImplementation(libs.androidx.ui.tooling)
}
