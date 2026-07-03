plugins {
    // AGP 9.0+ has built-in Kotlin support (runtime dependency on Kotlin Gradle
    // Plugin 2.2.10+) — the separate org.jetbrains.kotlin.android plugin must
    // NOT be applied; it is incompatible with AGP 9's new DSL.
    // See https://kotl.in/gradle/agp-built-in-kotlin
    id("com.android.application") version "9.2.1" apply false
    // Declared here (not in app/) so it shares a classloader with the Hilt
    // plugin below — see https://github.com/google/dagger/issues/3965.
    id("com.google.devtools.ksp") version "2.3.9" apply false
    // Both Kotlin compiler plugins must match AGP 9.2.1's embedded Kotlin (2.2.20)
    // — re-verify with `./gradlew :app:dependencies | grep kotlin-stdlib` after
    // any AGP upgrade.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.google.dagger.hilt.android") version "2.60" apply false
    // Applied conditionally in app/build.gradle.kts — google-services.json is
    // gitignored, so CI builds (unit tests only) must not require it.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
