@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.maven


pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins { this.id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

rootProject.name = "newpipe-plugin"
include("newpipe")
