plugins {
    id("likhtar.android.library.compose")
}

android {
    namespace = "com.likhtar.home.core.designsystem"
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

dependencies {
    api(project(":core:model"))

    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.androidx.tv.material)
    api(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.androidx.activity.compose)
    // Тільки генерація матриці QR — камери й сканера не тягне.
    implementation(libs.zxing.core)
    api(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // Правила рядів (вхід на першу картку, убік не виходити) — тест на емуляторі.
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
