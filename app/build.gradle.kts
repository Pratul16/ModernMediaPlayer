import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pratul.mmplayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pratul.mmplayer"
        // API 29 is the first release with MediaStore.RELATIVE_PATH and ContentResolver.loadThumbnail,
        // which the library and thumbnail pipeline are built on.
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Play Store upload key. keystore.properties and the .jks file stay on this computer only
    // (both are git-ignored); see PLAY_STORE.md for how to create them.
    val keystoreFile = rootProject.file("keystore.properties")
    val uploadKey = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("upload") {
                storeFile = rootProject.file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // Without an upload key, fall back to the debug key so release builds can still be
            // installed for testing (Play Console rejects those).
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // libVLC and FFmpeg ship native code for four CPU types (~50 MB each). Build one APK per CPU type
    // so a phone only downloads its own. (Play Store App Bundles do this automatically.)
    // Off for App Bundle builds, which split by CPU on their own (and fail with APK splits on).
    val buildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !buildingBundle
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            // Plus one APK that runs on any phone, for sharing directly.
            isUniversalApk = true
        }
    }
    packaging {
        jniLibs {
            // Store native libraries compressed: much smaller APK download for sideloading.
            useLegacyPackaging = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Playback engine.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    // Software audio decoders (AC-3, E-AC-3, DTS, TrueHD, ...) for formats phones lack in hardware.
    implementation(libs.media3.ffmpeg.decoder)
    // Fallback engine for containers/codecs Media3 cannot handle (WMV, RMVB, DivX, MPEG-2, ...).
    implementation(libs.libvlc.all)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
