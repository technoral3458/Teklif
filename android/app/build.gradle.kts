plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Once ortam degiskeni (CI gizli degiskenleri), sonra gradle.properties / -P degeri okunur.
fun secret(name: String, fallback: String = ""): String =
    System.getenv(name) ?: (project.findProperty(name) as String?) ?: fallback

android {
    namespace = "com.technoral.teklif"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.technoral.teklif"
        minSdk = 26
        targetSdk = 35
        versionCode = (secret("TEKLIF_VERSION_CODE", "1")).toInt()
        versionName = secret("TEKLIF_VERSION_NAME", "1.0.0")
    }

    val keystoreFile = file(secret("TEKLIF_KEYSTORE_FILE", "../keystore/teklif-release.jks"))

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = secret("TEKLIF_KEYSTORE_PASSWORD")
                keyAlias = secret("TEKLIF_KEY_ALIAS")
                keyPassword = secret("TEKLIF_KEY_PASSWORD")

                // minSdk 26 oldugu icin Gradle eski JAR imzasini kendiliginden kapatiyor.
                // Bazi ureticilerin kurulum servisleri v2-only paketleri reddedip
                // "Uygulama yuklenmedi" hatasi verdigi icin tum semalari aciyoruz.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Anahtar yoksa derleme yine de calissin diye kosullu bagliyoruz.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            // Kisayollar sabit paket adina bagli oldugu icin debug'ta da ayni applicationId kullanilir.
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
}
