plugins {
    id("likhtar.android.library")
}

android {
    namespace = "com.likhtar.home.core.tv"

    // Порожні flavor-и лише для узгодження варіантів: модуль тягне :core:data.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }

    // Заглушки android.jar (Log, JsonReader) мають повертати значення за замовчуванням,
    // а не кидати виняток — інакше юніт-тести розбору падають на логуванні.
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}
