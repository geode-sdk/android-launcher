val composeBOM: String by rootProject.extra

kotlin {
    compilerOptions {
        extraWarnings.set(true)
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.geode.launcher"
        minSdk = 23
        targetSdk = 36
        versionCode = 21
        versionName = "1.6.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments("-DUSE_TULIPHOOK:BOOL=OFF", "-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES:BOOL=OFF")
            }
        }

        //noinspection ChromeOsAbiSupport (not my fault)
        ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }

    splits {
        abi {
            isEnable = true
            reset()

            //noinspection ChromeOsAbiSupport. i'm sorry!
            include("arm64-v8a", "armeabi-v7a")

            isUniversalApk = true
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

        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
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
            version = "3.25.0+"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    namespace = "com.geode.launcher"
    ndkVersion = "28.1.13356709"
}

dependencies {
    implementation (platform("androidx.compose:compose-bom:$composeBOM"))
    implementation ("androidx.core:core-ktx:1.16.0")
    implementation ("androidx.compose.ui:ui")
    implementation ("androidx.compose.material3:material3")
    implementation ("androidx.compose.ui:ui-tooling-preview")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation ("androidx.activity:activity-compose:1.10.1")
    implementation ("androidx.activity:activity-ktx:1.10.1")
    implementation ("androidx.appcompat:appcompat:1.7.0")
    implementation ("androidx.documentfile:documentfile:1.0.1")
    implementation ("com.squareup.okio:okio:3.11.0")
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.8.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation ("com.mikepenz:multiplatform-markdown-renderer-android:0.33.0")
    implementation ("com.mikepenz:multiplatform-markdown-renderer-m3:0.33.0")
    implementation ("androidx.browser:browser:1.8.0")
    debugImplementation ("androidx.compose.ui:ui-tooling")
    coreLibraryDesugaring ("com.android.tools:desugar_jdk_libs:2.1.5")
}
