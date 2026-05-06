package com.javapro.fps.model

data class FrameSample(
    val timestamp: Long,
    val frameTimeMs: Float
)

data class FpsStats(
    val currentFps: Float = 0f,
    val avgFps: Float = 0f,
    val minFps: Float = 0f,
    val maxFps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val maxFrameTimeMs: Float = 0f,
    val variance: Float = 0f,
    val smoothness: Float = 0f,
    val fps1Low: Float = 0f,
    val fps5Low: Float = 0f,
    val jankCount: Int = 0,
    val bigJankCount: Int = 0
)

data class SystemStats(
    val cpuUsage: Float = 0f,
    val cpuFreq: Long = 0L,
    val gpuUsage: Float = 0f,
    val gpuFreq: Long = 0L,
    val temperature: Float = 0f,
    val powerW: Float = 0f
)

data class MonitorState(
    val fpsStats: FpsStats = FpsStats(),
    val systemStats: SystemStats = SystemStats(),
    val isRunning: Boolean = false,
    val activeBackend: String = "None",
    val targetPackage: String = ""
)
