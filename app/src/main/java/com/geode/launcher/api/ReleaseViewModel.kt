package com.geode.launcher.api

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geode.launcher.utils.DownloadReceiver
import com.geode.launcher.utils.PreferenceUtils
import com.geode.launcher.utils.downloadFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class ReleaseViewModel(private val releaseRepository: ReleaseRepository, private val sharedPreferences: PreferenceUtils, private val application: Application): ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as Application
                val preferences = PreferenceUtils.get(application)

                ReleaseViewModel(
                    releaseRepository = ReleaseRepository(),
                    sharedPreferences = preferences,
                    application = application
                )
            }
        }
    }

    sealed class ReleaseUIState {
        data object InUpdateCheck : ReleaseUIState()
        data class Failure(val exception: Exception) : ReleaseUIState()
        data class InDownload(val downloaded: Long, val outOf: Long) : ReleaseUIState()
        data object Finished : ReleaseUIState()
    }

    private val _uiState = MutableStateFlow<ReleaseUIState>(ReleaseUIState.Finished)
    val uiState = _uiState.asStateFlow()

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

    private suspend fun getLatestRelease(): Release? {
        _uiState.value = ReleaseUIState.InUpdateCheck

        val latestRelease = retry {
            releaseRepository.getLatestNightlyRelease(true)
        }

        return latestRelease
    }

    private suspend fun checkForNewRelease() {
        val release = try {
            getLatestRelease()
        } catch (e: Exception) {
            _uiState.value = ReleaseUIState.Failure(e)
            return
        }

        if (release == null) {
            _uiState.value = ReleaseUIState.Finished
            return
        }

        val currentVersion = sharedPreferences.getLong(PreferenceUtils.Key.CURRENT_VERSION_TIMESTAMP)
        val latestVersion = release.getDescriptor()

        if (latestVersion <= currentVersion) {
            _uiState.value = ReleaseUIState.Finished
            return
        }

        val releaseAsset = release.getAndroidDownload()
        if (releaseAsset == null) {
            val noAssetException = Exception("missing Android download")
            _uiState.value = ReleaseUIState.Failure(noAssetException)

            return
        }

        createDownload(releaseAsset)
    }

    fun runReleaseCheck() {
        viewModelScope.launch {
            checkForNewRelease()
        }
    }

    private fun createDownload(asset: Asset) {
        _uiState.value = ReleaseUIState.InDownload(0, asset.size.toLong())

        val downloadReceiver = object : DownloadReceiver {
            override fun onProgress(progress: Long, outOf: Long) {
                _uiState.value = ReleaseViewModel.ReleaseUIState.InDownload(progress, outOf)
            }

            override fun onFinished(downloadPath: String) {
                _uiState.value = ReleaseViewModel.ReleaseUIState.Finished
                // todo: process
            }
        }

        downloadFile(application, asset.browserDownloadUrl, asset.name, downloadReceiver)
    }

    init {
        val shouldUpdate = sharedPreferences.getBoolean(PreferenceUtils.Key.UPDATE_AUTOMATICALLY)
        if (shouldUpdate) {
            runReleaseCheck()
        }
    }
}