rootProject.name = "aegis-desktop"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // Compose Multiplatform's Gradle plugin and the androidx artifacts it pulls in.
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":core")
include(":platform")
include(":desktop")
