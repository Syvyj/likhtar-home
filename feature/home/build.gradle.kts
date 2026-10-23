plugins {
    id("likhtar.android.library.compose")
}

android {
    namespace = "com.likhtar.home.feature.home"

    // Порожні flavor-и лише для узгодження варіантів: цей модуль тягне :core:catalog,
    // у якого BuildConfig різний для personal і play.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:catalog"))
    implementation(project(":core:data"))
    implementation(project(":core:tv"))
    implementation(project(":core:smarthome"))
    implementation(project(":core:wallpaper"))

    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
}
