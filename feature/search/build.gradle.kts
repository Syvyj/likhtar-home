plugins {
    id("likhtar.android.library.compose")
}

android {
    namespace = "com.likhtar.home.feature.search"

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
    implementation(project(":core:catalog"))
    // Наші шоу в пошуку: портальні позиції TMDB-id не мають, і без цього модуля їх
    // у видачі не було зовсім.
    implementation(project(":core:tv"))
    // Банер найкращого збігу — той самий TitleSpotlight, що в Кінозалі й Реаліті.
    implementation(project(":feature:cinema"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
