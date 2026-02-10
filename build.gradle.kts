// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Declaramos la serialización aquí con una versión estable [cite: 2026-01-31]
    kotlin("plugin.serialization") version "2.0.0" apply false
}