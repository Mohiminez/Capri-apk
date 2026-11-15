plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.capri.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.capri.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // On-device models (.litertlm files), Google's LiteRT-LM runtime
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.0")
    // The runtime's bytecode needs a newer coroutines than its POM says
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
