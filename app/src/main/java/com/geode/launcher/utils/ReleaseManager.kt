package com.geode.launcher.utils

import android.content.Context
import android.util.Log
import com.geode.launcher.api.Release
import com.geode.launcher.api.ReleaseRepository
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
import java.io.File
import java.io.InterruptedIOException

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
            if (!::managerInstance.isInitialized) {
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
        data class InDownload(val downloaded: Long, val outOf: Long) : ReleaseManagerState()
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

    private fun sendError(e: Exception) {
        _uiState.value = ReleaseManagerState.Failure(e)

        // ignore cancellation, it's good actually
        if (e !is CancellationException && e !is InterruptedIOException) {
            Log.w("Geode", "Release download has failed:")
            e.printStackTrace()
        }
    }

    private suspend fun getLatestRelease(): Release? {
        val sharedPreferences = PreferenceUtils.get(applicationContext)
        val useNightly = sharedPreferences.getBoolean(PreferenceUtils.Key.RELEASE_CHANNEL)

        val latestRelease = if (useNightly) {
            releaseRepository.getLatestNightlyRelease()
        } else {
            releaseRepository.getLatestRelease()
        }

        return latestRelease
    }

    private suspend fun performUpdate(release: Release) {
        val releaseAsset = release.getAndroidDownload()
        if (releaseAsset == null) {
            val noAssetException = Exception("missing Android download")
            _uiState.value = ReleaseManagerState.Failure(noAssetException)

            return
        }

        // set an initial download size
        _uiState.value = ReleaseManagerState.InDownload(0, releaseAsset.size.toLong())

        try {
            val fileStream = DownloadUtils.downloadStream(httpClient, releaseAsset.browserDownloadUrl) { progress, outOf ->
                _uiState.value = ReleaseManagerState.InDownload(progress, outOf)
            }

            val geodeFile = getGeodeOutputPath()

            // work around a permission issue from adb push
            if (geodeFile.exists()) {
                geodeFile.delete()
            }

            geodeFile.parentFile?.mkdirs()

            DownloadUtils.extractFileFromZipStream(
                fileStream,
                geodeFile.outputStream(),
                geodeFile.name
            )
        } catch (e: Exception) {
            sendError(e)
            return
        }

        // extraction performed
        updatePreferences(release)
        _uiState.value = ReleaseManagerState.Finished(true)
    }

    private fun fileWasExternallyModified(): Boolean {
        val geodeFile = getGeodeOutputPath()
        if (!geodeFile.exists()) {
            return false
        }

        val sharedPreferences = PreferenceUtils.get(applicationContext)

        val fileLastModified = sharedPreferences.getLong(PreferenceUtils.Key.CURRENT_RELEASE_MODIFIED)
        if (fileLastModified == 0L) {
            return false
        }

        return fileLastModified != geodeFile.lastModified()
    }

    private suspend fun checkForNewRelease(allowOverwriting: Boolean = false) {
        val release = try {
            getLatestRelease()
        } catch (e: Exception) {
            sendError(e)
            return
        }

        if (release == null) {
            _uiState.value = ReleaseManagerState.Finished()
            return
        }

        val sharedPreferences = PreferenceUtils.get(applicationContext)

        val currentVersion = sharedPreferences.getLong(PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP)
        val latestVersion = release.getDescriptor()

        // make sure geode is still here. just in case
        val geodeFile = getGeodeOutputPath()

        // check if an update is needed
        if (latestVersion <= currentVersion && geodeFile.exists()) {
            _uiState.value = ReleaseManagerState.Finished()
            return
        }

        // check if the file was externally modified
        if (!allowOverwriting && fileWasExternallyModified()) {
            sendError(UpdateException(UpdateException.Reason.EXTERNAL_FILE_IN_USE))
            return
        }

        performUpdate(release)
    }

    private fun updatePreferences(release: Release) {
        val sharedPreferences = PreferenceUtils.get(applicationContext)

        sharedPreferences.setString(
            PreferenceUtils.Key.CURRENT_VERSION_TAG,
            release.getDescription()
        )

        sharedPreferences.setLong(
            PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP,
            release.getDescriptor()
        )

        val outputFile = getGeodeOutputPath()
        sharedPreferences.setLong(
            PreferenceUtils.Key.CURRENT_RELEASE_MODIFIED,
            outputFile.lastModified()
        )
    }

    private fun getGeodeOutputPath(): File {
        val geodeName = LaunchUtils.geodeFilename
        val geodeDirectory = LaunchUtils.getBaseDirectory(applicationContext)

        return File(geodeDirectory, geodeName)
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