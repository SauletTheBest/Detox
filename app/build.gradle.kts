plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP = code generator. Room reads our @Entity/@Dao classes and writes the SQL glue code.
    alias(libs.plugins.ksp)
}

// Room saves a JSON description of every database version into app/schemas/
// (open it: you'll see the exact CREATE TABLE statements). Needed later for migrations.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.detox.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.detox.app"
        minSdk = 26
        targetSdk = 37
        // versionCode: the number Android compares for updates (must only go up).
        // versionName: what people see. 0.8.0 = "Detox Home" (two-page strict launcher).
        versionCode = 8
        versionName = "0.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8: shrinks and optimizes the whole app (our code + Compose + Kotlin) -> smoother app.
            // (The template's packageScope = "libraries only" mode produced a startup crash:
            //  IllegalAccessError between two optimized classes. Whole-app mode is consistent.)
            optimization {
                enable = true
            }
            // Personal app, installed by us over USB: sign the release build with the same
            // local debug key, so it installs as an UPDATE over the debug build (data kept).
            // A Play Store release would use your own private key instead.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}