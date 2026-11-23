plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.capri.mobile"
    compileSdk = 35

    androidResources { noCompress += "nnue" }   // the 40 MB network is already dense; skip the slow compression

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
    // One small APK per CPU type instead of one giant APK carrying four copies of the AI runtimes (arm64, 32-bit arm, x86, x86_64).
    // Nearly every phone is arm64; older or 32-bit-only phones use the arm32 file.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    packaging {
        // Compress the native libraries inside the APK: a smaller download (they are unpacked on install).
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/*.kotlin_module", "/META-INF/LICENSE*", "/META-INF/NOTICE*", "/META-INF/DEPENDENCIES", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        }
    }
    // Stockfish 11 (C++), built by the NDK into libstockfish.so. Skip it with  -PwithStockfish=false  (the app then plays with its own engine).
    if (project.findProperty("withStockfish") != "false") {
        defaultConfig {
            externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_static") } }
        }
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
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
