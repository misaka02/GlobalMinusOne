plugins { id("com.android.library") }

group = "com.github.topjohnwu.libsu"
version = "6.0.0-local"

android {
    namespace = "com.topjohnwu.superuser"
    compileSdk = 36
    defaultConfig {
        minSdk = 19
        consumerProguardFiles("proguard-rules.pro")
    }
    buildFeatures { buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    sourceSets["main"].java.srcDirs("../upstream-src/libsu-6.0.0/core/src/main/java")
    sourceSets["main"].manifest.srcFile("../upstream-src/libsu-6.0.0/core/src/main/AndroidManifest.xml")
}
dependencies {
    compileOnly(files("../../../shizuku-13.1.5/annotation-1.3.0.jar"))
}



