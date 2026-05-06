package com.javapro.fps.monitor

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.javapro.fps.model.FrameSample
import com.javapro.fps.parser.FpsParser
import com.javapro.utils.RootUtils
import com.javapro.utils.ShizukuManager
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

class FpsMonitorManager(private val context: Context) {

    private val _frameSamples = MutableStateFlow<List<FrameSample>>(emptyList())
    val frameSamples = _frameSamples.asStateFlow()

    private val _activeBackend = MutableStateFlow("None")
    val activeBackend = _activeBackend.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var targetPackage: String = ""
    private var refreshRate: Float = 60f

    private var lastTotalFrames: Long = 0L
    private var lastTimestamp: Long = 0L

    enum class Backend {
        SURFACE_FLINGER,
        GFXINFO,
        NONE
    }

    private var currentBackend = Backend.NONE

    fun startMonitoring(packageName: String) {
        targetPackage = packageName
        refreshRate = getRefreshRate()

        monitorJob?.cancel()
        monitorJob = scope.launch {
            autoDetectAndRun()
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        _activeBackend.value = "None"
        currentBackend = Backend.NONE
    }

    private suspend fun autoDetectAndRun() {
        // Try SurfaceFlinger first (often requires root/shizuku)
        if (trySurfaceFlinger()) return

        // Fallback to gfxinfo
        if (tryGfxInfo()) return

        _activeBackend.value = "Failed"
    }

    private suspend fun trySurfaceFlinger(): Boolean {
        _activeBackend.value = "SurfaceFlinger (Detecting...)"
        val output = runCommand("dumpsys SurfaceFlinger --latency")
        val lines = output.lines().filter { it.isNotBlank() }

        if (lines.size > 1 && !output.contains("Permission denied")) {
            android.util.Log.d("FpsMonitor", "SurfaceFlinger valid: ${lines.size} lines")
            currentBackend = Backend.SURFACE_FLINGER
            _activeBackend.value = "SurfaceFlinger"
            runSurfaceFlingerLoop()
            return true
        } else {
            android.util.Log.d("FpsMonitor", "SurfaceFlinger invalid (only ${lines.size} lines or permission denied)")
        }
        return false
    }

    private suspend fun tryGfxInfo(): Boolean {
        _activeBackend.value = "GfxInfo (Detecting...)"
        val output = runCommand("dumpsys gfxinfo $targetPackage framestats")
        if (output.isNotBlank() && !output.contains("Permission denied")) {
            currentBackend = Backend.GFXINFO
            _activeBackend.value = "GfxInfo"
            runGfxInfoLoop()
            return true
        }
        return false
    }

    private suspend fun runSurfaceFlingerLoop() {
        while (coroutineContext.isActive) {
            val output = runCommand("dumpsys SurfaceFlinger --latency")
            val lines = output.lines().filter { it.isNotBlank() }

            if (lines.size <= 1 || output.contains("Permission denied")) {
                android.util.Log.d("FpsMonitor", "SurfaceFlinger became invalid, falling back...")
                if (tryGfxInfo()) break
            }
            val samples = FpsParser.parseSurfaceFlinger(output)
            if (samples.isNotEmpty()) {
                _frameSamples.value = samples
            }
            delay(500) // Poll every 500ms
        }
    }

    private suspend fun runGfxInfoLoop() {
        lastTotalFrames = 0L
        lastTimestamp = System.currentTimeMillis()

        while (coroutineContext.isActive) {
            val output = runCommand("dumpsys gfxinfo $targetPackage framestats")
            if (output.isBlank() || output.contains("Permission denied")) {
                _activeBackend.value = "Failed"
                break
            }

            val currentTimestamp = System.currentTimeMillis()
            val deltaTimeMs = currentTimestamp - lastTimestamp
            val totalFrames = FpsParser.parseTotalFrames(output)

            android.util.Log.d("FpsMonitor", "GfxInfo output length: ${output.length}, totalFrames: $totalFrames")

            if (lastTotalFrames > 0 && deltaTimeMs > 0) {
                val deltaFrames = totalFrames - lastTotalFrames
                val fps = deltaFrames * 1000f / deltaTimeMs

                android.util.Log.d("FpsMonitor", "DeltaFrames: $deltaFrames, DeltaTime: $deltaTimeMs, Calculated FPS: $fps")

                // If delta-based FPS is valid, we can use it to augment or replace sample-based stats
                // For now, let's parse samples too for frame time detail
                val samples = FpsParser.parseGfxInfo(output)
                if (samples.isNotEmpty()) {
                    // Update samples with calculated FPS from delta if needed
                    // but for now calculateStats handles it from samples.
                    _frameSamples.value = samples
                }
            }

            lastTotalFrames = totalFrames
            lastTimestamp = currentTimestamp

            delay(500)
        }
    }

    private fun runCommand(command: String): String {
        // 1. Try Shizuku
        if (ShizukuManager.isAvailable()) {
            val res = ShizukuManager.runCommand(command)
            if (res.isNotBlank()) return res
        }

        // 2. Try Root via Shell
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            if (output.isNotBlank()) return output.toString()
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Try Normal Shell (may work for some dumpsys if developer options are on)
        try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            return output.toString()
        } catch (e: Exception) {
            // Ignore
        }

        return ""
    }

    private fun getRefreshRate(): Float {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.refreshRate
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.refreshRate
            }
        } catch (e: Exception) {
            60f
        }
    }
}
