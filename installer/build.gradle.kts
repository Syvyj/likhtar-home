import java.util.Properties

// Застосунок для телефона: знаходить телевізор у мережі й ставить на нього Ліхтар через ADB.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.likhtar.installer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.likhtar.installer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Той самий ключ, що й у лаунчера (`.env` або `-P`); без нього — debug-підпис.
    val localEnv = Properties().apply { rootProject.file(".env").takeIf { it.isFile }?.inputStream()?.use(::load) }
    val env = { key: String -> (findProperty(key) as String?) ?: localEnv.getProperty(key).orEmpty() }
    val releaseKeystore = env("LIKHTAR_KEYSTORE").takeIf { it.isNotBlank() }?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = env("LIKHTAR_KEYSTORE_PASSWORD")
                keyAlias = env("LIKHTAR_KEY_ALIAS")
                keyPassword = env("LIKHTAR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:adb"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
