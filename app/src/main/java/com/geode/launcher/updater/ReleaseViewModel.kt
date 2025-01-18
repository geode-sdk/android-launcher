package com.geode.launcher.updater

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ReleaseViewModel(private val application: Application): ViewModel() {
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as Application

                ReleaseViewModel(
                    application = application
                )
            }
        }
    }

    sealed class ReleaseUIState {
        data object InUpdateCheck : ReleaseUIState()
        data class Failure(val exception: Exception) : ReleaseUIState()
        data class InDownload(val downloaded: Long, val outOf: Long?) : ReleaseUIState()
        data class Finished(val hasUpdated: Boolean = false) : ReleaseUIState()
        data class Cancelled(val isCancelling: Boolean = false) : ReleaseUIState()

        companion object {
            fun managerStateToUI(state: ReleaseManager.ReleaseManagerState): ReleaseUIState {
                return when (state) {
                    is ReleaseManager.ReleaseManagerState.InUpdateCheck -> InUpdateCheck
                    is ReleaseManager.ReleaseManagerState.Failure -> Failure(state.exception)
                    is ReleaseManager.ReleaseManagerState.InDownload ->
                        InDownload(state.downloaded, state.outOf)
                    is ReleaseManager.ReleaseManagerState.Finished -> Finished(state.hasUpdated)
                }
            }
        }
    }

    private val _uiState = MutableStateFlow<ReleaseUIState>(ReleaseUIState.Finished())
    val uiState = _uiState.asStateFlow()

    val isInUpdate
        get() = ReleaseManager.get(application).isInUpdate

    val nextLauncherUpdate = ReleaseManager.get(application).availableLauncherUpdate

    var hasPerformedCheck = false
        private set

    fun cancelUpdate() {
        viewModelScope.launch {
            _uiState.value = ReleaseUIState.Cancelled(true)

            ReleaseManager.get(application).cancelUpdate()

            _uiState.value = ReleaseUIState.Cancelled()
        }
    }

    private suspend fun syncUiState(
        flow: StateFlow<ReleaseManager.ReleaseManagerState>
    ) {
        flow.map(ReleaseUIState::managerStateToUI).collect {
            // send the mapped state to the ui
            _uiState.value = it
        }
    }

    fun useGlobalCheckState() {
        hasPerformedCheck = true

        viewModelScope.launch(Dispatchers.IO) {
            val releaseFlow = ReleaseManager.get(application)
                .uiState

            syncUiState(releaseFlow)
        }
    }

    fun runReleaseCheck(isManual: Boolean = false) {
        hasPerformedCheck = true

        viewModelScope.launch(Dispatchers.IO) {
            val releaseFlow = ReleaseManager.get(application)
                .checkForUpdates(isManual)

            syncUiState(releaseFlow)
        }
    }
}