package com.javapro.fps.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javapro.fps.model.FpsStats
import com.javapro.fps.model.MonitorState
import com.javapro.fps.model.SystemStats
import com.javapro.fps.monitor.FpsMonitorManager
import com.javapro.fps.parser.FpsParser
import com.javapro.fps.service.FpsOverlayService
import com.javapro.fps.utils.CircularBuffer
import com.javapro.fps.utils.SystemInfoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FpsViewModel(application: Application) : AndroidViewModel(application) {

    private val monitorManager = FpsMonitorManager(application)

    private val _uiState = MutableStateFlow(MonitorState())
    val uiState = _uiState.asStateFlow()

    // History Buffers
    val fpsHistory = CircularBuffer(100)
    val frameTimeHistory = CircularBuffer(100)
    val cpuHistory = CircularBuffer(100)
    val gpuHistory = CircularBuffer(100)
    val tempHistory = CircularBuffer(100)
    val powerHistory = CircularBuffer(100)

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            monitorManager.activeBackend.collect { backend ->
                _uiState.update { it.copy(activeBackend = backend) }
            }
        }

        viewModelScope.launch {
            monitorManager.frameSamples.collect { samples ->
                if (samples.isNotEmpty()) {
                    val stats = FpsParser.calculateStats(samples, 60f) // TODO: Get actual refresh rate
                    _uiState.update { it.copy(fpsStats = stats) }

                    fpsHistory.add(stats.currentFps)
                    frameTimeHistory.add(stats.frameTimeMs)
                }
            }
        }
    }

    fun toggleMonitoring(packageName: String) {
        if (_uiState.value.isRunning) {
            stopMonitoring()
        } else {
            startMonitoring(packageName)
        }
    }

    private fun startMonitoring(packageName: String) {
        _uiState.update { it.copy(isRunning = true, targetPackage = packageName) }
        monitorManager.startMonitoring(packageName)

        val intent = Intent(getApplication(), FpsOverlayService::class.java).apply {
            putExtra("TARGET_PACKAGE", packageName)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }

        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val systemStats = SystemStats(
                    cpuUsage = SystemInfoUtils.getCpuUsage(),
                    cpuFreq = SystemInfoUtils.getCpuFreq(),
                    gpuUsage = SystemInfoUtils.getGpuUsage(),
                    gpuFreq = SystemInfoUtils.getGpuFreq(),
                    temperature = SystemInfoUtils.getTemperature(),
                    powerW = 0f // Needs specific implementation
                )

                _uiState.update { it.copy(systemStats = systemStats) }

                cpuHistory.add(systemStats.cpuUsage)
                gpuHistory.add(systemStats.gpuUsage)
                tempHistory.add(systemStats.temperature)

                delay(1000)
            }
        }
    }

    private fun stopMonitoring() {
        _uiState.update { it.copy(isRunning = false) }
        monitorManager.stopMonitoring()
        getApplication<Application>().stopService(Intent(getApplication(), FpsOverlayService::class.java))
        pollingJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
