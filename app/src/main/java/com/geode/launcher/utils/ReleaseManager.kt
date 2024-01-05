package com.geode.launcher.utils

import android.content.Context
import com.geode.launcher.api.Asset
import com.geode.launcher.api.Release
import com.geode.launcher.api.ReleaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Singleton to manage Geode updates, from update checking to downloading.
 */
class ReleaseManager private constructor(
    private val applicationContext: Context,
    private val releaseRepository: ReleaseRepository = ReleaseRepository()
) {
    companion object {
        private lateinit var managerInstance: ReleaseManager

        fun get(context: Context): ReleaseManager {
            if (!::managerInstance.isInitialized) {
                val applicationContext = context.applicationContext
                managerInstance = ReleaseManager(applicationContext)
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

    private var updateJob: Job? = null
    private var currentUpdate: Long? = null

    private val uiState = MutableStateFlow<ReleaseManagerState>(ReleaseManagerState.Finished())

    // runs a given function, retrying until it succeeds or max attempts are reached
    private suspend fun <R> retry(block: suspend () -> R): R {
        val maxAttempts = 5
        val initialDelay = 1000L

        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // only retry on exceptions that can be handled
                if (e !is IOException) {
                    throw e
                }
            }

            delay(initialDelay * attempt)
        }

        // run final time for exceptions
        return block()
    }

    private fun sendError(e: Exception) {
        uiState.value = ReleaseManagerState.Failure(e)
        e.printStackTrace()
    }

    private suspend fun getLatestRelease(): Release? {
        val sharedPreferences = PreferenceUtils.get(applicationContext)
        val useNightly = sharedPreferences.getBoolean(PreferenceUtils.Key.RELEASE_CHANNEL)

        val latestRelease = retry {
            if (useNightly) {
                releaseRepository.getLatestNightlyRelease()
            } else {
                releaseRepository.getLatestRelease()
            }
        }

        return latestRelease
    }

    private suspend fun performUpdate(release: Release, releaseAsset: Asset) {
        try {
            val file = performDownload(releaseAsset.browserDownloadUrl)
            performExtraction(file)
        } catch (e: Exception) {
            sendError(e)
            return
        }

        // extraction performed
        updatePreferences(release)
        uiState.value = ReleaseManagerState.Finished(true)
    }

    // cancels the previous update and begins a new one
    private suspend fun beginUpdate(release: Release, releaseAsset: Asset) {
        if (release.getDescriptor() == currentUpdate) {
            return
        }

        updateJob?.cancel()

        currentUpdate = release.getDescriptor()
        updateJob = coroutineScope {
            launch {
                performUpdate(release, releaseAsset)
            }
        }
    }

    private suspend fun checkForNewRelease(updateFlow: MutableStateFlow<ReleaseManagerState>, updateScope: CoroutineScope?) {
        val release = try {
            getLatestRelease()
        } catch (e: Exception) {
            updateFlow.value = ReleaseManagerState.Failure(e)
            return
        }

        if (release == null) {
            updateFlow.value = ReleaseManagerState.Finished()
            return
        }

        val sharedPreferences = PreferenceUtils.get(applicationContext)

        val currentVersion = sharedPreferences.getLong(PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP)
        val latestVersion = release.getDescriptor()

        // check if an update is needed
        if (latestVersion <= currentVersion) {
            updateFlow.value = ReleaseManagerState.Finished()
            return
        }

        val releaseAsset = release.getAndroidDownload()
        if (releaseAsset == null) {
            val noAssetException = Exception("missing Android download")
            updateFlow.value = ReleaseManagerState.Failure(noAssetException)

            return
        }

        // set an initial download size
        uiState.value = ReleaseManagerState.InDownload(0, releaseAsset.size.toLong())

        // final act... sync update flow to uiState
        if (updateScope?.isActive == true) {
            updateScope.launch {
                uiState.collect {
                    updateFlow.value = it
                }
            }
        }

        beginUpdate(release, releaseAsset)
    }

    private suspend fun updatePreferences(release: Release) {
        coroutineScope {
            val sharedPreferences = PreferenceUtils.get(applicationContext)

            sharedPreferences.setString(
                PreferenceUtils.Key.CURRENT_VERSION_TAG,
                release.getDescription()
            )

            sharedPreferences.setLong(
                PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP,
                release.getDescriptor()
            )
        }
    }

    private suspend fun performDownload(url: String): File {
        return DownloadUtils.downloadFile(applicationContext, url, "geode-release.zip") { progress, outOf ->
            uiState.value = ReleaseManagerState.InDownload(progress, outOf)
        }
    }

    private suspend fun performExtraction(outputFile: File) {
        try {
            val geodeName = LaunchUtils.getGeodeFilename()
            val geodeFile = getGeodeOutputPath(geodeName)

            DownloadUtils.extractFileFromZip(outputFile, geodeFile, geodeName)
        } finally {
            // delete file now that it's no longer needed
            outputFile.delete()
        }
    }

    private fun getGeodeOutputPath(geodeName: String): File {
        val fallbackPath = File(applicationContext.filesDir, "launcher")
        val geodeDirectory = applicationContext.getExternalFilesDir("") ?: fallbackPath

        return File(geodeDirectory, geodeName)
    }

    /**
     * Schedules a new update checking job.
     * @param updateScope Limits the lifetime of flow collection to the scope.
     * @return Flow that tracks the state of the update, eventually being merged into the main downloader state.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun checkForUpdates(updateScope: CoroutineScope? = null): StateFlow<ReleaseManagerState> {
        val flow = MutableStateFlow<ReleaseManagerState>(ReleaseManagerState.InUpdateCheck)

        GlobalScope.launch {
            checkForNewRelease(flow, updateScope)
        }

        return flow.asStateFlow()
    }
}