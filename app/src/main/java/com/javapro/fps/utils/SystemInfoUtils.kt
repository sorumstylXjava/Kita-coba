package com.javapro.fps.utils

import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object SystemInfoUtils {

    private var cachedGpuLoadPath: String? = null
    private var cachedGpuFreqPath: String? = null
    private var lastFailReason: String = ""

    fun getFailReason() = lastFailReason

    fun getCpuUsage(): Float {
        return try {
            val process = Runtime.getRuntime().exec("top -n 1 -d 1")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var cpu = 0f
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("CPU") == true && line?.contains("%") == true) {
                    val parts = line!!.split(Regex("\\s+"))
                    parts.forEach {
                        if (it.endsWith("%")) {
                            cpu = it.removeSuffix("%").toFloatOrNull() ?: 0f
                            return@forEach
                        }
                    }
                    if (cpu > 0) break
                }
            }
            cpu
        } catch (e: Exception) {
            0f
        }
    }

    fun getCpuFreq(): Long {
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getGpuUsage(): Float {
        val path = cachedGpuLoadPath ?: findGpuLoadPath()
        if (path == null) {
            lastFailReason = "GPU load path not found"
            return -1f
        }

        return try {
            val raw = readFile(path)
            if (raw == null) {
                cachedGpuLoadPath = null
                return -1f
            }
            parseGpuLoad(raw)
        } catch (e: Exception) {
            cachedGpuLoadPath = null
            -1f
        }
    }

    fun getGpuFreq(): Long {
        val path = cachedGpuFreqPath ?: findGpuFreqPath()
        if (path == null) {
            return 0L
        }

        return try {
            val raw = readFile(path)
            if (raw == null) {
                cachedGpuFreqPath = null
                return 0L
            }
            raw.trim().filter { it.isDigit() }.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            cachedGpuFreqPath = null
            0L
        }
    }

    fun getTemperature(): Float {
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val temp = reader.readLine()?.toFloatOrNull() ?: 0f
            if (temp > 1000) temp / 1000f else temp
        } catch (e: Exception) {
            0f
        }
    }

    private fun findGpuLoadPath(): String? {
        // GPU Load Fallback list
        val fallbacks = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/gpuload",
            "/sys/class/devfreq/gpufreq/mali_ondemand/utilisation",
            "/sys/kernel/debug/ged/hal/gpu_utilization",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
            "/sys/devices/platform/1c500000.mali/utilization"
        )

        for (p in fallbacks) {
            if (File(p).exists()) {
                cachedGpuLoadPath = p
                return p
            }
        }

        // GPU Base Path Detection
        val basePath = detectGpuBasePath()
        if (basePath != null) {
            val possibleLoadNames = listOf("gpu_load", "load", "utilization", "cur_usage")
            for (name in possibleLoadNames) {
                val f = File(basePath, name)
                if (f.exists()) {
                    cachedGpuLoadPath = f.absolutePath
                    return cachedGpuLoadPath
                }
            }
        }

        return null
    }

    private fun findGpuFreqPath(): String? {
        val fallbacks = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/devfreq/gpufreq/cur_freq"
        )

        for (p in fallbacks) {
            if (File(p).exists()) {
                cachedGpuFreqPath = p
                return p
            }
        }

        val basePath = detectGpuBasePath()
        if (basePath != null) {
            val f = File(basePath, "cur_freq")
            if (f.exists()) {
                cachedGpuFreqPath = f.absolutePath
                return cachedGpuFreqPath
            }
        }
        return null
    }

    private fun detectGpuBasePath(): String? {
        // 1. Snapdragon/KGSL
        val kgslPath = "/sys/class/kgsl/kgsl-3d0/devfreq"
        if (File(kgslPath).exists()) return kgslPath

        // 2. MediaTek/Mali
        val mtkPath = "/sys/class/devfreq/gpufreq"
        if (File(mtkPath).exists()) return mtkPath

        // 3. Scan devfreq
        val devfreqDir = File("/sys/class/devfreq")
        if (devfreqDir.exists() && devfreqDir.isDirectory) {
            devfreqDir.listFiles()?.forEach { folder ->
                val name = folder.name.lowercase()
                if (name.contains("gpu") || name.contains("mali") || name.contains("kgsl")) {
                    return folder.absolutePath
                }
            }
        }
        return null
    }

    private fun parseGpuLoad(raw: String): Float {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return -1f

        // Handle "busy total" format (kgsl)
        if (trimmed.contains(" ")) {
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val busy = parts[0].toFloatOrNull()
                val total = parts[1].toFloatOrNull()
                if (busy != null && total != null && total > 0) {
                    return (busy / total) * 100f
                }
            }
        }

        // Handle "35%"
        if (trimmed.endsWith("%")) {
            return trimmed.removeSuffix("%").trim().toFloatOrNull() ?: -1f
        }

        // Handle GED/MTK or mixed strings
        val numericOnly = trimmed.filter { it.isDigit() || it == '.' }
        return numericOnly.toFloatOrNull() ?: -1f
    }

    private fun readFile(path: String): String? {
        return try {
            val content = File(path).readText().trim()
            if (content.isEmpty()) null else content
        } catch (e: Exception) {
            try {
                // Try with shell if direct file read fails
                val process = Runtime.getRuntime().exec(arrayOf("cat", path))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val line = reader.readLine()
                if (line != null && line.isNotBlank()) line.trim() else null
            } catch (e2: Exception) {
                null
            }
        }
    }
}
