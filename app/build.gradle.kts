plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

fun gitCommitCount(): Int = try {
    ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir).start()
        .inputStream.bufferedReader().readLine().trim().toInt()
} catch (_: Exception) { 1 }

fun legacyExportFixVersionCode(): Int = maxOf(gitCommitCount(), 204)

android {
    namespace = "se.joynes.aiterminal"
    compileSdk = 36
    flavorDimensions += "mode"

    defaultConfig {
        applicationId = "se.joynes.aiterminal"
        minSdk = 24
        targetSdk = 36
        versionCode = legacyExportFixVersionCode()
        versionName = "1.${legacyExportFixVersionCode()}"

        testInstrumentationRunner = "se.joynes.aiterminal.HiltTestRunner"
    }
    productFlavors {
        create("production") {
            dimension = "mode"
            buildConfigField("boolean", "IS_DIAGNOSTIC", "false")
        }
        create("diagnostic") {
            dimension = "mode"
            applicationIdSuffix = ".diag"
            versionNameSuffix = "-diag"
            buildConfigField("boolean", "IS_DIAGNOSTIC", "true")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // SSH
    implementation(libs.sshlib) {
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.jsch)

    // Terminal emulator (local Termux modules, patched for SSH-backed embedded sessions)
    implementation(project(":terminal-emulator"))
    implementation(project(":terminal-view"))

    // Biometric & Security
    implementation(libs.biometric)
    implementation(libs.security.crypto)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.uiautomator)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
