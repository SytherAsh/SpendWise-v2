plugins {
    // Kotlin compilation is built into AGP 9.0+ — no separate Kotlin plugin needed.
    id("com.android.application")
    // KSP hooks into AGP's built-in Kotlin compilation directly; it does not
    // require (and is not blocked by) the org.jetbrains.kotlin.android plugin.
    id("com.google.devtools.ksp") version "2.3.9"
}

android {
    namespace = "com.spendwise"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spendwise"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // src/main/kotlin and src/test/kotlin are recognized by default — no
    // explicit sourceSets block needed.

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.work:work-runtime-ktx:2.11.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.work:work-testing:2.11.2")
}
