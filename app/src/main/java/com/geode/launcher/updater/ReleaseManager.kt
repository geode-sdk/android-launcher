package com.geode.launcher.updater

import android.content.Context
import android.util.Log
import com.geode.launcher.BuildConfig
import com.geode.launcher.utils.DownloadUtils
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val TAG_LATEST = "latest"
private const val TAG_BETA = "prerelease"

/**
 * Singleton to manage Geode updates, from update checking to downloading.
 */
class ReleaseManager private constructor(
    private val applicationContext: Context,
    private val httpClient: OkHttpClient,
    private val releaseRepository: ReleaseRepository = ReleaseRepository(httpClient)
) {
    companion object {
        private lateinit var managerInstance: ReleaseManager

        fun get(context: Context): ReleaseManager {
            if (!Companion::managerInstance.isInitialized) {
                val applicationContext = context.applicationContext

                val httpClient = OkHttpClient.Builder()
                    .cache(Cache(
                        applicationContext.cacheDir,
                        maxSize = 10L * 1024L * 1024L // 10mb cache size
                    ))
                    .build()

                managerInstance = ReleaseManager(
                    applicationContext,
                    httpClient
                )
            }

            return managerInstance
        }
    }

    sealed class ReleaseManagerState {
        data object InUpdateCheck : ReleaseManagerState()
        data class Failure(val exception: Exception) : ReleaseManagerState()
        data class InDownload(val downloaded: Long, val outOf: Long?) : ReleaseManagerState()
        data class Finished(val hasUpdated: Boolean = false) : ReleaseManagerState()
    }

    class UpdateException(reason: Reason? = null, cause: Throwable? = null) : Exception(reason?.name, cause) {
        enum class Reason {
            EXTERNAL_FILE_IN_USE
        }

        var reason: Reason? = reason
            private set
    }

    private var updateJob: Job? = null

    private val _uiState = MutableStateFlow<ReleaseManagerState>(ReleaseManagerState.Finished())
    val uiState = _uiState.asStateFlow()

    val isInUpdate: Boolean
        get() = _uiState.value !is ReleaseManagerState.Failure && _uiState.value !is ReleaseManagerState.Finished

    private val _availableLauncherUpdateTag = MutableStateFlow<String?>(null)
    val availableLauncherUpdateTag = _availableLauncherUpdateTag.asStateFlow()

    private val _availableLauncherUpdateDetails = MutableStateFlow<DownloadableLauncherRelease?>(null)
    val availableLauncherUpdateDetails = _availableLauncherUpdateDetails.asStateFlow()

    var dismissedLauncherUpdate = false

    var skipStateUpdate = false

    private fun sendError(e: Exception) {
        skipStateUpdate = true
        _uiState.value = ReleaseManagerState.Failure(e)

        // ignore cancellation, it's good actually
        if (e !is CancellationException && e !is InterruptedIOException) {
            Log.w("Geode", "Release download has failed:")
            e.printStackTrace()
        }
    }

    private fun mapSelectedReleaseToTag(): String {
        val sharedPreferences = PreferenceUtils.get(applicationContext)
        val releaseChannel = sharedPreferences.getInt(PreferenceUtils.Key.RELEASE_CHANNEL_TAG)

        return when (releaseChannel) {
            2 -> "nightly"
            1 -> TAG_BETA
            else -> TAG_LATEST
        }
    }

    private fun getBestReleaseForGameVersion(gameVersion: Long): String? = when {
        gameVersion >= 40L -> mapSelectedReleaseToTag()
        gameVersion == 39L -> "v3.9.3"
        gameVersion == 38L -> "v2.0.0-beta.27"
        gameVersion == 37L -> "v2.0.0-beta.4"
        else -> null
    }

    private suspend fun getLatestRelease(): Downloadable? {
        if (!GamePackageUtils.isGameInstalled(applicationContext.packageManager)) {
            return null
        }

        val gameVersion = GamePackageUtils.getGameVersionCode(applicationContext.packageManager)
        val preferences = PreferenceUtils.get(applicationContext)

        val targetTag = preferences.getInt(PreferenceUtils.Key.RELEASE_CHANNEL_TAG)

        return when (targetTag) {
            2 -> releaseRepository.getReleaseByTag("nightly")
            1 -> releaseRepository.getLatestIndexRelease(gameVersion, true)
            else -> releaseRepository.getLatestIndexRelease(gameVersion, false)
        }
    }

    private suspend fun downloadLauncherUpdate(release: Downloadable): Result<File> {
        val download = release.getDownload()
            ?: return Result.failure(FileNotFoundException())

        val outputDirectory = applicationContext.filesDir
        val outputFile = File(outputDirectory, download.filename)

        try {
            DownloadUtils.downloadStream(
                httpClient,
                download.url,
                onResponse = { body ->
                    body.source().use { source ->
                        outputFile.sink().buffer().use { sink ->
                            sink.writeAll(source)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(outputFile)
    }

    private suspend fun performResourceDownload(resourceAsset: DownloadableAsset, initialSize: Long): Boolean {
        val guessSize = if (resourceAsset.size == null) null else initialSize + resourceAsset.size
        val guessInitial = if (guessSize == null) 0 else initialSize
        _uiState.value = ReleaseManagerState.InDownload(guessInitial, guessSize)

        val outputDir = getTempResourcesDirectory()
        val outputPath = outputDir.path

        val finalDir = LaunchUtils.getGeodeResourcesDirectory(applicationContext)

        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            DownloadUtils.downloadStream(
                httpClient,
                resourceAsset.url,
                onProgress = { progress, outOf ->
                    if (!skipStateUpdate)
                        _uiState.value = ReleaseManagerState.InDownload(initialSize + progress, outOf + initialSize)
                },
                onResponse = { body ->
                    DownloadUtils.copyZipStreamToDirectory(
                        body.byteStream(),
                        outputDir
                    )

                    if (finalDir.exists()) {
                        finalDir.deleteRecursively()
                    }

                    if (!outputDir.renameTo(finalDir)) {
                        println("Failed to rename temporary directory!")
                        DownloadUtils.copyDirectory(outputDir, finalDir)
                    }
                }
            )
        } catch (e: Exception) {
            sendError(e)
            return false
        } finally {
            val tempPathClone = File(outputPath)
            runCatching {
                if (tempPathClone.exists()) tempPathClone.deleteRecursively()
            }
        }

        return true
    }

    private suspend fun performUpdate(release: Downloadable) {
        skipStateUpdate = false

        val releaseAsset = release.getDownload()
        if (releaseAsset == null) {
            val noAssetException = Exception("missing Android download")
            _uiState.value = ReleaseManagerState.Failure(noAssetException)

            return
        }

        val resourcesAsset = release.getResourcesDownload()

        var releaseSize = releaseAsset.size ?: 0L
        val resourcesSize = resourcesAsset?.size ?: 0L

        // set an initial download size
        val initialSize = if (releaseAsset.size == null && resourcesAsset?.size == null) null else releaseSize + resourcesSize
        _uiState.value = ReleaseManagerState.InDownload(0, initialSize)

        val outputFile = getTempFile()
        // clone the file instance as renameTo may move the original file
        val tempFilePath = outputFile.path

        val geodeFile = getGeodeOutputPath()

        try {
            // tempFile should be in same path as geodeFile
            val geodeParent = geodeFile.parentFile
            if (geodeParent != null && !geodeParent.exists()) {
                geodeParent.mkdirs()
            }

            DownloadUtils.downloadStream(
                httpClient,
                releaseAsset.url,
                onProgress = { progress, outOf ->
                    if (!skipStateUpdate)
                        _uiState.value = ReleaseManagerState.InDownload(progress, outOf + resourcesSize)

                    releaseSize = max(outOf, releaseSize)
                },
                onResponse = { body ->
                    DownloadUtils.extractFileFromZipStream(
                        body.byteStream(),
                        outputFile,
                        geodeFile.name
                    )

                    // work around a permission issue from adb push
                    if (geodeFile.exists()) {
                        geodeFile.delete()
                    }

                    val renameSuccessful = runCatching {
                        outputFile.renameTo(geodeFile)
                    }.getOrDefault(false)

                    if (!renameSuccessful) {
                        // attempt a manual copy if rename fails for whatever reason
                        DownloadUtils.copyFile(outputFile, geodeFile)
                    }
                }
            )
        } catch (e: Exception) {
            sendError(e)
            return
        } finally {
            val tempFileClone = File(tempFilePath)
            runCatching {
                if (tempFileClone.exists()) tempFileClone.delete()
            }
        }

        if (resourcesAsset != null) {
            if (!performResourceDownload(resourcesAsset, releaseSize)) {
                return
            }
        }

        // extraction performed
        updatePreferences(release)

        val currentTime = Clock.System.now().toEpochMilliseconds()
        PreferenceUtils.get(applicationContext)
            .setLong(PreferenceUtils.Key.LAST_UPDATE_CHECK_TIME, currentTime)

        _uiState.value = ReleaseManagerState.Finished(true)
    }

    private fun fileWasExternallyModified(modifiedOnly: Boolean = false): Boolean {
        // make sure geode is still here. just in case
        val geodeFile = getGeodeOutputPath()
        if (!geodeFile.exists()) {
            return !modifiedOnly
        }

        val sharedPreferences = PreferenceUtils.get(applicationContext)

        val originalFileHash = sharedPreferences.getString(PreferenceUtils.Key.CURRENT_RELEASE_MODIFIED)
            ?: return true

        val currentFileHash = computeFileHash(geodeFile)
        return originalFileHash != currentFileHash
    }

    private suspend fun getLatestLauncherUpdate(): DownloadableLauncherRelease? {
        return try {
            releaseRepository.getLatestLauncherRelease()
        } catch (e: Exception) {
            sendError(e)
            return null
        }
    }

    suspend fun checkLauncherUpdate() {
        val launcherRelease = getLatestLauncherUpdate() ?: return

        // unconditionally track it, we're not using it for checks and it might be good to have
        _availableLauncherUpdateDetails.value = launcherRelease

        val launcherTag = launcherRelease.release.tagName

        PreferenceUtils.get(applicationContext)
            .setString(PreferenceUtils.Key.LAST_LAUNCHER_UPDATE, launcherTag)

        if (launcherTag != BuildConfig.VERSION_NAME) {
            _availableLauncherUpdateTag.value = launcherTag
        }
    }

    fun checkCachedLauncherUpdate() {
        val lastUpdate = PreferenceUtils.get(applicationContext)
            .getString(PreferenceUtils.Key.LAST_LAUNCHER_UPDATE) ?: return

        if (lastUpdate != BuildConfig.VERSION_NAME) {
            _availableLauncherUpdateTag.value = lastUpdate
        }
    }

    suspend fun downloadLatestLauncherUpdate(): Result<File> {
        val update = _availableLauncherUpdateDetails.value
            ?: getLatestLauncherUpdate()
            ?: return Result.failure(FileNotFoundException())

        return downloadLauncherUpdate(update)
    }

    fun shouldUseCache(): Boolean {
        val sharedPreferences = PreferenceUtils.get(applicationContext)
        if (sharedPreferences.getBoolean(PreferenceUtils.Key.DISABLE_UPDATE_CACHE)) {
            return false
        }

        val lastCheckTime = sharedPreferences.getLong(PreferenceUtils.Key.LAST_UPDATE_CHECK_TIME)

        // only check for updates if it's been over 15 minutes since last check
        val checkMinTime = Clock.System.now().minus(15.minutes).toEpochMilliseconds()
        return lastCheckTime > checkMinTime && !fileWasExternallyModified()
    }

    fun afterUseCachedUpdate() {
        checkCachedLauncherUpdate()
        _uiState.tryEmit(ReleaseManagerState.Finished())
    }

    private suspend fun checkForNewRelease(isManual: Boolean = false) {
        val sharedPreferences = PreferenceUtils.get(applicationContext)

        if (!isManual && shouldUseCache()) {
            afterUseCachedUpdate()
            return
        }

        val release = try {
            getLatestRelease()
        } catch (e: Exception) {
            sendError(e)
            return
        }

        checkLauncherUpdate()

        if (release == null) {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            sharedPreferences.setLong(PreferenceUtils.Key.LAST_UPDATE_CHECK_TIME, currentTime)

            _uiState.value = ReleaseManagerState.Finished()
            return
        }

        val currentVersion = sharedPreferences.getLong(PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP)
        val latestVersion = release.getDescriptor()

        // check if an update is needed
        if (latestVersion == currentVersion && !fileWasExternallyModified()) {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            sharedPreferences.setLong(PreferenceUtils.Key.LAST_UPDATE_CHECK_TIME, currentTime)

            _uiState.value = ReleaseManagerState.Finished()
            return
        }

        val allowOverwriting = !sharedPreferences.getBoolean(PreferenceUtils.Key.DEVELOPER_MODE) || isManual

        // check if the file was externally modified
        if (!allowOverwriting && fileWasExternallyModified(true)) {
            sendError(UpdateException(UpdateException.Reason.EXTERNAL_FILE_IN_USE))
            return
        }

        performUpdate(release)
    }

    private fun updatePreferences(release: Downloadable) {
        val sharedPreferences = PreferenceUtils.get(applicationContext)

        sharedPreferences.setString(
            PreferenceUtils.Key.CURRENT_VERSION_TAG,
            release.getDescription()
        )

        sharedPreferences.setLong(
            PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP,
            release.getDescriptor()
        )

        // store hash as modified time is unreliable (don't know why)
        val outputFile = getGeodeOutputPath()
        val fileHash = computeFileHash(outputFile)

        sharedPreferences.setString(PreferenceUtils.Key.CURRENT_RELEASE_MODIFIED, fileHash)
    }

    private fun computeFileHash(file: File): String {
        val sink = HashingSink.md5(blackholeSink())
        file.source().buffer().use {
            it.readAll(sink)
        }

        return sink.hash.hex()
    }

    private fun getGeodeOutputPath(): File {
        val geodeName = LaunchUtils.geodeFilename
        val geodeDirectory = LaunchUtils.getBaseDirectory(applicationContext)

        return File(geodeDirectory, geodeName)
    }

    private fun createRandomString(): String {
        val alphabet = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return buildString(8) {
            repeat(8) { append(alphabet.random()) }
        }
    }

    private fun getTempFile(): File {
        val geodeName = LaunchUtils.geodeFilename
        val geodeDirectory = LaunchUtils.getBaseDirectory(applicationContext)

        // warning!! while File::createTempFile may look tempting, a certain brand of phones has a messed up implementation of it
        // so we're making a temp file manually (as long as it doesn't collide with the geode download, it's okay)

        val suffix = createRandomString()
        val tmpName = "tmp-$suffix.$geodeName"

        val tempFile = File(geodeDirectory, tmpName)

        return tempFile
    }

    private fun getTempResourcesDirectory(): File {
        val finalDir = LaunchUtils.getGeodeResourcesDirectory(applicationContext)
        val suffix = createRandomString()
        val tempDirName = "tmp-$suffix-geode.loader"

        val parentDir = finalDir.parentFile
        return if (parentDir != null) {
            File(finalDir.parentFile, tempDirName)
        } else {
            File(tempDirName)
        }
    }

    /**
     * Cancels the current update job.
     */
    suspend fun cancelUpdate() {
        updateJob?.cancelAndJoin()
        updateJob = null

        _uiState.value = ReleaseManagerState.Finished()
    }

    /**
     * Schedules a new update checking job.
     * @return Flow that tracks the state of the update.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun checkForUpdates(isManual: Boolean = false): StateFlow<ReleaseManagerState> {
        if (!isInUpdate) {
            if (isManual || !shouldUseCache()) {
                _uiState.value = ReleaseManagerState.InUpdateCheck
                updateJob = GlobalScope.launch {
                    checkForNewRelease(isManual)
                }
            } else {
                afterUseCachedUpdate()
            }
        }

        return _uiState.asStateFlow()
    }
}
