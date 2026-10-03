package com.guibilar.financeinsights.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.ui.theme.ChartColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** One series of a chart: a name for the legend and the selection panel, its colour, a value per label. */
data class ChartSeries(val name: String, val color: Color, val values: List<Double>)

/** A value axis with round steps ("$0, $500, $1K, …") that covers [min]..[max] and includes zero. */
data class ValueScale(val min: Double, val max: Double, val ticks: List<Double>) {
    fun fraction(value: Double) = if (max == min) 0f else ((value - min) / (max - min)).toFloat()

    companion object {
        fun of(minValue: Double, maxValue: Double, targetTicks: Int = 4): ValueScale {
            val lo = minOf(0.0, minValue)
            val hi = maxOf(0.0, maxValue).let { if (it == lo) lo + 100 else it }
            val step = niceStep((hi - lo) / targetTicks)
            val min = floor(lo / step) * step
            val max = ceil(hi / step) * step
            val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
            return ValueScale(min, max, ticks)
        }

        private fun niceStep(raw: Double): Double {
            val magnitude = 10.0.pow(floor(log10(max(raw, 1e-9))))
            val fraction = raw / magnitude
            val nice = when {
                fraction <= 1 -> 1.0
                fraction <= 2 -> 2.0
                fraction <= 2.5 -> 2.5
                fraction <= 5 -> 5.0
                else -> 10.0
            }
            return nice * magnitude
        }
    }
}

/** The left axis's width, shared by charts drawn one above the other so their plots line up. */
val AxisWidth = 44.dp
private val LabelHeight = 20.dp

/** Where the bars go, inside the axis labels. */
fun DrawScope.plotArea(withXLabels: Boolean = true): Rect =
    Rect(AxisWidth.toPx(), 6.dp.toPx(), size.width, size.height - if (withXLabels) LabelHeight.toPx() else 4.dp.toPx())

fun axisTextStyle(colors: ChartColors) = TextStyle(color = colors.muted, fontSize = 11.sp)

/** Hairline gridlines with compact labels, and a stronger line at zero. */
fun DrawScope.drawValueAxis(plot: Rect, scale: ValueScale, colors: ChartColors, measurer: TextMeasurer) {
    val style = axisTextStyle(colors)
    for (tick in scale.ticks) {
        val y = plot.bottom - scale.fraction(tick) * plot.height
        drawLine(
            if (abs(tick) < 1e-9) colors.axis else colors.grid,
            Offset(plot.left, y),
            Offset(plot.right, y),
            strokeWidth = 1.dp.toPx(),
        )
        val label = measurer.measure(Money.compact(tick), style)
        drawText(
            label,
            topLeft = Offset(plot.left - label.size.width - 8.dp.toPx(), y - label.size.height / 2f),
        )
    }
}

/** Labels under the slots, skipping some when they would crowd each other. */
fun DrawScope.drawCategoryLabels(plot: Rect, labels: List<String>, colors: ChartColors, measurer: TextMeasurer, selected: Int?) {
    if (labels.isEmpty()) return
    val slot = plot.width / labels.size
    val style = axisTextStyle(colors)
    val widest = labels.maxOf { measurer.measure(it, style).size.width }
    val every = max(1, ceil((widest + 8.dp.toPx()) / slot).toInt())
    labels.forEachIndexed { index, text ->
        if (index % every != 0 && index != selected) return@forEachIndexed
        val label = measurer.measure(text, if (index == selected) style.copy(color = colors.text) else style)
        drawText(
            label,
            topLeft = Offset(plot.left + slot * (index + 0.5f) - label.size.width / 2f, plot.bottom + 4.dp.toPx()),
        )
    }
}

/** A dashed line across the plot, for an average. */
fun DrawScope.drawAverageLine(plot: Rect, scale: ValueScale, value: Double, color: Color) {
    val y = plot.bottom - scale.fraction(value) * plot.height
    drawLine(
        color,
        Offset(plot.left, y),
        Offset(plot.right, y),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
}

/** Which slot an x position falls in, or null outside the plot. */
fun slotAt(x: Float, plot: Rect, count: Int): Int? {
    if (count == 0 || x < plot.left || x > plot.right) return null
    return ((x - plot.left) / (plot.width / count)).toInt().coerceIn(0, count - 1)
}
