plugins {
    id("likhtar.android.library")
}

android {
    namespace = "com.likhtar.home.core.data"

    // Той самий вимір, що й в app: у src/personal — те, чого в збірці для магазину немає
    // зовсім (TorrServer, сторонні плеєри); у src/play — порожні двійники.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }
}

dependencies {
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
