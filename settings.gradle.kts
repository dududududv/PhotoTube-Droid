pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhotoTube-Droid"
include(":app")

include(":backdrop", ":liquid-ui")
project(":backdrop").projectDir = file("libraries/backdrop")
project(":liquid-ui").projectDir = file("libraries/liquid-ui")
