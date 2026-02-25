package com.geode.launcher.log

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geode.launcher.utils.LaunchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Instant

data class GeodeLog(
    val filename: String,
    val lastModified: Instant,
    val fileSize: Long,
)

class GeodeLogsViewModel(crashDirectory: File) : BaseDirectoryViewModel<GeodeLog>(crashDirectory) {
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val crashDirectory = LaunchUtils.getGeodeLogsDirectory(application)

                GeodeLogsViewModel(crashDirectory)
            }
        }
    }

    override fun getItemKey(item: GeodeLog) = item.filename

    override fun getFileList(): List<GeodeLog> {
        if (!baseDirectory.exists()) {
            return emptyList()
        }

        val children = baseDirectory.listFiles()
            ?: return emptyList()

        return children
            .map {
                GeodeLog(
                    filename = it.name,
                    lastModified = Instant.fromEpochMilliseconds(it.lastModified()),
                    fileSize = it.length()
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun getFileText(filename: String): List<String> {
        val logFile = File(baseDirectory, filename)
        if (!logFile.exists()) {
            return listOf("File does not exist!")
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                logFile.readLines()
            }
        }.getOrDefault(emptyList())
    }
}