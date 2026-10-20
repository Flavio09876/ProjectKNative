plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    namespace = "br.com.projectk.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.projectk.nativeapp"
        minSdk = 23
        targetSdk = 28
        val b = (project.findProperty("build") as String?)?.toIntOrNull() ?: 1
        versionCode = b
        versionName = "0.1.$b"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.caverock:androidsvg-aar:1.4")
}
