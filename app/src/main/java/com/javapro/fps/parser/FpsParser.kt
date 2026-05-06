package com.javapro.fps.parser

import com.javapro.fps.model.FrameSample
import kotlin.math.max

object FpsParser {

    /**
     * Parse gfxinfo framestats output
     * Output format usually has a header "Flags,IntendedVsync,Vsync,..."
     * followed by lines of numbers.
     */
    fun parseGfxInfo(output: String): List<FrameSample> {
        val lines = output.lines()
        val frameSamples = mutableListOf<FrameSample>()

        var inFrameStats = false
        for (line in lines) {
            if (line.contains("---PROFILEDATA---")) {
                inFrameStats = true
                continue
            }
            if (line.isBlank() || !line[0].isDigit()) continue
            if (inFrameStats) {
                val parts = line.split(",")
                if (parts.size >= 13) {
                    try {
                        // Vsync is index 1 or 2
                        // Frame duration can be calculated from Vsync and TotalDuration
                        // In many cases, we use (TotalDuration - Vsync) or similar
                        // For simplicity, let's use the intended vsync as timestamp
                        val intendedVsync = parts[1].toLong()
                        val vsync = parts[2].toLong()
                        val frameCompleted = parts[13].toLong()

                        val frameTimeNs = frameCompleted - intendedVsync
                        val frameTimeMs = frameTimeNs / 1_000_000f

                        if (frameTimeMs > 0) {
                            frameSamples.add(FrameSample(vsync, frameTimeMs))
                        }
                    } catch (e: Exception) {
                        // Skip invalid lines
                    }
                }
            }
        }
        return frameSamples
    }

    /**
     * Parse total frames from gfxinfo
     */
    fun parseTotalFrames(output: String): Long {
        val lines = output.lines()
        for (line in lines) {
            if (line.contains("Total frames rendered:")) {
                return line.split(":").getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    /**
     * Parse SurfaceFlinger latency output
     * Command: dumpsys SurfaceFlinger --latency <window>
     * Output:
     * refresh_period
     * timestamp timestamp timestamp
     * ...
     */
    fun parseSurfaceFlinger(output: String): List<FrameSample> {
        val lines = output.lines()
        if (lines.isEmpty()) return emptyList()

        val frameSamples = mutableListOf<FrameSample>()

        // First line is refresh period in nanoseconds
        val refreshPeriodNs = lines[0].trim().toLongOrNull() ?: 16666667L

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val parts = line.split(Regex("\\s+"))
            if (parts.size == 3) {
                try {
                    val desiredPresentTime = parts[0].toLong()
                    val actualPresentTime = parts[1].toLong()
                    val frameReadyTime = parts[2].toLong()

                    // actualPresentTime == 0 means frame not yet presented or dropped
                    // Long.MAX_VALUE is also a skip marker in some implementations
                    if (actualPresentTime <= 0 || actualPresentTime == Long.MAX_VALUE) continue

                    // We calculate frame time as the interval between frames or compared to budget
                    // For FrameSample, we'll store the timestamp and we can calculate FPS from intervals later
                    // Or we can estimate frameTimeMs here.
                    // Using interval from previous frame is more accurate for FPS.
                    frameSamples.add(FrameSample(actualPresentTime, 0f))
                } catch (e: Exception) {
                    // Skip
                }
            }
        }

        // Calculate frameTimeMs based on intervals if we have at least 2 frames
        val result = mutableListOf<FrameSample>()
        for (i in 1 until frameSamples.size) {
            val prev = frameSamples[i-1].timestamp
            val curr = frameSamples[i].timestamp
            val diffNs = curr - prev
            if (diffNs > 0) {
                result.add(FrameSample(curr, diffNs / 1_000_000f))
            }
        }

        return result
    }

    fun calculateStats(samples: List<FrameSample>, refreshRate: Float): com.javapro.fps.model.FpsStats {
        if (samples.isEmpty()) return com.javapro.fps.model.FpsStats()

        val frameTimes = samples.map { it.frameTimeMs }.filter { it > 0 }
        if (frameTimes.isEmpty()) return com.javapro.fps.model.FpsStats()

        val avgFrameTime = frameTimes.average().toFloat()
        val currentFps = if (avgFrameTime > 0) 1000f / frameTimes.last() else 0f
        val avgFps = 1000f / avgFrameTime

        val minFrameTime = frameTimes.minOrNull() ?: 0f
        val maxFrameTime = frameTimes.maxOrNull() ?: 0f

        val maxFps = if (minFrameTime > 0) 1000f / minFrameTime else 0f
        val minFps = if (maxFrameTime > 0) 1000f / maxFrameTime else 0f

        // Variance
        val variance = frameTimes.map { (it - avgFrameTime) * (it - avgFrameTime) }.average().toFloat()

        // Jank Detection (Adaptive)
        val frameBudget = 1000f / refreshRate
        val jankThreshold = frameBudget * 1.5f
        val bigJankThreshold = frameBudget * 2.0f

        var jankCount = 0
        var bigJankCount = 0
        frameTimes.forEach {
            if (it > bigJankThreshold) bigJankCount++
            else if (it > jankThreshold) jankCount++
        }

        // Smoothness (simplified: 100 - (jank percentage))
        val smoothness = max(0f, 100f - (jankCount + bigJankCount * 2f) / frameTimes.size * 100f)

        // 1% and 5% low
        val sortedFps = frameTimes.map { 1000f / it }.sorted()
        val fps1Low = if (sortedFps.isNotEmpty()) sortedFps[(sortedFps.size * 0.01).toInt()] else 0f
        val fps5Low = if (sortedFps.isNotEmpty()) sortedFps[(sortedFps.size * 0.05).toInt()] else 0f

        return com.javapro.fps.model.FpsStats(
            currentFps = currentFps,
            avgFps = avgFps,
            minFps = minFps,
            maxFps = maxFps,
            frameTimeMs = frameTimes.last(),
            maxFrameTimeMs = maxFrameTime,
            variance = variance,
            smoothness = smoothness,
            fps1Low = fps1Low,
            fps5Low = fps5Low,
            jankCount = jankCount,
            bigJankCount = bigJankCount
        )
    }
}
