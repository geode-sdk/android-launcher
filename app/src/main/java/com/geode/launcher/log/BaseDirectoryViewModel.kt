package com.geode.launcher.log

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

abstract class BaseDirectoryViewModel<T>(protected val baseDirectory: File) : ViewModel() {
    private val _lineState = mutableStateListOf<T>()
    val lineState = _lineState

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var logJob: Job? = null

    abstract fun getFileList(): List<T>

    abstract fun getItemKey(item: T): Any

    open fun clearAllFiles() {
        logJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            if (baseDirectory.exists()) {
                val files = baseDirectory.listFiles()
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
            val lines = getFileList()

            _lineState.clear()
            _lineState.addAll(lines)

            _isLoading.value = false

            logJob = null
        }
    }

    fun removeFile(filename: String) {
        logJob?.cancel()

        val crashPath = File(baseDirectory, filename)
        if (crashPath.exists()) {
            crashPath.delete()
            loadLogs()
        }
    }

    init {
        loadLogs()
    }
}