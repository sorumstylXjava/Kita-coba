package com.javapro.fps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RealtimeLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Cyan,
    fillColor: Color = lineColor.copy(alpha = 0.2f),
    maxDataPoints: Int = 100,
    minY: Float = 0f,
    maxY: Float = 120f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (data.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val spacing = width / (maxDataPoints - 1)

        val path = Path()
        val fillPath = Path()

        val normalizedData = data.takeLast(maxDataPoints)

        normalizedData.forEachIndexed { index, value ->
            val x = index * spacing
            val y = height - ((value - minY) / (maxY - minY) * height).coerceIn(0f, height)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (index == normalizedData.lastIndex) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            color = fillColor
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
