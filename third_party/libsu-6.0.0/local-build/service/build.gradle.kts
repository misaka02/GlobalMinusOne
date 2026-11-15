plugins { id("com.android.library") }

group = "com.github.topjohnwu.libsu"
version = "6.0.0-local"

android {
    namespace = "com.topjohnwu.superuser.ipc"
    compileSdk = 36
    defaultConfig { minSdk = 19 }
    buildFeatures { aidl = true; buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    sourceSets["main"].java.srcDirs("../upstream-src/libsu-6.0.0/service/src/main/java")
    sourceSets["main"].aidl.srcDirs("../upstream-src/libsu-6.0.0/service/src/main/aidl")
    sourceSets["main"].manifest.srcFile("../upstream-src/libsu-6.0.0/service/src/main/AndroidManifest.xml")
    sourceSets["main"].assets.srcDirs("../upstream-src/libsu-6.0.0/service/src/main/assets")
}
dependencies {
    api(project(":core"))
    compileOnly(files("../../../shizuku-13.1.5/annotation-1.3.0.jar"))
}



