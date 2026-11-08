package com.example.superduperfitnesstracker3000.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val axisDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

data class ChartPoint(val date: LocalDate, val value: Double)

private const val LEFT_AXIS = 44f
private const val BOTTOM_AXIS = 22f

/**
 * Weight line chart: raw weigh-ins as faint dots/line, moving average as a bold line,
 * optional dashed goal line. Tap to inspect a day.
 */
@Composable
fun WeightLineChart(
    raw: List<ChartPoint>,
    average: List<ChartPoint>,
    goal: Double?,
    modifier: Modifier = Modifier,
) {
    if (raw.isEmpty()) {
        EmptyHint("No weigh-ins in this range", modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val rawColor = MaterialTheme.colorScheme.outline
    val goalColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    var selected by remember(raw) { mutableStateOf<Int?>(null) }

    val minX = raw.first().date.toEpochDay().toFloat()
    val maxX = raw.last().date.toEpochDay().toFloat().let { if (it == minX) it + 1 else it }
    val values = raw.map { it.value } + average.map { it.value } + listOfNotNull(goal)
    var minY = floor(values.min() - 0.5)
    var maxY = ceil(values.max() + 0.5)
    if (maxY - minY < 2) { minY -= 1; maxY += 1 }

    Column(modifier) {
        val sel = selected?.let { raw.getOrNull(it) }
        Text(
            text = if (sel != null) {
                val avg = average.firstOrNull { it.date == sel.date }?.value
                "${sel.date.pretty()}: ${fmt1(sel.value)} kg" + (avg?.let { " · 7-day avg ${fmt1(it)} kg" } ?: "")
            } else "Tap the chart to see a day",
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(raw) {
                    detectTapGestures { pos ->
                        val plotW = size.width - LEFT_AXIS
                        val x = (pos.x - LEFT_AXIS) / plotW * (maxX - minX) + minX
                        selected = raw.indices.minByOrNull { abs(raw[it].date.toEpochDay() - x) }
                    }
                }
        ) {
            val plot = Size(size.width - LEFT_AXIS, size.height - BOTTOM_AXIS)
            fun px(d: LocalDate) = LEFT_AXIS + (d.toEpochDay() - minX) / (maxX - minX) * plot.width
            fun py(v: Double) = (plot.height - (v - minY) / (maxY - minY) * plot.height).toFloat()

            drawYGrid(measurer, minY, maxY, 4, plot, gridColor, labelColor) { fmt1(it) }
            drawXLabels(measurer, raw.first().date, raw.last().date, ::px, plot, labelColor)

            goal?.takeIf { it in minY..maxY }?.let {
                drawLine(goalColor, Offset(LEFT_AXIS, py(it)), Offset(size.width, py(it)), 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }

            drawPolyline(raw.map { Offset(px(it.date), py(it.value)) }, rawColor.copy(alpha = 0.5f), 2f)
            raw.forEach { drawCircle(rawColor, 3.5f, Offset(px(it.date), py(it.value))) }
            drawPolyline(average.map { Offset(px(it.date), py(it.value)) }, primary, 6f)

            sel?.let {
                val x = px(it.date)
                drawLine(labelColor.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, plot.height), 1.5f)
                drawCircle(primary, 7f, Offset(x, py(it.value)))
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(rawColor, "Daily")
            LegendItem(primary, "7-day average")
            if (goal != null) LegendItem(goalColor, "Goal")
        }
    }
}

/** Bar chart with one bar per day (missing days left empty) and an optional dashed target line. */
@Composable
fun DailyBarChart(
    points: List<ChartPoint>,
    from: LocalDate,
    to: LocalDate,
    target: Double?,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    emptyText: String = "No calculated food in this range",
) {
    if (points.isEmpty()) {
        EmptyHint(emptyText, modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val targetColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val byDate = points.associate { it.date to it.value }
    val days = (0..(to.toEpochDay() - from.toEpochDay())).map { from.plusDays(it) }
    val maxY = (listOfNotNull(target) + points.map { it.value }).max().let { niceCeil(it * 1.1) }
    var selected by remember(points, from) { mutableStateOf<LocalDate?>(null) }

    Column(modifier) {
        val sel = selected
        Text(
            text = if (sel != null) "${sel.pretty()}: ${byDate[sel]?.let { fmt0(it) + " $unit" } ?: "no data"}"
            else "Tap a bar to see the value",
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(days) {
                    detectTapGestures { pos ->
                        val slot = (size.width - LEFT_AXIS) / days.size
                        val i = ((pos.x - LEFT_AXIS) / slot).toInt().coerceIn(0, days.lastIndex)
                        selected = days[i]
                    }
                }
        ) {
            val plot = Size(size.width - LEFT_AXIS, size.height - BOTTOM_AXIS)
            val slot = plot.width / days.size
            fun py(v: Double) = (plot.height - v / maxY * plot.height).toFloat()

            drawYGrid(measurer, 0.0, maxY, 4, plot, gridColor, labelColor) { fmt0(it) }
            drawXLabels(measurer, from, to, { d -> LEFT_AXIS + (d.toEpochDay() - from.toEpochDay() + 0.5f) * slot }, plot, labelColor)

            days.forEachIndexed { i, d ->
                val v = byDate[d] ?: return@forEachIndexed
                val barW = (slot * 0.7f).coerceAtLeast(1.5f)
                val x = LEFT_AXIS + i * slot + (slot - barW) / 2
                drawRect(
                    color = if (d == sel) color.copy(alpha = 0.6f) else color,
                    topLeft = Offset(x, py(v)),
                    size = Size(barW, plot.height - py(v)),
                )
            }
            target?.let {
                drawLine(targetColor, Offset(LEFT_AXIS, py(it)), Offset(size.width, py(it)), 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        if (target != null) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(color, unit)
                LegendItem(targetColor, "Target ${fmt0(target)}")
            }
        }
    }
}

/** Circular progress ring with content in the middle (used for daily water). */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val track = color.copy(alpha = 0.15f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = strokeWidth.toPx()
            val arcSize = Size(size.minDimension - w, size.minDimension - w)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val step = when {
        v > 2000 -> 500.0
        v > 500 -> 250.0
        v > 100 -> 50.0
        v > 20 -> 10.0
        else -> 5.0
    }
    return ceil(v / step) * step
}

private fun DrawScope.drawPolyline(points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawYGrid(
    measurer: TextMeasurer,
    minY: Double,
    maxY: Double,
    steps: Int,
    plot: Size,
    gridColor: Color,
    labelColor: Color,
    label: (Double) -> String,
) {
    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    for (i in 0..steps) {
        val v = minY + (maxY - minY) * i / steps
        val y = plot.height - plot.height * i / steps
        drawLine(gridColor, Offset(LEFT_AXIS, y), Offset(size.width, y), 1f)
        val layout = measurer.measure(label(v), style)
        drawText(layout, topLeft = Offset(LEFT_AXIS - layout.size.width - 6f, (y - layout.size.height / 2).coerceIn(0f, size.height - layout.size.height)))
    }
}

private fun DrawScope.drawXLabels(
    measurer: TextMeasurer,
    from: LocalDate,
    to: LocalDate,
    px: (LocalDate) -> Float,
    plot: Size,
    labelColor: Color,
) {
    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    val span = to.toEpochDay() - from.toEpochDay()
    val dates = if (span <= 0) listOf(from) else listOf(from, from.plusDays(span / 2), to).distinct()
    dates.forEach { d ->
        val layout = measurer.measure(d.format(axisDate), style)
        val x = (px(d) - layout.size.width / 2).coerceIn(LEFT_AXIS, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, plot.height + 4f))
    }
}
