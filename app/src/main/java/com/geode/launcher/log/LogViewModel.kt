package com.geode.launcher.log

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import okio.buffer
import okio.source
import java.io.EOFException
import java.util.LinkedList

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

    private var _isLoading = MutableStateFlow(false)
    var isLoading = _isLoading.asStateFlow()

    private suspend fun logOutput(): List<LogLine> {
        val logLines = LinkedList<LogLine>()

        // -B = binary format, -d = dump logs
        val logSource = Runtime.getRuntime().exec("logcat -B -d")
            .inputStream.source().buffer()

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
        }

        return logLines
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            // -c = clear
            Runtime.getRuntime().exec("logcat -c")
                .waitFor()
        }

        _lineState.clear()
    }

    fun getLogData(): String {
        return _lineState.joinToString("\n") { it.asSimpleString }
    }

    init {
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val lines = logOutput()
            _lineState.addAll(lines)
            _isLoading.value = false
        }
    }
}