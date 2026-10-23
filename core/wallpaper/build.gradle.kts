plugins {
    id("likhtar.android.library")
}

android {
    namespace = "com.likhtar.home.core.wallpaper"

    // Порожні flavor-и лише для узгодження варіантів: модуль тягне :core:data.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }
    buildFeatures { buildConfig = true }

    defaultConfig {
        // Типовий URL маніфесту шпалер для цієї збірки. У релізі порожній; для перевірки
        // передається як -PlikhtarWallpaperManifest=http://…
        buildConfigField(
            "String",
            "DEFAULT_WALLPAPER_MANIFEST_URL",
            "\"" + likhtarEnv("likhtarWallpaperManifest") + "\""
        )
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
