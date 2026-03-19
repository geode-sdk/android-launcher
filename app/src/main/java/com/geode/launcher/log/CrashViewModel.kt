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
import kotlin.time.Instant

data class CrashDump(
    val filename: String,
    val lastModified: Instant,
    val fullPath: String,
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

    override fun getItemKey(item: CrashDump) = item.filename

    override fun getFileList(): List<CrashDump> {
        val crashDirectory = baseDirectory

        if (!crashDirectory.exists()) {
            return emptyList()
        }

        val children = crashDirectory.listFiles {
            // ignore indicator files (including old file)
                _, name -> !LaunchUtils.CRASH_INDICATOR_NAMES.contains(name)
        } ?: return emptyList()

        hasIndicator = LaunchUtils.CRASH_INDICATOR_NAMES.any {
            File(crashDirectory, it).exists()
        }

        return children
            .map {
                CrashDump(
                    filename = it.name,
                    lastModified = Instant.fromEpochMilliseconds(it.lastModified()),
                    fullPath = it.path,
                )
            }
            .sortedByDescending { it.lastModified }
    }

    fun clearIndicator() {
        viewModelScope.launch(Dispatchers.IO) {
            LaunchUtils.CRASH_INDICATOR_NAMES.forEach {
                val crashFile = File(baseDirectory, it)
                if (crashFile.exists()) {
                    crashFile.delete()
                }
            }

            hasIndicator = false
        }
    }

    fun createIndicator() {
        viewModelScope.launch(Dispatchers.IO) {
            val crashFile = File(baseDirectory, LaunchUtils.CRASH_INDICATOR_NAMES.first())
            crashFile.createNewFile()

            hasIndicator = true
        }
    }

    override fun clearAllFiles() {
        super.clearAllFiles()
        hasIndicator = false
    }
}