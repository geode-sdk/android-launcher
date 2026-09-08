import groovy.json.JsonSlurper
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

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

val currentGeodeVersion = "5.10.1"

abstract class DownloadVersionMetadataTask : DefaultTask() {
    @get:Input
    abstract val geodeVersion: Property<String>

    @get:OutputFile
    abstract val targetFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val downloadGeodeVersion = geodeVersion.get()
        val destinationFile = targetFile.get().asFile

        val releaseUrl = "https://api.geode-sdk.org/v1/loader/versions/$downloadGeodeVersion"
        URI.create(releaseUrl).toURL().openStream().use { response ->
            destinationFile.outputStream().use { destination ->
                response.copyTo(destination)
            }
        }
    }
}

abstract class DownloadGeodeFileTask : DefaultTask() {
    @get:Input
    abstract val platform: Property<String>

    @get:InputFile
    abstract val metadata: RegularFileProperty

    private fun downloadAndVerifyFile(filename: String, url: String, hash: String?): File {
        logger.info("Downloading $filename from $url")

        val tempFile = File(temporaryDir, filename)

        URI.create(url).toURL().openStream().use { response ->
            tempFile.outputStream().use { output ->
                response.copyTo(output)
            }
        }

        if (hash != null) {
            val downloadedHash = MessageDigest.getInstance("SHA-256")
                .digest(tempFile.readBytes())
                .toHexString()

            if (downloadedHash != hash) {
                throw GradleException("Hash check failed for file $filename, expected $hash but found $downloadedHash.")
            }
        }

        return tempFile
    }

    fun downloadZip(fallbackUrl: String?): File {
        val downloadPlatform = platform.get()

        val metadataFile = metadata.get().asFile
        val json = JsonSlurper().parse(metadataFile) as Map<*, *>

        val payload = json["payload"] as? Map<*, *>

        val downloads = payload?.get("downloads") as? Map<*, *>
        val platformInfo = downloads?.get(downloadPlatform) as? Map<*, *>

        val downloadUrl = platformInfo?.get("url") as? String ?: fallbackUrl
        if (downloadUrl == null) {
            throw GradleException("Failed to fetch download url for platform $platform.")
        }

        val expectedHash = platformInfo?.get("hash") as? String

        return downloadAndVerifyFile("$downloadPlatform.zip", downloadUrl, expectedHash)
    }
}

abstract class DownloadAndUnzipGeodeTask : DownloadGeodeFileTask() {
    @get:Input
    abstract val geodeVersion: Property<String>

    @get:OutputFile
    abstract val targetFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val downloadGeodeVersion = geodeVersion.get()
        val downloadPlatform = platform.get()
        val destinationFile = targetFile.get().asFile

        val tempOutput = this.downloadZip("https://github.com/geode-sdk/geode/releases/download/v$downloadGeodeVersion/geode-v$downloadGeodeVersion-$downloadPlatform.zip")

        val expectedFile = "Geode.$downloadPlatform.so"

        ZipInputStream(tempOutput.inputStream()).use { zip ->
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

abstract class DownloadAndUnzipGeodeResourcesTask : DownloadGeodeFileTask() {
    init {
        platform.set("resources")
    }

    @get:Input
    abstract val geodeVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val downloadGeodeVersion = geodeVersion.get()

        val tempOutput = this.downloadZip("https://github.com/geode-sdk/geode/releases/download/v$downloadGeodeVersion/resources.zip")

        val baseOutput = outputDir.get().asFile
        if (baseOutput.exists()) baseOutput.deleteRecursively()

        val output = File(baseOutput, "geode.loader")
        output.mkdirs()

        val canonicalOutput = output.canonicalPath

        ZipInputStream(tempOutput.inputStream()).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                val destination = File(output, entry.name)
                if (!destination.canonicalPath.startsWith(canonicalOutput)) {
                    throw GradleException("attempted copy to outside of output directory: ${entry.name}")
                }

                if (destination.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()

                    destination.outputStream().use { destinationStream ->
                        zipStream.copyTo(destinationStream)
                    }
                }

                entry = zipStream.nextEntry
            }
        }
    }
}

val fetchGeodeMetadataTask = tasks.register<DownloadVersionMetadataTask>("fetchGeodeMetadata") {
    description = "Fetches and saves a Geode version's metadata"
    inputs.property("geodeVersion", currentGeodeVersion)

    geodeVersion = currentGeodeVersion
    targetFile = temporaryDir.resolve("metadata.json")
}

val downloadGeode32Task = tasks.register<DownloadAndUnzipGeodeTask>("downloadGeode32Binary") {
    description = "Downloads the 32bit Geode release archive"
    inputs.property("geodeVersion", currentGeodeVersion)

    geodeVersion = currentGeodeVersion
    platform = "android32"
    metadata = fetchGeodeMetadataTask.flatMap { it.targetFile }
    targetFile = temporaryDir.resolve("libgeode.so")
}

val downloadGeode64Task = tasks.register<DownloadAndUnzipGeodeTask>("downloadGeode64Binary") {
    description = "Downloads the 64bit Geode release archive"
    inputs.property("geodeVersion", currentGeodeVersion)

    geodeVersion = currentGeodeVersion
    platform = "android64"
    metadata = fetchGeodeMetadataTask.flatMap { it.targetFile }
    targetFile = temporaryDir.resolve("libgeode.so")
}

val downloadGeodeResourcesTask = tasks.register<DownloadAndUnzipGeodeResourcesTask>("downloadGeodeResources") {
    description = "Downloads Geode resources"
    inputs.property("geodeVersion", currentGeodeVersion)

    geodeVersion = currentGeodeVersion
    metadata = fetchGeodeMetadataTask.flatMap { it.targetFile }
    outputDir = layout.buildDirectory.dir("geode-resources")
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

        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
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
        variant.sources.assets?.addGeneratedSourceDirectory(
            downloadGeodeResourcesTask,
            DownloadAndUnzipGeodeResourcesTask::outputDir
        )
    }
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
    implementation ("androidx.games:games-frame-pacing:2.1.3")

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
