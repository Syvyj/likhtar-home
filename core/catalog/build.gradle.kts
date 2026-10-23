plugins {
    id("likhtar.android.library")
}

android {
    namespace = "com.likhtar.home.core.catalog"
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        // Ключ TMDB і адреса Ліхтаря для цієї збірки. Порожні за замовчуванням: у релізі їх
        // задає користувач у налаштуваннях. Для розробки:
        //   -PlikhtarTmdbKey=… -PlikhtarSourceUrl=http://<server>:18118
        // Локальні ключі не потрапляють у Git: розробницький `.env` може задавати їх без
        // окремого -P прапорця (likhtarEnv дивиться спершу -P, тоді .env).
        buildConfigField(
            "String",
            "DEFAULT_TMDB_KEY",
            "\"" + likhtarEnv("likhtarTmdbKey", "TMDB_key", "TMDB_KEY") + "\""
        )
        buildConfigField("String", "DEFAULT_LIKHTAR_URL", "\"" + likhtarEnv("likhtarSourceUrl") + "\"")
        // «Де подивитися» і стріми: у play порожні (LAN-адреси домашнього сервера не
        // мають потрапляти в сторову збірку), домашні дефолти — у flavor personal нижче.
        buildConfigField("String", "DEFAULT_RESOLVER_URL", "\"" + likhtarEnv("likhtarResolverUrl") + "\"")
        buildConfigField("String", "DEFAULT_LIKHTAR_STREAMS", "\"" + likhtarEnv("likhtarStreamsUrl") + "\"")
    }

    // Той самий вимір, що й в app: play — сторова збірка, personal — своя.
    flavorDimensions += "dist"
    productFlavors {
        create("play") {
            dimension = "dist"
            // Збірка для магазину — без ключа TMDB в APK: запити йдуть через проксі нашого
            // сервера (/tmdb/api/3), він і підставляє ключ.
            buildConfigField("String", "DEFAULT_TMDB_KEY", "\"\"")
            // Наш сервер для всіх, хто ставить лаунчер із сайту: публічний шлюз Cloudflare —
            // лише каталог, лише читання. Без нього публічна збірка не мала куди ходити:
            // порожній Кінозал.
            val publicServer = (project.findProperty("likhtarPublicUrl") as String?)
                ?: "https://likhtar.syvyj.dev"
            buildConfigField("String", "DEFAULT_LIKHTAR_STREAMS", "\"" + publicServer + "\"")
            buildConfigField("String", "DEFAULT_RESOLVER_URL", "\"" + publicServer + "\"")
        }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.smbj)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}
