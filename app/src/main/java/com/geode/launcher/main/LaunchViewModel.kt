package com.geode.launcher.main

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

data class LaunchArguments(
    val autoSafeMode: Boolean,
    val forcePause: Boolean,
    val forceLaunch: Boolean
)

class LaunchViewModel(private val application: Application): ViewModel() {
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application

                LaunchViewModel(
                    application = application
                )
            }
        }
    }

    private var readyTimerPassed = false
    private var readyTimer: CountDownTimer? = null

    private fun initReadyTimer() {
        val cooldownLength = PreferenceUtils.get(application).getInt(PreferenceUtils.Key.WAIT_PERIOD) * 1000L
        if (cooldownLength == 0L) {
            readyTimerPassed = true
            return
        }

        readyTimer = object : CountDownTimer(cooldownLength, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // no tick necessary
            }

            override fun onFinish() {
                readyTimerPassed = true

                if (_uiState.value is LaunchUIState.Working) {
                    viewModelScope.launch {
                        preReadyCheck()
                    }
                }
            }
        }.start()
    }

    enum class LaunchCancelReason {
        MANUAL, AUTOMATIC, LAST_LAUNCH_CRASHED, GEODE_NOT_FOUND, GAME_MISSING, GAME_OUTDATED;

        fun allowsRetry() = when (this) {
            MANUAL,
            AUTOMATIC,
            LAST_LAUNCH_CRASHED,
            GEODE_NOT_FOUND -> true
            GAME_MISSING,
            GAME_OUTDATED -> false
        }

        fun isGameInstallIssue() = when (this) {
            GAME_OUTDATED,
            GAME_MISSING -> true
            else -> false
        }
    }

    sealed class LaunchUIState {
        data object Initial : LaunchUIState()
        data object UpdateCheck : LaunchUIState()
        data class Updating(val downloaded: Long, val outOf: Long?) : LaunchUIState()
        data class Cancelled(val reason: LaunchCancelReason, val inProgress: Boolean = false) : LaunchUIState()
        data object Working : LaunchUIState()
        data object Ready : LaunchUIState()

        fun isInProgress() = when (this) {
            is Ready,
            is Working,
            is Updating,
            is UpdateCheck -> true
            else -> false
        }
    }

    private val _uiState: MutableStateFlow<LaunchUIState>

    init {
        val preferenceUtils = PreferenceUtils.get(application)
        val initialCancel = !preferenceUtils.getBoolean(PreferenceUtils.Key.UPDATE_AUTOMATICALLY)
                && !preferenceUtils.getBoolean(PreferenceUtils.Key.LOAD_AUTOMATICALLY)

        // fixes a single frame of progress being displayed when we're not actually going to do work
        _uiState = MutableStateFlow<LaunchUIState>(
            if (initialCancel) LaunchUIState.Cancelled(LaunchCancelReason.AUTOMATIC)
            else LaunchUIState.Initial
        )
    }

    val uiState = _uiState.asStateFlow()

    val nextLauncherUpdate = ReleaseManager.get(application).availableLauncherUpdate

    var loadFailure: LoadFailureInfo? = null
    var launchArguments: LaunchArguments? = null

    private var hasCheckedForUpdates = false
    private var isCancelling = false
    private var hasManuallyStarted = false

    private var restoreUpdateState: LaunchUIState? = null

    private suspend fun determineUpdateStatus() {
        ReleaseManager.get(application).uiState.takeWhile {
            it is ReleaseManager.ReleaseManagerState.InUpdateCheck || it is ReleaseManager.ReleaseManagerState.InDownload
        }.map { when (it) {
                is ReleaseManager.ReleaseManagerState.InUpdateCheck -> LaunchUIState.UpdateCheck
                is ReleaseManager.ReleaseManagerState.InDownload -> LaunchUIState.Updating(it.downloaded, it.outOf)
                else -> LaunchUIState.UpdateCheck
            }
        }.collect {
            _uiState.emit(it)
        }

        // flow terminates once update check is finished
        hasCheckedForUpdates = true

        val currentState = restoreUpdateState
        if (currentState != null) {
            _uiState.emit(currentState)
            return
        }

        preReadyCheck()
    }

    fun currentCrashInfo(): LoadFailureInfo? {
        val currentState = _uiState.value
        if (currentState is LaunchUIState.Cancelled && currentState.reason == LaunchCancelReason.LAST_LAUNCH_CRASHED) {
            return loadFailure
        }

        return null
    }

    fun clearCrashInfo() {
        val currentState = _uiState.value
        if (currentState is LaunchUIState.Cancelled && currentState.reason == LaunchCancelReason.LAST_LAUNCH_CRASHED) {
            loadFailure = null
        }
    }

    private suspend fun preReadyCheck() {
        if (isCancelling) {
            // don't let the game start if cancelled
            return
        }

        val packageManager = application.packageManager

        if (!GamePackageUtils.isGameInstalled(packageManager)) {
            return _uiState.emit(LaunchUIState.Cancelled(LaunchCancelReason.GAME_MISSING))
        }

        if (ReleaseManager.get(application).isInUpdate) {
            viewModelScope.launch {
                determineUpdateStatus()
            }
            return
        }

        if (loadFailure != null) {
            // last launch crashed, so cancel
            return _uiState.emit(LaunchUIState.Cancelled(LaunchCancelReason.LAST_LAUNCH_CRASHED))
        }

        if (GamePackageUtils.getGameVersionCode(packageManager) < Constants.SUPPORTED_VERSION_CODE_MIN) {
            return _uiState.emit(LaunchUIState.Cancelled(LaunchCancelReason.GAME_OUTDATED))
        }

        if (!LaunchUtils.isGeodeInstalled(application)) {
            return _uiState.emit(LaunchUIState.Cancelled(LaunchCancelReason.GEODE_NOT_FOUND))
        }

        // if forcing immediate launch, then act as if it's manually started (skips timers)
        val forceImmediate = launchArguments?.forceLaunch == true
        if (forceImmediate) {
            hasManuallyStarted = true
        }

        val loadAutomatically = PreferenceUtils.get(application).getBoolean(PreferenceUtils.Key.LOAD_AUTOMATICALLY)
        val forcePause = launchArguments?.forcePause == true
        if (!hasManuallyStarted && (forcePause || !loadAutomatically)) {
            return cancelLaunch(true)
        }

        if (!readyTimerPassed && !hasManuallyStarted) {
            return _uiState.emit(LaunchUIState.Working)
        }

        _uiState.emit(LaunchUIState.Ready)
    }

    suspend fun beginLaunchFlow(isRestart: Boolean = false) {
        if (isRestart) {
            hasManuallyStarted = true
        }

        if (_uiState.value !is LaunchUIState.Initial && _uiState.value !is LaunchUIState.Cancelled) {
            return
        }

        if (_uiState.value is LaunchUIState.Cancelled && !isRestart) {
            return
        }

        isCancelling = false

        if (!GamePackageUtils.isGameInstalled(application.packageManager)) {
            return _uiState.emit(LaunchUIState.Cancelled(LaunchCancelReason.GAME_MISSING))
        }

        if (readyTimer == null) {
            initReadyTimer()
        }

        val hasGeode = LaunchUtils.isGeodeInstalled(application)
        val shouldUpdate = PreferenceUtils.get(application).getBoolean(PreferenceUtils.Key.UPDATE_AUTOMATICALLY)
        if ((shouldUpdate && !hasCheckedForUpdates) || !hasGeode) {
            ReleaseManager.get(application).checkForUpdates(false)
        }

        preReadyCheck()
    }

    fun retryUpdate() {
        ReleaseManager.get(application).checkForUpdates(true)
        if (ReleaseManager.get(application).isInUpdate) {
            restoreUpdateState = _uiState.value
            viewModelScope.launch {
                determineUpdateStatus()
            }
            return
        }
    }

    suspend fun cancelLaunch(isAutomatic: Boolean = false) {
        // no need to double cancel
        if (_uiState.value is LaunchUIState.Cancelled) {
            return
        }

        val reason = if (isAutomatic) LaunchCancelReason.AUTOMATIC else LaunchCancelReason.MANUAL

        isCancelling = true

        val releaseManager = ReleaseManager.get(application)
        if (releaseManager.isInUpdate) {
            _uiState.emit(LaunchUIState.Cancelled(reason, true))
            releaseManager.cancelUpdate()
        }

        _uiState.emit(LaunchUIState.Cancelled(reason, false))
    }

    override fun onCleared() {
        super.onCleared()

        readyTimer?.cancel()
    }
}