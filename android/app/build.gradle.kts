plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.technoral.petkit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.technoral.petkit"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("tr", "en")
    }

    // İmzalama: özel anahtar depoya konmaz. Anahtar dosyası varsa (yerelde
    // ürettiyseniz ya da CI oluşturduysa) onunla, yoksa Android'in hata ayıklama
    // anahtarıyla imzalanır. Her iki durumda da telefona kurulabilir bir APK çıkar.
    val anahtarDosyasi = rootProject.file(
        System.getenv("PETKIT_KEYSTORE_FILE") ?: "keystore/petkit-tr.jks"
    )
    val anahtarVar = anahtarDosyasi.exists()

    signingConfigs {
        if (anahtarVar) {
            create("kisisel") {
                storeFile = anahtarDosyasi
                storePassword = System.getenv("PETKIT_KEYSTORE_PASSWORD") ?: "petkit2026"
                keyAlias = System.getenv("PETKIT_KEY_ALIAS") ?: "petkittr"
                keyPassword = System.getenv("PETKIT_KEY_PASSWORD")
                    ?: System.getenv("PETKIT_KEYSTORE_PASSWORD") ?: "petkit2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (anahtarVar) signingConfigs.getByName("kisisel")
            else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
