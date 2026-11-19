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
        // CI sets GITHUB_RUN_NUMBER, so every build gets a higher version (0.5.<run>) and installs over the previous one.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 500 + run
        versionName = "0.5.$run"
    }
    // One fixed key for every build, so a new APK installs over the old one (a fresh random key each build would say "App not installed").
    signingConfigs {
        create("capri") {
            // Your own private key (GitHub secrets, see README "Signing") if set; otherwise the key in the repo, which is PUBLIC:
            // fine for testing, but anyone could sign an app that installs over yours.
            val own = System.getenv("CAPRI_KEYSTORE_FILE")
            storeFile = if (own != null && file(own).exists()) file(own) else file("capri.keystore")
            storePassword = System.getenv("CAPRI_KEYSTORE_PASSWORD") ?: "capri-mobile"
            keyAlias = System.getenv("CAPRI_KEY_ALIAS") ?: "capri"
            keyPassword = System.getenv("CAPRI_KEY_PASSWORD") ?: System.getenv("CAPRI_KEYSTORE_PASSWORD") ?: "capri-mobile"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("capri") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("capri")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // On-device models (.litertlm files), Google's LiteRT-LM runtime
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.0")
    // Runs Kokoro (the Windows version's voice) on the phone
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // The runtime's bytecode needs a newer coroutines than its POM says
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
