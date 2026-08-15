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

rootProject.name = "Dora"

include(":app")

include(":poc:capture")

include(":poc:search")

include(":poc:recovery")

includeBuild("poc/vpn-contract-kernel")

include(":core:common")

include(":core:model")

include(":core:testing")
