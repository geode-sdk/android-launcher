package com.geode.launcher.log

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geode.launcher.utils.LaunchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class CrashDump @OptIn(ExperimentalTime::class) constructor(
    val filename: String,
    val lastModified: Instant
)

class CrashViewModel(crashDirectory: File) : BaseDirectoryViewModel<CrashDump>(crashDirectory) {
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val crashDirectory = LaunchUtils.getCrashDirectory(application)

                CrashViewModel(crashDirectory)
            }
        }
    }

    var hasIndicator = false

    override fun getFileList(): List<CrashDump> {
        val crashDirectory = baseDirectory

        if (!crashDirectory.exists()) {
            return emptyList()
        }

        val children = crashDirectory.listFiles {
            // ignore indicator files (including old file)
                _, name -> name != LaunchUtils.CRASH_INDICATOR_NAME && name != "last-pid"
        } ?: return emptyList()

        hasIndicator = File(crashDirectory, LaunchUtils.CRASH_INDICATOR_NAME).exists()

        return children
            .map {
                CrashDump(
                    filename = it.name,
                    lastModified = Instant.fromEpochMilliseconds(it.lastModified())
                )
            }
            .sortedByDescending { it.lastModified }
    }

    fun clearIndicator() {
        viewModelScope.launch(Dispatchers.IO) {
            File(baseDirectory, LaunchUtils.CRASH_INDICATOR_NAME).delete()

            hasIndicator = false
        }
    }

    fun createIndicator() {
        viewModelScope.launch(Dispatchers.IO) {
            File(baseDirectory, LaunchUtils.CRASH_INDICATOR_NAME).createNewFile()

            hasIndicator = true
        }
    }

    override fun clearAllFiles() {
        super.clearAllFiles()
        hasIndicator = false
    }
}