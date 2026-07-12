plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.accessible.toolkit.bridge"
    compileSdk = 34

    defaultConfig {
        minSdk = 29

        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:vosk"))
    implementation(project(":core:vad"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}