import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.likhtar.home"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.likhtar.home"
        minSdk = 24
        targetSdk = 36
        // Номер збірки — кількість комітів: росте з кожною правкою, тож новіша збірка завжди
        // «новіша» для системи. Без git (архів) — 1.
        versionCode = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)
        versionName = "0.1.0-alpha01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // personal — збірка для себе й для бети, play — те, що йде в стор. Різниця не в
    // «урізанні фіч», а в тому, що play не містить коду й рядків про сторонні плеєри
    // взагалі: вони живуть тільки в джерелах personal (app/src/personal).
    flavorDimensions += "dist"
    productFlavors {
        create("play") {
            dimension = "dist"
            // Публічні випуски нумеруються від версії, а не від комітів: публічний репозиторій
            // має власну, коротку історію, і з нього номер збірки впав би нижче вже виданого.
            // 0.1.0 → 100, 0.1.1 → 101, 0.2.0 → 200.
            versionName = "0.1.3"
            versionCode = 103
        }
    }

    // Справжній ключ — поза репозиторієм: шлях і паролі з `.env` або `-P` (LIKHTAR_KEYSTORE,
    // LIKHTAR_KEYSTORE_PASSWORD, LIKHTAR_KEY_ALIAS, LIKHTAR_KEY_PASSWORD; README, «Building»).
    // Без них release підписується debug-ключем, як і досі, — збірку можна ставити поверх
    // тієї, що вже стоїть. Зміна ключа вимагає перевстановлення начисто.
    // Той самий `.env`, що читає build-logic; тут його плагін не застосований, тож читаємо самі.
    val localEnv = Properties().apply { rootProject.file(".env").takeIf { it.isFile }?.inputStream()?.use(::load) }
    val env = { key: String -> (findProperty(key) as String?) ?: localEnv.getProperty(key).orEmpty() }
    val releaseKeystore = env("LIKHTAR_KEYSTORE").takeIf { it.isNotBlank() }?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = env("LIKHTAR_KEYSTORE_PASSWORD")
                keyAlias = env("LIKHTAR_KEY_ALIAS")
                keyPassword = env("LIKHTAR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:wallpaper"))
    implementation(project(":core:catalog"))
    // Потрібен рівно для одного шва: `:core:tv` оголошує `EpgServer`, а адресу знає
    // `:core:catalog`. Зшивається тут, щоб не тягти каталог (а з ним вимір флейворів)
    // у модуль телебачення й далі в чотири фічі.
    implementation(project(":core:tv"))
    implementation(project(":core:smarthome"))
    testImplementation(libs.junit)
    implementation(project(":feature:home"))
    implementation(project(":feature:cinema"))
    implementation(project(":feature:search"))
    implementation(project(":feature:apps"))
    implementation(project(":feature:smarthome"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    ksp(libs.hilt.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.androidx.tv.material)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}
