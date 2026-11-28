import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

// The CI runner sets MT_DEBUG_KEYSTORE to one cached key, which is what lets an APK install
// over the last one; a local build without the variable keeps Gradle's own debug key.
val sharedDebugKeystore: File? = System.getenv("MT_DEBUG_KEYSTORE")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }

// A real distribution key, when one is supplied, outranks the shared CI key.
val releaseKeystore = System.getenv("KEYSTORE_PATH")
val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val hasReleaseKeystore = !releaseKeystore.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank()

android {
    namespace = "com.manhwatranslate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.manhwatranslate"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.2.1"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword ?: releaseStorePassword
            }
        }
        if (sharedDebugKeystore != null) {
            create("sharedDebug") {
                storeFile = sharedDebugKeystore
                storePassword = System.getenv("MT_DEBUG_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("MT_DEBUG_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("MT_DEBUG_KEYSTORE_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            if (sharedDebugKeystore != null) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
        }
        release {
            signingConfig = when {
                hasReleaseKeystore -> signingConfigs.getByName("release")
                sharedDebugKeystore != null -> signingConfigs.getByName("sharedDebug")
                else -> signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // llama.cpp ships native libraries for every desktop OS inside the JAR; Android only
            // needs the ABI lib it loads via System.loadLibrary, so drop the desktop ones.
            excludes += "de/kherud/llama/Mac/**"
            excludes += "de/kherud/llama/Windows/**"
            excludes += "de/kherud/llama/Linux/x86_64/**"
            excludes += "de/kherud/llama/Linux/aarch64/**"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.exifinterface)
    implementation(libs.google.play.services.auth)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.translate)

    implementation(libs.llama)
    implementation(libs.onnxruntime.android)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
