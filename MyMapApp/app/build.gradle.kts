import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.mapapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mapapp.v2"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Make API Key available in BuildConfig
        buildConfigField("String", "MAPTILER_KEY", "\"${localProperties.getProperty("MAPTILER_KEY") ?: ""}\"")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("my-release-key.jks")
            storePassword = "password"
            keyAlias = "my-key-alias"
            keyPassword = "password"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // MapLibre GL Native
    implementation("org.maplibre.gl:android-sdk:11.5.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // Network & Data
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.mobilitydata:gtfs-realtime-bindings:0.0.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0") // JSON Serialization
    
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2") // ViewModel support
    implementation("androidx.activity:activity-ktx:1.8.0") // by viewModels() support
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
}
