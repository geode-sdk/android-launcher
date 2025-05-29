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
import okio.buffer
import okio.source
import java.io.File
import java.io.InterruptedIOException
import kotlin.io.path.deleteIfExists

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

    private val _availableLauncherUpdate = MutableStateFlow<DownloadableLauncherRelease?>(null)
    val availableLauncherUpdate = _availableLauncherUpdate.asStateFlow()

    private fun sendError(e: Exception) {
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

        if (preferences.getBoolean(PreferenceUtils.Key.USE_INDEX_API)) {
            val targetTag = preferences.getInt(PreferenceUtils.Key.RELEASE_CHANNEL_TAG)

            return when (targetTag) {
                2 -> releaseRepository.getReleaseByTag("nightly")
                1 -> releaseRepository.getLatestIndexRelease(gameVersion, true)
                else -> releaseRepository.getLatestIndexRelease(gameVersion, false)
            }
        } else {
            val targetTag = getBestReleaseForGameVersion(gameVersion) ?: return null

            return when (targetTag) {
                TAG_LATEST -> releaseRepository.getLatestGeodeRelease()
                TAG_BETA -> releaseRepository.getLatestGeodePreRelease()
                else -> releaseRepository.getReleaseByTag(targetTag)
            }
        }
    }

    private suspend fun downloadLauncherUpdate(release: Downloadable) {
        val download = release.getDownload() ?: return

        val outputDirectory = LaunchUtils.getBaseDirectory(applicationContext)
        val outputFile = File(outputDirectory, download.filename)

        if (outputFile.exists()) {
            // only download the apk once
            return
        }

        _uiState.value = ReleaseManagerState.InDownload(0, download.size)

        try {
            val fileStream = DownloadUtils.downloadStream(
                httpClient,
                download.url
            ) { progress, outOf ->
                _uiState.value = ReleaseManagerState.InDownload(progress, outOf)
            }

            fileStream.copyTo(outputFile.outputStream())
        } catch (e: Exception) {
            sendError(e)
            return
        }
    }

    private suspend fun performUpdate(release: Downloadable) {
        val releaseAsset = release.getDownload()
        if (releaseAsset == null) {
            val noAssetException = Exception("missing Android download")
            _uiState.value = ReleaseManagerState.Failure(noAssetException)

            return
        }

        // set an initial download size
        _uiState.value = ReleaseManagerState.InDownload(0, releaseAsset.size)

        val outputFile = getTempFile()
        // clone the file instance as renameTo may move the original file
        val tempFilePath = outputFile.path

        try {
            val fileStream = DownloadUtils.downloadStream(
                httpClient,
                releaseAsset.url
            ) { progress, outOf ->
                _uiState.value = ReleaseManagerState.InDownload(progress, outOf)
            }

            outputFile.parentFile?.mkdirs()

            val geodeFile = getGeodeOutputPath()

            DownloadUtils.extractFileFromZipStream(
                fileStream,
                outputFile.outputStream(),
                geodeFile.name
            )

            // work around a permission issue from adb push
            if (geodeFile.exists()) {
                geodeFile.delete()
            }

            outputFile.renameTo(geodeFile)
        } catch (e: Exception) {
            sendError(e)
            return
        } finally {
            val tempFileClone = File(tempFilePath)
            try {
                if (tempFileClone.exists()) tempFileClone.delete()
            } catch (_: Exception) { }
        }

        downloadLauncherUpdateIfNecessary()

        // extraction performed
        updatePreferences(release)
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
            ?: return false

        val currentFileHash = computeFileHash(geodeFile)

        return originalFileHash != currentFileHash
    }

    private fun checkLauncherUpdate(launcherUpdate: DownloadableLauncherRelease) {
        if (launcherUpdate.release.tagName != BuildConfig.VERSION_NAME) {
            _availableLauncherUpdate.value = launcherUpdate
        }
    }

    private suspend fun downloadLauncherUpdateIfNecessary() {
        val update = _availableLauncherUpdate.value ?: return
        downloadLauncherUpdate(update)
    }

    private suspend fun checkForNewRelease(isManual: Boolean = false) {
        val release = try {
            getLatestRelease()
        } catch (e: Exception) {
            sendError(e)
            return
        }

        val launcherRelease = try {
            releaseRepository.getLatestLauncherRelease()
        } catch (e: Exception) {
            sendError(e)
            return
        }

        if (launcherRelease != null) {
            checkLauncherUpdate(launcherRelease)
        }

        if (release == null) {
            downloadLauncherUpdateIfNecessary()

            _uiState.value = ReleaseManagerState.Finished()
            return
        }

        val sharedPreferences = PreferenceUtils.get(applicationContext)

        val currentVersion = sharedPreferences.getLong(PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP)
        val latestVersion = release.getDescriptor()

        // check if an update is needed
        if (latestVersion == currentVersion && !fileWasExternallyModified()) {
            downloadLauncherUpdateIfNecessary()

            _uiState.value = ReleaseManagerState.Finished()
            return
        }

        val allowOverwriting = !sharedPreferences.getBoolean(PreferenceUtils.Key.DEVELOPER_MODE) || isManual

        // check if the file was externally modified
        if (!allowOverwriting && fileWasExternallyModified(true)) {
            downloadLauncherUpdateIfNecessary()

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

    private fun computeFileHash(file: File): String =
        file.source().buffer().readByteString().md5().hex()

    private fun getGeodeOutputPath(): File {
        val geodeName = LaunchUtils.geodeFilename
        val geodeDirectory = LaunchUtils.getBaseDirectory(applicationContext)

        return File(geodeDirectory, geodeName)
    }

    private fun getTempFile(): File {
        val geodeName = LaunchUtils.geodeFilename
        val geodeDirectory = LaunchUtils.getBaseDirectory(applicationContext)

        val tempFile = File.createTempFile(geodeName, null, geodeDirectory)

        return tempFile
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
            _uiState.value = ReleaseManagerState.InUpdateCheck
            updateJob = GlobalScope.launch {
                checkForNewRelease(isManual)
            }
        }

        return _uiState.asStateFlow()
    }
}
