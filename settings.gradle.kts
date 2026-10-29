pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "likhtar-home"

include(":app")
include(":core:model")
include(":core:adb")
include(":core:designsystem")
include(":core:data")
include(":core:wallpaper")
include(":core:smarthome")
include(":core:tv")
include(":core:catalog")
include(":feature:home")
include(":feature:cinema")
include(":feature:search")
include(":feature:apps")
include(":feature:smarthome")
include(":feature:settings")
include(":installer")
