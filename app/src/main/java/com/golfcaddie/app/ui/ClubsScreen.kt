package com.golfcaddie.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golfcaddie.app.ui.theme.GolfIcons
import com.golfcaddie.core.ClubStats
import com.golfcaddie.core.ClubType
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.StatSource
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun ClubsScreen(state: UiState, vm: CaddieViewModel) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val id = open
    if (id != null && state.bag.clubs.any { it.id == id }) {
        ClubDetail(state, vm, id, onBack = { open = null })
    } else {
        ClubsList(state, onOpen = { open = it })
    }
}

/** Where a club's numbers come from, in the row's small print. */
private fun sourceLabel(s: ClubStats) = when (s.source) {
    StatSource.HISTORY -> "${s.shotCount} shot" + if (s.shotCount == 1) "" else "s"
    StatSource.MAX_ONLY -> "from max"
    StatSource.DEFAULT -> "no data yet"
}

// ---- The gapping chart ------------------------------------------------------------------------------

/** Every club in the bag: your usual range as a bar, your typical distance as a dot. */
@Composable
private fun ClubsList(state: UiState, onOpen: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val u = state.unit
    val clubs = state.bag.clubs.filter { it.type != ClubType.PUTTER }
    val stats = clubs.filter { it.inBag }.map { ClubStats.of(it, state.bag.shots) }
    val noData = stats.all { it.source == StatSource.DEFAULT }
    Column(Modifier.fillMaxSize().background(c.background)) {
        PageHeader("Clubs", if (noData) "Typical-golfer distances for now" else "${stats.size} clubs in your bag")
        val scale = remember(stats, u) { ChartScale.of(stats, u) }
        LazyColumn(Modifier.fillMaxSize().background(c.surface)) {
            if (noData) {
                item {
                    Column(
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).fillMaxWidth()
                            .background(c.primaryContainer, MaterialTheme.shapes.large).padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Your caddie starts with typical distances", style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer)
                        Text(
                            "Record shots on the course, or type a few into each club, and the bars switch to your own numbers.",
                            style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer,
                        )
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GolfIcon(GolfIcons.Info, 20.dp, c.onSurfaceVariant)
                    Text(
                        "The caddie plans around your typical distance: the average of recent shots, with mishits ignored.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp), color = c.onSurfaceVariant,
                    )
                }
            }
            item { Legend() }
            item { Axis(scale, u) }
            items(stats, key = { it.club.id }) { s -> GappingRow(s, scale, u, onClick = { onOpen(s.club.id) }) }
            val out = clubs.filterNot { it.inBag }
            items(out, key = { "out-" + it.id }) { club ->
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clickable { onOpen(club.id) }.padding(start = 20.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Overline("Not in your bag")
                        Text(club.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp))
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { GolfIcon(GolfIcons.ChevronRight, 22.dp, c.onSurfaceVariant) }
                }
            }
        }
    }
}

/** Maps distances to the chart's width, with gridlines every 50 yards (or metres). */
private class ChartScale(val start: Double, val end: Double, val ticks: List<Int>) {
    fun fraction(v: Double) = ((v - start) / (end - start)).coerceIn(0.0, 1.0).toFloat()

    companion object {
        fun of(stats: List<ClubStats>, u: DistanceUnit): ChartScale {
            if (stats.isEmpty()) return ChartScale(50.0, 250.0, listOf(50, 100, 150, 200, 250))
            val lo = stats.minOf { u.fromMeters(it.lowMeters) }
            val hi = stats.maxOf { u.fromMeters(it.highMeters) }
            val start = floor(lo / 50) * 50
            val end = (ceil(hi / 50) * 50).coerceAtLeast(start + 100)
            return ChartScale(start, end, (start.toInt()..end.toInt() step 50).toList())
        }
    }
}

/** Row geometry, shared by the axis and the rows so the gridlines line up. */
private val NAME_WIDTH = 114.dp
private val VALUE_WIDTH = 44.dp

@Composable
private fun Legend() {
    val c = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(20.dp, 10.dp).background(c.primary, RoundedCornerShape(5.dp)))
            Text("5+ of your shots", style = style, color = c.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(20.dp, 10.dp).border(2.dp, c.primary, RoundedCornerShape(5.dp)))
            Text("Estimate", style = style, color = c.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(12.dp).background(c.onSurface, RoundedCornerShape(6.dp)))
            Text("Typical", style = style, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun Axis(scale: ChartScale, u: DistanceUnit) {
    val c = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
    Column {
        Row(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.width(NAME_WIDTH))
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                scale.ticks.forEach { t ->
                    Text(
                        "$t", style = style, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.width(40.dp).offset(x = maxWidth * scale.fraction(t.toDouble()) - 20.dp, y = 2.dp),
                    )
                }
            }
            Text(u.label, style = style, color = c.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(VALUE_WIDTH).padding(top = 2.dp))
        }
        HorizontalDivider(color = c.outlineVariant)
    }
}

/**
 * Bar 12 dp, radius 6: solid needs 5 or more of your shots; hollow is an estimate; dashed is no
 * data yet. Dot 14 dp with a 3 dp ring. The whole row taps through to the club.
 */
@Composable
private fun GappingRow(s: ClubStats, scale: ChartScale, unit: DistanceUnit, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val grid = c.outlineVariant.copy(alpha = 0.5f)
    val estimate = !s.rangeFromShots
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${s.club.name}: typical ${formatDistance(s.typicalMeters, unit)}, " +
                        "usually ${CaddieText.range(s, unit)}" + (if (estimate) " (estimate)" else "") + ", ${sourceLabel(s)}"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.width(NAME_WIDTH), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(s.club.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 19.sp), maxLines = 1, softWrap = false)
                Text(sourceLabel(s), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Medium), color = c.onSurfaceVariant)
            }
            val primary = c.primary
            val dot = c.onSurface
            val ring = c.surface
            Box(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    scale.ticks.forEach { t ->
                        val x = size.width * scale.fraction(t.toDouble())
                        drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                    val x0 = size.width * scale.fraction(unit.fromMeters(s.lowMeters))
                    val x1 = size.width * scale.fraction(unit.fromMeters(s.highMeters))
                    val h = 12.dp.toPx()
                    val top = (size.height - h) / 2
                    val r = CornerRadius(h / 2)
                    val w = (x1 - x0).coerceAtLeast(h)
                    when {
                        s.rangeFromShots -> drawRoundRect(primary, Offset(x0, top), Size(w, h), r)
                        s.source == StatSource.DEFAULT -> drawRoundRect(
                            primary, Offset(x0 + 1.dp.toPx(), top + 1.dp.toPx()), Size(w - 2.dp.toPx(), h - 2.dp.toPx()), r,
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                        else -> drawRoundRect(
                            primary, Offset(x0 + 1.dp.toPx(), top + 1.dp.toPx()), Size(w - 2.dp.toPx(), h - 2.dp.toPx()), r,
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                    val cx = size.width * scale.fraction(unit.fromMeters(s.typicalMeters))
                    drawCircle(ring, 10.dp.toPx(), Offset(cx, size.height / 2))
                    drawCircle(dot, 7.dp.toPx(), Offset(cx, size.height / 2))
                },
            )
            Text(
                "${unit.fromMeters(s.typicalMeters).roundToInt()}",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 26.sp),
                textAlign = TextAlign.End, modifier = Modifier.width(VALUE_WIDTH),
            )
        }
        HorizontalDivider(color = c.outlineVariant)
    }
}

// ---- One club --------------------------------------------------------------------------------------------

@Composable
private fun ClubDetail(state: UiState, vm: CaddieViewModel, clubId: String, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val u = state.unit
    val club = state.bag.clubs.first { it.id == clubId }
    val stats = ClubStats.of(club, state.bag.shots)
    val shots = state.bag.shots.filter { it.clubId == clubId }.sortedByDescending { it.timestampMillis }
    fun n(m: Double) = u.fromMeters(m).roundToInt()
    fun count(k: Int) = "$k shot" + if (k == 1) "" else "s"

    Column(Modifier.fillMaxSize().background(c.background)) {
        PageHeader(club.name, if (shots.isEmpty()) "No shots yet" else count(shots.size), leading = {
            IconButton(onClick = onBack, Modifier.size(48.dp)) {
                GolfIcon(GolfIcons.ArrowLeft, 26.dp, androidx.compose.material3.LocalContentColor.current, contentDescription = "Back to Clubs")
            }
        })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary: typical big, then range, max and which way it tends.
            SectionCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp), spacing = 14.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Overline("Typical")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${n(stats.typicalMeters)}", style = MaterialTheme.typography.displayMedium, modifier = Modifier.alignByBaseline().lineBox(58.dp))
                            Text(u.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold), color = c.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                        }
                    }
                    Tag(
                        when (stats.source) {
                            StatSource.HISTORY -> "From ${count(stats.shotCount)}"
                            StatSource.MAX_ONLY -> "From your max"
                            StatSource.DEFAULT -> "Typical golfer"
                        },
                        c.primaryContainer, c.onPrimaryContainer,
                    )
                }
                HorizontalDivider(color = c.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val value = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, lineHeight = 26.sp)
                    Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Overline(if (stats.rangeFromShots) "Usual range" else "Range (est.)")
                        Text("${n(stats.lowMeters)}–${n(stats.highMeters)}", style = value)
                    }
                    Column(Modifier.weight(0.65f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Overline("Max")
                        Text(stats.maxMeters?.let { "${n(it)}" } ?: "—", style = value)
                    }
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Overline("Tends")
                        val side = stats.sideMeters
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val amount = side?.let { abs(n(it)) }
                            when {
                                side == null || amount == null -> Text("—", style = value)
                                amount < 3 -> Text("Straight", style = value)
                                else -> {
                                    GolfIcon(if (side > 0) GolfIcons.ArrowRight else GolfIcons.ArrowLeft, 18.dp, c.onSurface)
                                    Text("$amount ${u.label} ${if (side > 0) "right" else "left"}", style = value)
                                }
                            }
                        }
                    }
                }
            }

            // In the bag, and your max.
            SectionCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp), spacing = 0.dp) {
                Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("In your bag", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Switch(
                        checked = club.inBag, onCheckedChange = { vm.setInBag(club.id, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
                    )
                }
                HorizontalDivider(color = c.outlineVariant)
                var maxText by remember(clubId, u) {
                    mutableStateOf(club.manualMaxMeters?.let { Math.round(u.fromMeters(it)).toString() } ?: "")
                }
                val maxValue = parseDistance(maxText, u)
                val maxInvalid = maxText.isNotBlank() && maxValue == null
                // Saved as you type, once it's a plausible distance (or cleared).
                LaunchedEffect(maxText) {
                    delay(600)
                    if (maxText.isBlank()) vm.setManualMax(clubId, null) else if (maxValue != null) vm.setManualMax(clubId, maxValue)
                }
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DistanceField(maxText, { maxText = it }, "My max", u, maxInvalid, Modifier.fillMaxWidth())
                    Text(
                        if (maxInvalid) rangeHint(u) else "Helps the caddie while you have few shots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (maxInvalid) c.error else c.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // Add a shot by hand.
            SectionCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp), spacing = 12.dp) {
                Text("Add a shot", style = MaterialTheme.typography.titleMedium)
                var shotText by remember { mutableStateOf("") }
                val shotValue = parseDistance(shotText, u)
                val shotInvalid = shotText.isNotBlank() && shotValue == null
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DistanceField(shotText, { shotText = it }, "Distance", u, shotInvalid, Modifier.weight(1f))
                    PillButton(
                        "Add", onClick = { shotValue?.let { vm.addShot(clubId, it) }; shotText = "" },
                        icon = GolfIcons.Add, enabled = shotValue != null,
                    )
                }
                if (shotInvalid) Text(rangeHint(u), style = MaterialTheme.typography.bodySmall, color = c.error)
            }

            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Overline("Shot history", Modifier.weight(1f))
                Text(count(shots.size), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp), color = c.onSurfaceVariant)
            }
            SectionCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 4.dp), spacing = 0.dp) {
                if (shots.isEmpty()) {
                    Text(
                        "No shots yet. Use Record a shot on the course, or add them here.",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp, end = 16.dp),
                    )
                }
                val date = SimpleDateFormat("d MMM", Locale.getDefault())
                shots.take(30).forEachIndexed { i, shot ->
                    if (i > 0) HorizontalDivider(color = c.outlineVariant)
                    val meta = listOfNotNull(
                        date.format(Date(shot.timestampMillis)),
                        if (shot.start != null) "walked with GPS" else "typed in",
                        shot.sideMeters?.let { side ->
                            val k = abs(n(side))
                            if (k < 1) "on line" else "$k ${u.label} ${if (side > 0) "right" else "left"}"
                        },
                    ).joinToString(" · ")
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(formatDistance(shot.distanceMeters, u), style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deleteShot(shot.id) }, Modifier.size(48.dp)) {
                            GolfIcon(GolfIcons.Delete, 22.dp, c.onSurfaceVariant, contentDescription = "Delete shot: ${formatDistance(shot.distanceMeters, u)}, $meta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistanceField(value: String, onChange: (String) -> Unit, label: String, u: DistanceUnit, error: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error,
        suffix = { Text(u.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.onSurfaceVariant) },
        textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.outline, focusedBorderColor = c.primary),
        modifier = modifier,
    )
}

private const val MAX_SHOT_METERS = 450.0

private fun rangeHint(unit: DistanceUnit) =
    "Between ${Math.round(unit.fromMeters(ClubStats.MIN_SHOT_METERS))} and " +
        "${Math.round(unit.fromMeters(MAX_SHOT_METERS))} ${unit.label}"

/**
 * A typed distance in meters, or null if it isn't a plausible full shot. Accepts a decimal
 * comma ("150,5") as typed on many European keyboards.
 */
private fun parseDistance(text: String, unit: DistanceUnit): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()
        ?.let(unit::toMeters)
        ?.takeIf { it >= ClubStats.MIN_SHOT_METERS && it <= MAX_SHOT_METERS }
