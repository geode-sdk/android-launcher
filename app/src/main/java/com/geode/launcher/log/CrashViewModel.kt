package com.geode.launcher.log

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geode.launcher.utils.LaunchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class CrashDump @OptIn(ExperimentalTime::class) constructor(
    val filename: String,
    val lastModified: Instant
)

class CrashViewModel(private val application: Application) : ViewModel() {
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application

                CrashViewModel(
                    application = application
                )
            }
        }
    }

    private val _lineState = mutableStateListOf<CrashDump>()
    val lineState = _lineState

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var failedToLoad = false

    private var logJob: Job? = null

    private fun getCrashList(): List<CrashDump> {
        val crashDirectory = LaunchUtils.getCrashDirectory(application)
        if (!crashDirectory.exists()) {
            return emptyList()
        }

        val children = crashDirectory.listFiles {
            // ignore indicator files (including old file)
                _, name -> name != LaunchUtils.CRASH_INDICATOR_NAME && name != "last-pid"
        } ?: return emptyList()

        return children
            .map {
                CrashDump(
                    filename = it.name,
                    lastModified = Instant.fromEpochMilliseconds(it.lastModified())
                )
            }
            .sortedByDescending { it.lastModified }
    }

    fun clearCrashes() {
        logJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            val crashDirectory = LaunchUtils.getCrashDirectory(application)
            if (crashDirectory.exists()) {
                val files = crashDirectory.listFiles()
                files?.forEach {
                    it.delete()
                }
            }
        }

        _lineState.clear()
    }

    private fun loadLogs() {
        _isLoading.value = true

        logJob?.cancel()
        logJob = viewModelScope.launch(Dispatchers.IO) {
            val lines = getCrashList()

            _lineState.clear()
            _lineState.addAll(lines)

            _isLoading.value = false

            logJob = null
        }
    }

    fun removeCrash(filename: String) {
        logJob?.cancel()

        val crashPath = File(LaunchUtils.getCrashDirectory(application), filename)
        if (crashPath.exists()) {
            crashPath.delete()
            loadLogs()
        }
    }

    init {
        loadLogs()
    }
}