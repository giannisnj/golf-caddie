package com.golfcaddie.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.golfcaddie.core.ClubStats
import com.golfcaddie.core.DistanceUnit
import kotlin.math.ceil
import kotlin.math.floor

private val LABEL_WIDTH = 92.dp
private val ROW_HEIGHT = 22.dp
private val BAR_HEIGHT = 8.dp

/**
 * Gapping chart: one row per club, a bar over its usual range and a dot at its typical
 * distance, so gaps and overlaps in the bag stand out. Filled bars come from your shots;
 * outlined bars are estimates until a club has enough shots (shape, not only shade, tells
 * them apart). The club cards below are the table view.
 */
@Composable
fun GappingChart(stats: List<ClubStats>, unit: DistanceUnit, onClubTap: (String) -> Unit) {
    if (stats.isEmpty()) return
    val ink = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)

    // Axis in the display unit, on round 50s.
    val lo = floor(stats.minOf { unit.fromMeters(it.lowMeters) } / 50) * 50
    val hi = ceil(stats.maxOf { unit.fromMeters(it.highMeters) } / 50) * 50
    val ticks = generateSequence(lo) { it + 50 }.takeWhile { it <= hi }.toList()
    fun x(widthPx: Float, meters: Double) = ((unit.fromMeters(meters) - lo) / (hi - lo) * widthPx).toFloat()

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("Gapping: your usual range with each club", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendSwatch(filled = true, ink = ink)
            Text("From your shots", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            LegendSwatch(filled = false, ink = ink)
            Text("Estimate (fewer than 5 shots)", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        stats.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .clickable { onClubTap(s.club.id) }
                    .semantics {
                        contentDescription = "${s.club.name}: usually ${CaddieText.range(s, unit)}, " +
                            "typical ${formatDistance(s.typicalMeters, unit)}"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.club.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(LABEL_WIDTH), maxLines = 1)
                Canvas(Modifier.weight(1f).height(ROW_HEIGHT)) {
                    gridlines(ticks.map { ((it - lo) / (hi - lo) * size.width).toFloat() }, grid)
                    val y = size.height / 2
                    val bar = BAR_HEIGHT.toPx()
                    val left = x(size.width, s.lowMeters)
                    val right = x(size.width, s.highMeters)
                    val r = CornerRadius(bar / 2)
                    if (s.rangeFromShots) {
                        drawRoundRect(ink, Offset(left, y - bar / 2), Size(right - left, bar), r)
                    } else {
                        drawRoundRect(ink.copy(alpha = 0.12f), Offset(left, y - bar / 2), Size(right - left, bar), r)
                        drawRoundRect(ink, Offset(left, y - bar / 2), Size(right - left, bar), r, style = Stroke(1.5.dp.toPx()))
                    }
                    // Typical distance: a dot with a surface-coloured ring so it reads on top of the bar.
                    val cx = x(size.width, s.typicalMeters)
                    drawCircle(surface, radius = 6.dp.toPx(), center = Offset(cx, y))
                    drawCircle(ink, radius = 4.dp.toPx(), center = Offset(cx, y))
                }
            }
        }
        // Axis labels under the plot, aligned with the gridlines.
        val measurer = rememberTextMeasurer()
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(LABEL_WIDTH))
            Canvas(Modifier.weight(1f).height(16.dp)) {
                ticks.forEachIndexed { i, t ->
                    val label = if (i == ticks.lastIndex) "${t.toInt()} ${unit.label}" else "${t.toInt()}"
                    val layout = measurer.measure(label, labelStyle)
                    val px = ((t - lo) / (hi - lo) * size.width).toFloat()
                    val left = (px - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(left, 0f))
                }
            }
        }
    }
}

private fun DrawScope.gridlines(xs: List<Float>, color: Color) {
    xs.forEach { drawLine(color, Offset(it, 0f), Offset(it, size.height), strokeWidth = 1.dp.toPx()) }
}

@Composable
private fun LegendSwatch(filled: Boolean, ink: Color) {
    Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
        val r = CornerRadius(size.height / 2)
        if (filled) {
            drawRoundRect(ink, cornerRadius = r)
        } else {
            drawRoundRect(ink.copy(alpha = 0.12f), cornerRadius = r)
            drawRoundRect(ink, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        }
    }
}
