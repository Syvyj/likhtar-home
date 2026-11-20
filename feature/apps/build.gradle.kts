plugins {
    id("likhtar.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.likhtar.home.feature.apps"

    // Порожні flavor-и лише для узгодження варіантів: модуль тягне :core:catalog.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    // Кадр шапки: хіт сервісу з його сторінки в Кінозалі й «Продовжити» MEGOGO.
    implementation(project(":core:catalog"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
}
