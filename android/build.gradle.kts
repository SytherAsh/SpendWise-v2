plugins {
    // AGP 9.0+ has built-in Kotlin support (runtime dependency on Kotlin Gradle
    // Plugin 2.2.10+) — the separate org.jetbrains.kotlin.android plugin must
    // NOT be applied; it is incompatible with AGP 9's new DSL.
    // See https://kotl.in/gradle/agp-built-in-kotlin
    id("com.android.application") version "9.2.1" apply false
}
