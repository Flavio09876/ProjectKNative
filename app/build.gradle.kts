plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.projectk.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.projectk.nativeapp"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }
}
