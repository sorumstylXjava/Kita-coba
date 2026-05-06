package com.javapro.fps.utils

import java.io.BufferedReader
import java.io.InputStreamReader

object SystemInfoUtils {

    fun getCpuUsage(): Float {
        return try {
            val process = Runtime.getRuntime().exec("top -n 1 -d 1")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var cpu = 0f
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("CPU") == true && line?.contains("%") == true) {
                    // Simplistic parse
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
        // Device specific, usually /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()?.removeSuffix("%")?.trim()?.toFloatOrNull() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }

    fun getGpuFreq(): Long {
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/class/kgsl/kgsl-3d0/gpuclk")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
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
}
