package com.guibilar.financeinsights.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import kotlin.math.min

private const val DIMMED = 0.35f

/**
 * Bars stacked per label (a month's spending by category), with an optional dashed average. Tapping a column selects
 * it (tapping it again clears the selection); the others dim so the selected one stands out.
 */
@Composable
fun StackedBarChart(
    labels: List<String>,
    series: List<ChartSeries>,
    description: String,
    modifier: Modifier = Modifier,
    average: Double? = null,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    height: Dp = 220.dp,
) {
    val colors = LocalChartColors.current
    val measurer = rememberTextMeasurer()
    val positive = labels.indices.map { i -> series.sumOf { it.values.getOrElse(i) { 0.0 }.coerceAtLeast(0.0) } }
    val negative = labels.indices.map { i -> series.sumOf { it.values.getOrElse(i) { 0.0 }.coerceAtMost(0.0) } }
    val scale = ValueScale.of(negative.minOrNull() ?: 0.0, maxOf(positive.maxOrNull() ?: 0.0, average ?: 0.0))
    val grow = growIn(series.map { it.values })
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .barSemantics(description, labels, selected, onSelect)
            .pointerInput(labels, selected) {
                detectTapGestures { offset ->
                    val plot = Rect(AxisWidth.toPx(), 0f, size.width.toFloat(), size.height.toFloat())
                    val index = slotAt(offset.x, plot, labels.size)
                    onSelect(if (index == selected) null else index)
                }
            },
    ) {
        val plot = plotArea(measurer, colors)
        drawValueAxis(plot, scale, colors, measurer)
        val slot = plot.width / labels.size.coerceAtLeast(1)
        val barWidth = min(slot * 0.55f, 28.dp.toPx())
        val gap = 1.5.dp.toPx()
        val zero = plot.bottom - scale.fraction(0.0) * plot.height
        labels.indices.forEach { i ->
            val alpha = if (selected == null || selected == i) 1f else DIMMED
            val left = plot.left + slot * i + (slot - barWidth) / 2
            var up = zero
            var down = zero
            series.forEach { s ->
                val value = s.values.getOrElse(i) { 0.0 }
                val length = (kotlin.math.abs(value) / (scale.max - scale.min) * plot.height).toFloat() * grow.value
                if (length < 0.5f) return@forEach
                if (value > 0) {
                    roundedBar(s.color.copy(alpha = alpha), left, up - length + gap, barWidth, length - gap)
                    up -= length
                } else {
                    roundedBar(s.color.copy(alpha = alpha), left, down + gap, barWidth, length - gap)
                    down += length
                }
            }
        }
        if (average != null && average > 0) drawAverageLine(plot, scale, average, colors.text)
        drawCategoryLabels(plot, labels, colors, measurer, selected)
    }
}

/** Bars side by side per label (a month's income, spending, left over and savings change); values can be negative. */
@Composable
fun GroupedBarChart(
    labels: List<String>,
    series: List<ChartSeries>,
    description: String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    height: Dp = 220.dp,
) {
    val colors = LocalChartColors.current
    val measurer = rememberTextMeasurer()
    val values = series.flatMap { it.values }
    val scale = ValueScale.of(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0)
    val grow = growIn(series.map { it.values })
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .barSemantics(description, labels, selected, onSelect)
            .pointerInput(labels, selected) {
                detectTapGestures { offset ->
                    val plot = Rect(AxisWidth.toPx(), 0f, size.width.toFloat(), size.height.toFloat())
                    val index = slotAt(offset.x, plot, labels.size)
                    onSelect(if (index == selected) null else index)
                }
            },
    ) {
        val plot = plotArea(measurer, colors)
        drawValueAxis(plot, scale, colors, measurer)
        val slot = plot.width / labels.size.coerceAtLeast(1)
        val groupWidth = slot * 0.8f
        val barWidth = min(groupWidth / series.size.coerceAtLeast(1) - 2.dp.toPx(), 14.dp.toPx())
        val used = (barWidth + 2.dp.toPx()) * series.size
        val zero = plot.bottom - scale.fraction(0.0) * plot.height
        labels.indices.forEach { i ->
            val alpha = if (selected == null || selected == i) 1f else DIMMED
            var left = plot.left + slot * i + (slot - used) / 2
            series.forEach { s ->
                val value = s.values.getOrElse(i) { 0.0 }
                val y = plot.bottom - scale.fraction(value * grow.value) * plot.height
                val top = min(y, zero)
                val length = kotlin.math.abs(zero - y)
                if (length >= 0.5f) roundedBar(s.color.copy(alpha = alpha), left, top, barWidth, length)
                left += barWidth + 2.dp.toPx()
            }
        }
        drawCategoryLabels(plot, labels, colors, measurer, selected)
    }
}

/**
 * One bar per period (a breakdown's spending or income), with the average dashed across, and below it the running
 * total as a line: two charts sharing the left axis width, so their periods line up.
 */
@Composable
fun PeriodCharts(
    labels: List<String>,
    values: List<Double>,
    cumulative: List<Double>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    average: Double? = null,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val colors = LocalChartColors.current
    val measurer = rememberTextMeasurer()
    StackedBarChart(
        labels = labels,
        series = listOf(ChartSeries("", color, values)),
        description = description,
        modifier = modifier,
        average = average,
        selected = selected,
        onSelect = onSelect,
        height = 180.dp,
    )
    val lineScale = ValueScale.of(cumulative.minOrNull() ?: 0.0, cumulative.maxOrNull() ?: 0.0, targetTicks = 3)
    val draw = growIn(listOf(cumulative))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "Running total" },
    ) {
        val plot = plotArea(measurer, colors, withXLabels = false)
        drawValueAxis(plot, lineScale, colors, measurer)
        if (cumulative.isEmpty()) return@Canvas
        val slot = plot.width / cumulative.size
        val points = cumulative.mapIndexed { i, value ->
            Offset(plot.left + slot * (i + 0.5f), plot.bottom - lineScale.fraction(value) * plot.height)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        // Drawn from left to right as it appears.
        clipRect(right = plot.left + (plot.width + 8.dp.toPx()) * draw.value) {
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        points.forEachIndexed { i, point ->
            if (point.x > plot.left + plot.width * draw.value + 1f) return@forEachIndexed
            val radius = if (i == selected) 4.5.dp.toPx() else 2.5.dp.toPx()
            drawCircle(color, radius, point)
        }
    }
}

/** 0 to 1 as new data arrives: the bars grow from the axis instead of appearing at once. */
@Composable
private fun growIn(data: Any): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    return progress
}

/** A bar with rounded corners, a little rounder at the ends that show. */
private fun DrawScope.roundedBar(color: Color, left: Float, top: Float, width: Float, height: Float) {
    if (height <= 0f || width <= 0f) return
    val radius = min(min(width / 2, height / 2), 4.dp.toPx())
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(radius, radius))
}

/**
 * The chart's summary, and a TalkBack action per bar ("Show Sep 2026"): picking a bar is otherwise a tap at a spot on
 * the canvas, and what the picked one shows (its categories, its figures) is out of reach without one.
 */
private fun Modifier.barSemantics(
    description: String,
    labels: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
): Modifier = semantics {
    contentDescription = description
    selected?.let { labels.getOrNull(it) }?.let { stateDescription = "$it shown" }
    customActions = labels.mapIndexed { i, label ->
        CustomAccessibilityAction(if (i == selected) "Stop showing $label" else "Show $label") {
            onSelect(if (i == selected) null else i)
            true
        }
    }
}
