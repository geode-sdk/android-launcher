import java.net.URI
import java.util.zip.ZipInputStream

val composeBOM: String by rootProject.extra

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

val currentGeodeVersion = "5.8.2"

abstract class DownloadAndUnzipGeodeTask : DefaultTask() {
    @get:Input
    abstract val geodeVersion: Property<String>

    @get:Input
    abstract val platform: Property<String>

    @get:OutputFile
    abstract val targetFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val downloadGeodeVersion = geodeVersion.get()
        val downloadPlatform = platform.get()
        val url = "https://github.com/geode-sdk/geode/releases/download/v$downloadGeodeVersion/geode-v$downloadGeodeVersion-$downloadPlatform.zip"
        val destinationFile = targetFile.get().asFile

        val expectedFile = "Geode.$downloadPlatform.so"

        logger.info("Downloading Geode v$downloadGeodeVersion for platform $downloadPlatform")

        ZipInputStream(URI.create(url).toURL().openStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == expectedFile) {
                    destinationFile.outputStream().use {
                        zip.copyTo(it)
                    }
                    return@execute
                }

                entry = zip.nextEntry
            }

            throw GradleException("Could not find Geode release.")
        }
    }
}

val downloadGeode32Task = tasks.register<DownloadAndUnzipGeodeTask>("downloadGeode32Binary") {
    description = "Downloads the 32bit Geode release archive"
    inputs.property("geodeVersion", currentGeodeVersion)

    geodeVersion = currentGeodeVersion
    platform = "android32"
    targetFile = temporaryDir.resolve("libgeode.so")
}

val downloadGeode64Task = tasks.register<DownloadAndUnzipGeodeTask>("downloadGeode64Binary") {
    description = "Downloads the 64bit Geode release archive"
    inputs.property("geodeVersion", currentGeodeVersion)

    geodeVersion = currentGeodeVersion
    platform = "android64"
    targetFile = temporaryDir.resolve("libgeode.so")
}

abstract class MergeGeodeFilesTask : DefaultTask() {
    @get:InputFile
    abstract val binary32: RegularFileProperty

    @get:InputFile
    abstract val binary64: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        binary32.get().asFile.copyTo(outputDir.file("armeabi-v7a/libgeode.so").get().asFile)
        binary64.get().asFile.copyTo(outputDir.file("arm64-v8a/libgeode.so").get().asFile)
    }
}

val fullDownloadTask = tasks.register<MergeGeodeFilesTask>("downloadGeodeFull") {
    description = "Downloads both Geode releases"

    binary32 = downloadGeode32Task.flatMap { it.targetFile }
    binary64 = downloadGeode64Task.flatMap { it.targetFile }
    outputDir = layout.buildDirectory.dir("geode-prebuilt")
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.geode.launcher"
        minSdk = 23
        targetSdk = 36
        versionCode = 29
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
            buildConfigField("String", "PREBUNDLED_GEODE", "null")
        }

        create("googlePlay") {
            dimension = "distribution"
            applicationId = "com.geode.launcher.play"
            buildConfigField("boolean", "GOOGLE_PLAY_BUILD", "true")
            buildConfigField("String", "PREBUNDLED_GEODE", "\"$currentGeodeVersion\"")
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

androidComponents {
    onVariants(selector().withFlavor("distribution" to "googlePlay")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            fullDownloadTask,
            MergeGeodeFilesTask::outputDir
        )
    }
}

dependencies {
    implementation (platform("androidx.compose:compose-bom:$composeBOM"))
    implementation ("androidx.core:core-ktx:1.18.0")
    implementation ("androidx.compose.ui:ui")
    implementation ("androidx.compose.material3:material3")
    implementation ("androidx.compose.ui:ui-tooling-preview")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation ("androidx.activity:activity-compose:1.13.0")
    implementation ("androidx.activity:activity-ktx:1.13.0")
    implementation ("androidx.appcompat:appcompat:1.7.1")
    implementation ("androidx.documentfile:documentfile:1.1.0")

    implementation ("com.squareup.okio:okio:3.17.0")
    implementation ("com.squareup.okhttp3:okhttp:5.3.2")
    implementation ("com.squareup.okhttp3:okhttp-coroutines:5.3.2")

    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.10.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    implementation ("com.mikepenz:multiplatform-markdown-renderer-android:0.39.2")
    implementation ("com.mikepenz:multiplatform-markdown-renderer-m3:0.39.2")
    implementation ("androidx.browser:browser:1.10.0")
    debugImplementation ("androidx.compose.ui:ui-tooling")
    coreLibraryDesugaring ("com.android.tools:desugar_jdk_libs:2.1.5")
}
