plugins {
    // Kotlin compilation is built into AGP 9.0+ — no separate Kotlin plugin needed.
    id("com.android.application")
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

    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
