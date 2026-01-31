buildscript {
    val composeBOM by extra("2026.01.01")
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.0.0" apply false
    id("com.android.library") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
