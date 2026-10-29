// Без Hilt (на відміну від likhtar.android.library): модулем користується й інсталятор для телефона.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.likhtar.home.core.adb"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
