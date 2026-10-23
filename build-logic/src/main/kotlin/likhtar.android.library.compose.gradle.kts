plugins {
    id("likhtar.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures { compose = true }
}

// Моделі каталогу — стабільні для Compose (див. compose_stability.conf у корені).
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}
