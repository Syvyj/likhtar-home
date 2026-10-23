plugins {
    id("likhtar.android.library.compose")
}

android {
    namespace = "com.likhtar.home.feature.smarthome"

    // Порожні flavor-и лише для узгодження варіантів: модуль тягне :core:data.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:smarthome"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
}
