// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // Essential for Firebase to work
        classpath("com.google.gms:google-services:4.4.2")
    }
}

plugins {
    // Upgraded to 8.6.0 to satisfy core-splashscreen:1.2.0 requirements
    id("com.android.application") version "8.6.0" apply false
    id("com.android.library") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
