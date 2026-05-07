package com.javapro.fps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javapro.fps.model.FpsStats
import com.javapro.fps.model.SystemStats

@Composable
fun StatsCard(
    title: String,
    value: String,
    unit: String = "",
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .padding(4.dp)
            .height(80.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryGrid(fpsStats: FpsStats, systemStats: SystemStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatsCard("AVG FPS", "%.1f".format(fpsStats.avgFps), modifier = Modifier.weight(1f))
            StatsCard("MIN FPS", "%.1f".format(fpsStats.minFps), modifier = Modifier.weight(1f))
            StatsCard("MAX FPS", "%.1f".format(fpsStats.maxFps), modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            StatsCard("1% LOW", "%.1f".format(fpsStats.fps1Low), modifier = Modifier.weight(1f))
            StatsCard("VAR", "%.2f".format(fpsStats.variance), modifier = Modifier.weight(1f))
            StatsCard("SMOOTH", "%.0f%%".format(fpsStats.smoothness), modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            StatsCard("TEMP", "%.1f".format(systemStats.temperature), "°C", modifier = Modifier.weight(1f))
            StatsCard("CPU", "%.0f%%".format(systemStats.cpuUsage), modifier = Modifier.weight(1f))
            val gpuValue = if (systemStats.gpuUsage >= 0) "%.0f%%".format(systemStats.gpuUsage) else "--"
            StatsCard("GPU", gpuValue, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ChartContainer(
    title: String,
    currentValue: String,
    data: List<Float>,
    lineColor: Color,
    minY: Float = 0f,
    maxY: Float = 100f
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = currentValue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = lineColor
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.height(100.dp).fillMaxWidth()) {
            RealtimeLineChart(
                data = data,
                lineColor = lineColor,
                minY = minY,
                maxY = maxY
            )
        }
    }
}
