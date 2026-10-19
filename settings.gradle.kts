pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GolfCaddie"

// Pure-Kotlin logic: geo math, OpenStreetMap parsing, club recommendation.
include(":core")

// Pass -PcoreOnly to build/test the core without the Android SDK.
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
    // Wear OS watch app: shows the phone's distances and club, and can start/finish shots.
    include(":wear")
}
