// Standalone build for the pure Kotlin/JVM modules (:domain and :processing).
//
// It exists so the metadata/processing engine can be compiled and tested on any machine
// with a JDK, even without the Android SDK or access to Google's Maven repository.
// Usage (from the repository root):  ./gradlew -p jvm-build test
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "CleanShot-JVM"

include(":domain")
project(":domain").projectDir = file("../domain")
include(":processing")
project(":processing").projectDir = file("../processing")
