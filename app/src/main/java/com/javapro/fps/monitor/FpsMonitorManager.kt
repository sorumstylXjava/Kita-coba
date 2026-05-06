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
        if (output.isNotBlank() && !output.contains("Permission denied")) {
            currentBackend = Backend.SURFACE_FLINGER
            _activeBackend.value = "SurfaceFlinger"
            runSurfaceFlingerLoop()
            return true
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
            if (output.isBlank() || output.contains("Permission denied")) {
                // Fallback
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
        while (coroutineContext.isActive) {
            val output = runCommand("dumpsys gfxinfo $targetPackage framestats")
            if (output.isBlank() || output.contains("Permission denied")) {
                _activeBackend.value = "Failed"
                break
            }
            val samples = FpsParser.parseGfxInfo(output)
            if (samples.isNotEmpty()) {
                _frameSamples.value = samples
            }
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
