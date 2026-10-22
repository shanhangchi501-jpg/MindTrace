package com.mindtrace.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class ChartPoint(val label: String, val value: Float?)

@Composable
fun LineTrendChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 10f,
    showLabels: Boolean = true,
    chartHeight: Int = 150
) {
    val values = points.mapNotNull { it.value }
    val description = if (values.isEmpty()) "暂无数据" else "趋势图，共 ${values.size} 个数据点"
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .height(chartHeight.dp)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatValue(maxValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
                Text(
                    formatValue(minValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight.dp)
                    .semantics { contentDescription = description }
            ) {
                val gridColor = Color.Gray.copy(alpha = 0.15f)
                val top = 8.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                val height = (bottom - top).coerceAtLeast(1f)
                val left = 4.dp.toPx()
                val right = size.width - 6.dp.toPx()
                val width = (right - left).coerceAtLeast(1f)

                repeat(3) { i ->
                    val y = top + height * i / 2f
                    drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                }

                if (values.isEmpty()) {
                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color.copy(alpha = 0.55f).toArgb()
                        textSize = 11.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "暂无数据",
                        size.width / 2f,
                        size.height / 2f,
                        textPaint
                    )
                    return@Canvas
                }
                val step = if (points.size <= 1) 0f else width / (points.size - 1)
                val range = (maxValue - minValue).coerceAtLeast(0.001f)
                fun x(index: Int) = left + step * index
                fun y(value: Float): Float =
                    top + height * (1f - ((value - minValue) / range).coerceIn(0f, 1f))

                var path: Path? = null
                points.forEachIndexed { index, point ->
                    val value = point.value
                    if (value == null) {
                        path?.let {
                            drawPath(it, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                        path = null
                    } else {
                        if (path == null) {
                            path = Path().apply { moveTo(x(index), y(value)) }
                        } else {
                            path?.lineTo(x(index), y(value))
                        }
                    }
                }
                path?.let {
                    drawPath(it, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                }

                points.forEachIndexed { index, point ->
                    val value = point.value ?: return@forEachIndexed
                    drawCircle(color = color, radius = 3.2.dp.toPx(), center = Offset(x(index), y(value)))
                    drawCircle(
                        color = color.copy(alpha = 0.18f),
                        radius = 6.5.dp.toPx(),
                        center = Offset(x(index), y(value))
                    )
                }
            }
        }
        if (showLabels && points.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, top = 4.dp)
            ) {
                val labels = when {
                    points.size <= 3 -> points.map { it.label }
                    else -> listOf(points.first().label, points[points.size / 2].label, points.last().label)
                }
                labels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            labels.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MiniLineChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val clean = values.filterNotNull()
    val rawMin = clean.minOrNull() ?: 0f
    val rawMax = clean.maxOrNull() ?: 1f
    val padding = if (rawMax - rawMin < 0.5f) 0.5f else 0f
    val min = rawMin - padding
    val max = rawMax + padding
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "最近趋势" }
    ) {
        if (values.isEmpty()) return@Canvas
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 4.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val width = (right - left).coerceAtLeast(1f)
        val range = (max - min).coerceAtLeast(0.5f)
        val step = if (values.size <= 1) 0f else width / (values.size - 1)
        fun y(value: Float) = top + (bottom - top) * (1f - ((value - min) / range).coerceIn(0f, 1f))

        var path: Path? = null
        values.forEachIndexed { index, value ->
            if (value == null) {
                path?.let { drawPath(it, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
                path = null
            } else {
                if (path == null) path = Path().apply { moveTo(left + step * index, y(value)) }
                else path?.lineTo(left + step * index, y(value))
            }
        }
        path?.let { drawPath(it, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
        values.forEachIndexed { index, value ->
            if (value != null) {
                drawCircle(color, 2.4.dp.toPx(), Offset(left + step * index, y(value)))
            }
        }
    }
}

@Composable
fun SimpleBarChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float = 10f,
    chartHeight: Int = 110
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEach { point ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = ((point.value ?: 0f) / maxValue).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.64f)
                                .height((chartHeight * fraction).coerceAtLeast(4f).dp)
                                .background(color.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.64f)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
        if (points.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                points.forEach { point ->
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun AdherenceRing(
    percent: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    hasData: Boolean = true
) {
    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = Color.Gray.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (hasData) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * percent.coerceIn(0, 100) / 100f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasData) "$percent%" else "—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasData) "按时服药" else "暂无记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FrequencyBars(
    items: List<Pair<String, Int>>,
    totalDays: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        items.forEach { (name, count) ->
            Column {
                Row {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$count 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((count.toFloat() / max).coerceIn(0.04f, 1f))
                            .height(8.dp)
                            .background(color.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                text = "这段时间还没有选择症状。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (totalDays > 0) {
            Text(
                text = "按出现天数统计，共 $totalDays 天记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

private fun formatValue(value: Float): String =
    if (value == value.roundToInt().toFloat()) value.roundToInt().toString()
    else String.format("%.1f", value)
