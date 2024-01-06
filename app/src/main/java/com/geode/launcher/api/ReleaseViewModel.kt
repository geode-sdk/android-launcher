package com.geode.launcher.api

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geode.launcher.utils.ReleaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformWhile
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
        data class InDownload(val downloaded: Long, val outOf: Long) : ReleaseUIState()
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

    var hasPerformedCheck = false
        private set

    fun cancelUpdate() {
        viewModelScope.launch {
            _uiState.value = ReleaseUIState.Cancelled(true)

            ReleaseManager.get(application).cancelUpdate()

            _uiState.value = ReleaseUIState.Cancelled()
        }
    }

    fun runReleaseCheck() {
        hasPerformedCheck = true

        viewModelScope.launch {
            val releaseFlow = ReleaseManager.get(application)
                .checkForUpdates()

            releaseFlow
                .transformWhile {
                    // map the ui state into something that can be used
                    emit(ReleaseUIState.managerStateToUI(it))

                    // end collection once ReleaseManager reaches a state of "completion"
                    it !is ReleaseManager.ReleaseManagerState.Finished &&
                            it !is ReleaseManager.ReleaseManagerState.Failure
                }
                .collect {
                    // send the mapped state to the ui
                    _uiState.value = it
                }
        }
    }
}