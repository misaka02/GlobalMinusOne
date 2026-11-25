import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.globalminusone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.globalminusone"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 116
        versionName = "2.4.110"
    }

    androidResources {
        localeFilters += listOf("zh", "en")
    }

    val releaseSigningProps = Properties().apply {
        val propFile = rootProject.file("release-signing.properties")
        if (propFile.isFile) propFile.inputStream().use(::load)
    }
    val signingStorePath = releaseSigningProps.getProperty("storeFile")
        ?: System.getenv("GMO_SIGNING_STORE_FILE")
    val signingStorePass = releaseSigningProps.getProperty("storePassword")
        ?: System.getenv("GMO_SIGNING_STORE_PASSWORD")
    val signingKeyAlias = releaseSigningProps.getProperty("keyAlias")
        ?: System.getenv("GMO_SIGNING_KEY_ALIAS")
    val signingKeyPass = releaseSigningProps.getProperty("keyPassword")
        ?: System.getenv("GMO_SIGNING_KEY_PASSWORD")
    val hasCustomReleaseSigning = !signingStorePath.isNullOrBlank() &&
        !signingStorePass.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPass.isNullOrBlank()

    val compatibleDebugKeystore = System.getenv("GMO_COMPAT_DEBUG_KEYSTORE")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isFile }

    signingConfigs {
        if (compatibleDebugKeystore != null) {
            getByName("debug") {
                storeFile = compatibleDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (hasCustomReleaseSigning) {
            create("release") {
                storeFile = File(signingStorePath!!).let {
                    if (it.isAbsolute) it else rootProject.file(signingStorePath)
                }
                storePassword = signingStorePass
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasCustomReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "/META-INF/version-control-info.textproto",
                "/kotlin/**"
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

composeCompiler {
    if (providers.environmentVariable("GMO_COMPOSE_METRICS").orNull == "1" ||
        providers.gradleProperty("enableComposeCompilerReports").orNull == "true"
    ) {
        val outDir = layout.buildDirectory.dir("compose-metrics")
        reportsDestination.set(outDir)
        metricsDestination.set(outDir)
    }
}

// Keep the forked test JVM small alongside the in-process Kotlin compiler inside the guard.
tasks.withType<Test>().configureEach {
    minHeapSize = "32m"
    maxHeapSize = "256m"
    maxParallelForks = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=64m", "-XX:ActiveProcessorCount=2")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Offline-pinned Google RE2/J 1.8; provenance and license: third_party/re2j-1.8/.
    implementation(files("libs/re2j-1.8.jar"))
    // Compile-only Xposed API 82 ABI stub (provided at runtime by LSPosed; never packaged).
    compileOnly(files("libs/xposed-api-82-stub.jar"))

    // Pinned upstream sources/artifacts and reproducible local libsu build; see third_party/.
    implementation(fileTree("../third_party/shizuku-13.1.5") { include("*.aar") })
    implementation(fileTree("../third_party/libsu-6.0.0/local-build/output") { include("libsu-*-locally-built.aar") })

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components & Serialization
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)

    // Compose UI & Material3 (without bloated material-icons-extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Tooling & Tests
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
