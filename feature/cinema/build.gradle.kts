plugins {
    id("likhtar.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.likhtar.home.feature.cinema"
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Порожні flavor-и лише для узгодження варіантів: цей модуль тягне :core:catalog,
    // у якого BuildConfig різний для personal і play.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:tv"))
    implementation(project(":core:catalog"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    // Повернення фокуса — єдина повторювана категорія дефектів (PROJECT.md, п. 4): тест на емуляторі.
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
