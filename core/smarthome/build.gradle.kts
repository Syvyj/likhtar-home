plugins {
    id("likhtar.android.library")
}

android {
    namespace = "com.likhtar.home.core.smarthome"

    // Порожні flavor-и лише для узгодження варіантів: модуль тягне :core:data.
    flavorDimensions += "dist"
    productFlavors {
        create("play") { dimension = "dist" }
    }
    buildFeatures { buildConfig = true }

    // Заглушки android.jar (Log і подібні) мають повертати значення за замовчуванням,
    // а не кидати виняток: інакше юніт-тести клієнта падають на логуванні.
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        // Адреса та токен Home Assistant для цієї збірки. Порожні за замовчуванням: у релізі
        // їх задає користувач. Для розробки: -PlikhtarHaUrl=… -PlikhtarHaToken=…
        buildConfigField("String", "DEFAULT_HA_URL", "\"" + likhtarEnv("likhtarHaUrl") + "\"")
        buildConfigField("String", "DEFAULT_HA_TOKEN", "\"" + likhtarEnv("likhtarHaToken") + "\"")
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.mockwebserver)
}
