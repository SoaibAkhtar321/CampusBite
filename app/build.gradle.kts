import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.firebase.crashlytics)

    kotlin("kapt")
}

// ─────────────────────────────────────────────────────────────────
//  Release signing configuration
//
//  Secrets are NEVER hardcoded here. They are loaded from either:
//   1) A local, git-ignored `key.properties` file at the project
//      root (developer machines), or
//   2) Environment variables (CI/CD pipelines):
//        RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
//        RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
//
//  If neither source provides complete values, the release
//  signingConfig block below intentionally throws — this fails the
//  Gradle build loudly instead of silently producing an unsigned
//  or debug-signed release artifact.
// ─────────────────────────────────────────────────────────────────
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
val hasKeystorePropertiesFile = keystorePropertiesFile.exists()
if (hasKeystorePropertiesFile) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun releaseConfigValue(propertyKey: String, envKey: String): String? {
    return keystoreProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
}

val releaseStoreFilePath = releaseConfigValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = releaseConfigValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseConfigValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseConfigValue("keyPassword", "RELEASE_KEY_PASSWORD")

val isReleaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

// Only invoked by Gradle when a release variant is actually being
// assembled/bundled, so `assembleDebug` / unit tests never require
// signing secrets to be present.
gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any { task ->
        task.name.contains("Release", ignoreCase = false) &&
                (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
    }
    if (buildingRelease && !isReleaseSigningConfigured) {
        throw GradleException(
            "Release signing is not configured.\n" +
                    "Provide storeFile, storePassword, keyAlias, keyPassword either via:\n" +
                    "  1) a git-ignored 'key.properties' file at the project root, or\n" +
                    "  2) environment variables RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
                    "RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD.\n" +
                    "See key.properties.example for the expected format."
        )
    }
}

android {
    namespace = "com.campusbite.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.campusbite.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (isReleaseSigningConfigured) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            // Intentionally unchanged — debug App Check provider and
            // debug workflow are out of scope for this task.
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // Firebase App Check
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Google Sign-In / Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // QR Code
    implementation("com.google.zxing:core:3.5.3")
}