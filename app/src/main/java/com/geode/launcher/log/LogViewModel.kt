package com.geode.launcher.log

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import okio.buffer
import okio.source
import java.io.EOFException
import java.io.IOException

/**
 * ViewModel to manage log output from logcat.
 * Currently, it loads the list of logs at init with no ability to read additional logs.
 */
class LogViewModel: ViewModel() {
    // the original version of this code did support streaming btw
    // the states were way too annoying to manage across multiple threads
    // yay!

    private val _lineState = mutableStateListOf<LogLine>()
    val lineState = _lineState

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var failedToLoad = false

    private var logJob: Job? = null

    var filterCrashes = false
        private set

    fun toggleCrashBuffer() {
        filterCrashes = !filterCrashes

        _lineState.clear()
        loadLogs()
    }

    private suspend fun logOutput(): List<LogLine> {
        val logLines = ArrayList<LogLine>()

        // -B = binary format, -d = dump logs
        val logCommand = if (filterCrashes) "logcat -b crash -B -d"
            else "logcat -B -d"

        val logProcess = try {
            Runtime.getRuntime().exec(logCommand)
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            logLines += LogLine.showException(ioe)

            failedToLoad = true

            return logLines
        }

        val logSource = logProcess.inputStream.source().buffer()

        try {
            coroutineScope {
                // this runs until the stream is exhausted
                while (true) {
                    val line = LogLine.fromBufferedSource(logSource)

                    // skip debug/verbose lines
                    if (line.priority >= LogPriority.INFO) {
                        logLines += line
                    }

                    yield()
                }
            }
        } catch (_: EOFException) {
            // ignore, end of file reached
        } catch (e: Exception) {
            e.printStackTrace()
            logLines += LogLine.showException(e)

            // technically it maybe didn't completely fail...
            failedToLoad = true
        }

        return logLines
    }

    fun clearLogs() {
        logJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            // -c = clear
            Runtime.getRuntime().exec("logcat -c")
                .waitFor()
        }

        _lineState.clear()
    }

    private fun dumpLogcatText(): String {
        val logCommand = if (filterCrashes) "logcat -b crash -d"
            else "logcat -d"

        val logProcess = try {
            Runtime.getRuntime().exec(logCommand)
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            return ioe.stackTraceToString()
        }

        return logProcess.inputStream.bufferedReader().readText()
    }

    fun getLogData(): String = if (failedToLoad) {
            dumpLogcatText()
        } else {
            _lineState.joinToString("\n") { it.asSimpleString }
        }

    private fun loadLogs() {
        _isLoading.value = true

        logJob?.cancel()
        logJob = viewModelScope.launch(Dispatchers.IO) {
            val lines = logOutput()
            _lineState.addAll(lines)
            _isLoading.value = false

            logJob = null
        }
    }

    init {
        loadLogs()
    }
}