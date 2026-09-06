kotlin {
    compilerOptions {
        extraWarnings.set(true)
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 37

    defaultConfig {
        applicationId = "com.geode.launcher"
        minSdk = 23
        targetSdk = 36
        versionCode = 30
        versionName = "1.8.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }

        //noinspection ChromeOsAbiSupport (not my fault)
        ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }

    splits {
        abi {
            isEnable = !project.hasProperty("disableAbiSplits")
            reset()

            //noinspection ChromeOsAbiSupport. i'm sorry!
            include("arm64-v8a", "armeabi-v7a")

            isUniversalApk = true
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("standard") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "GOOGLE_PLAY_BUILD", "false")
        }

        create("googlePlay") {
            dimension = "distribution"
            applicationId = "com.geode.launcher.play"
            buildConfigField("boolean", "GOOGLE_PLAY_BUILD", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // enables a polyfill for java Instant on api levels < 26 (used for updater)
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    externalNativeBuild {
        cmake {
            version = "3.21.0+"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    namespace = "com.geode.launcher"
    ndkVersion = "29.0.14206865"
}

dependencies {
    implementation (platform("androidx.compose:compose-bom:2026.08.00"))
    implementation ("androidx.core:core-ktx:1.19.0")
    implementation ("androidx.compose.ui:ui")
    implementation ("androidx.compose.material3:material3")
    implementation ("androidx.compose.ui:ui-tooling-preview")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation ("androidx.activity:activity-compose:1.13.0")
    implementation ("androidx.activity:activity-ktx:1.13.0")
    implementation ("androidx.appcompat:appcompat:1.8.0")
    implementation ("androidx.documentfile:documentfile:1.1.0")

    implementation ("com.squareup.okio:okio:3.18.2")
    implementation ("com.squareup.okhttp3:okhttp:5.5.0")
    implementation ("com.squareup.okhttp3:okhttp-coroutines:5.5.0")

    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.11.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")

    implementation ("com.mikepenz:multiplatform-markdown-renderer-android:0.45.0")
    implementation ("com.mikepenz:multiplatform-markdown-renderer-m3:0.45.0")
    implementation ("androidx.browser:browser:1.10.0")
    debugImplementation ("androidx.compose.ui:ui-tooling")
    coreLibraryDesugaring ("com.android.tools:desugar_jdk_libs:2.1.5")
}
