import java.util.Properties
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    jacoco
}

fun gitCommitCount(): Int = try {
    ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir).start()
        .inputStream.bufferedReader().readLine().trim().toInt()
} catch (_: Exception) { 1 }

fun legacyExportFixVersionCode(): Int = maxOf(gitCommitCount(), 204)

val releaseKeystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("release-keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(name: String): String? =
    releaseKeystoreProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: System.getenv(name)

val hasReleaseSigningConfig = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD"
).all { !releaseSigningProperty(it).isNullOrBlank() }

gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any { it.name.contains("Release") }
    if (needsReleaseSigning && !hasReleaseSigningConfig) {
        throw GradleException(
            "Release signing is not configured. Create release-keystore.properties " +
                "or provide RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
                "RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD."
        )
    }
}

android {
    namespace = "se.joynes.terminalhub"
    compileSdk = 36
    flavorDimensions += "mode"

    defaultConfig {
        applicationId = "se.joynes.terminalhub"
        minSdk = 24
        targetSdk = 36
        versionCode = legacyExportFixVersionCode()
        versionName = "1.${legacyExportFixVersionCode()}"

        testInstrumentationRunner = "se.joynes.terminalhub.HiltTestRunner"
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
            if (hasReleaseSigningConfig) {
                storeFile = file(releaseSigningProperty("RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningProperty("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningProperty("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningProperty("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            signingConfig?.enableV1Signing = true
            signingConfig?.enableV2Signing = true
            signingConfig?.enableV3Signing = true
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
    testCoverage {
        // 0.8.16's ASM operation limit rejects the large Compose terminal screen.
        jacocoVersion = "0.8.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

jacoco { toolVersion = "0.8.14" }

// Keep authored Compose/UI code, coroutine bodies and data classes in the denominator.
// Only Android, Hilt and Room generated classes are excluded.
val appCoverageClasses = fileTree(layout.buildDirectory.dir(
    "intermediates/classes/diagnosticDebug/transformDiagnosticDebugClassesWithAsm/dirs"
)) {
    include("se/joynes/terminalhub/**", "com/trilead/ssh2/**")
    exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Hilt_*", "**/Dagger*",
        "**/*_Factory*", "**/*_MembersInjector*", "**/*_GeneratedInjector*",
        "**/*_HiltModules*", "**/*_HiltComponents*", "**/*_Impl*", "**/HiltTestActivity*")
}

val appCoverageData = fileTree(layout.buildDirectory) {
    include("outputs/unit_test_code_coverage/diagnosticDebugUnitTest/*.exec",
        "outputs/code_coverage/diagnosticDebugAndroidTest/connected/**/*.ec")
}

tasks.register<JacocoReport>("appCoverageReport") {
    group = "verification"
    description = "Merge diagnostic JVM and emulator coverage for all authored app code."
    dependsOn("testDiagnosticDebugUnitTest", "createDiagnosticDebugCoverageReport")
    executionData.setFrom(appCoverageData)
    classDirectories.setFrom(appCoverageClasses)
    sourceDirectories.setFrom(files("src/main/java", "src/diagnostic/java"))
    doFirst {
        check(appCoverageClasses.files.isNotEmpty()) { "No app classes found; coverage cannot be reported." }
        check(appCoverageData.files.any { it.extension == "exec" && it.length() > 0 } &&
            appCoverageData.files.any { it.extension == "ec" && it.length() > 0 }) {
            "Both JVM and emulator execution data are required for combined app coverage."
        }
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/appCoverageReport/report.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/appCoverageReport/html"))
    }
}

tasks.register<JacocoCoverageVerification>("appCoverageVerification") {
    group = "verification"
    description = "Require at least 95% line coverage across authored app code."
    dependsOn("appCoverageReport")
    executionData.setFrom(appCoverageData)
    classDirectories.setFrom(appCoverageClasses)
    sourceDirectories.setFrom(files("src/main/java", "src/diagnostic/java"))
    violationRules {
        rule {
            limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
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
    implementation(libs.snakeyaml)

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
