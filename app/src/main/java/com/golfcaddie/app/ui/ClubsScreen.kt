package com.golfcaddie.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.golfcaddie.core.ClubStats
import com.golfcaddie.core.ClubType
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.StatSource
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ClubsScreen(state: UiState, vm: CaddieViewModel) {
    var expanded by remember { mutableStateOf<String?>(null) }
    val u = state.unit
    fun d(m: Double) = formatDistance(m, u)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clubs = state.bag.clubs.filter { it.type != ClubType.PUTTER }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), state = listState) {
        item {
            GappingChart(
                stats = clubs.filter { it.inBag }.map { ClubStats.of(it, state.bag.shots) },
                unit = u,
                onClubTap = { id ->
                    // Tapping a bar opens that club's card (the numbers behind the chart).
                    expanded = id
                    scope.launch { listState.animateScrollToItem(2 + clubs.indexOfFirst { it.id == id }) }
                },
            )
        }
        item {
            Text(
                "Your distances. The caddie plans around your typical distance: the average of your " +
                    "recent shots (mishits ignored) once you have ${ClubStats.MIN_SHOTS}, otherwise " +
                    "${(ClubStats.TYPICAL_FRACTION_OF_MAX * 100).toInt()}% of your max.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }
        items(clubs, key = { it.id }) { club ->
            val stats = ClubStats.of(club, state.bag.shots)
            val open = expanded == club.id
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = if (open) null else club.id }) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = club.inBag, onCheckedChange = { vm.setInBag(club.id, it) })
                        Column(Modifier.weight(1f)) {
                            Text(club.name, fontWeight = FontWeight.Bold)
                            val src = when (stats.source) {
                                StatSource.HISTORY -> "${stats.shotCount} shots"
                                StatSource.MAX_ONLY -> "from max"
                                StatSource.DEFAULT -> "default, no data yet"
                            }
                            Text("Typical ${d(stats.typicalMeters)} · Max ${stats.maxMeters?.let(::d) ?: "–"} · $src",
                                style = MaterialTheme.typography.bodySmall)
                            val range = CaddieText.range(stats, u) + if (stats.rangeFromShots) "" else " (estimate)"
                            val side = CaddieText.side(stats, u)?.let { " · $it" } ?: ""
                            Text("Usually $range$side", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (open) ClubEditor(state, vm, club.id, ::d)
                }
            }
        }
    }
}

@Composable
private fun ClubEditor(state: UiState, vm: CaddieViewModel, clubId: String, d: (Double) -> String) {
    val u = state.unit
    val club = state.bag.clubs.first { it.id == clubId }
    var maxText by remember(clubId, u) {
        mutableStateOf(club.manualMaxMeters?.let { Math.round(u.fromMeters(it)).toString() } ?: "")
    }
    var shotText by remember { mutableStateOf("") }
    val maxValue = parseDistance(maxText, u)
    val shotValue = parseDistance(shotText, u)
    val maxInvalid = maxText.isNotBlank() && maxValue == null
    val shotInvalid = shotText.isNotBlank() && shotValue == null

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = maxText, onValueChange = { maxText = it },
                label = { Text("My max (${u.label})") }, singleLine = true,
                isError = maxInvalid,
                supportingText = if (maxInvalid) ({ Text(rangeHint(u)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            // Empty clears your max; anything invalid is left for you to correct.
            OutlinedButton(
                onClick = { vm.setManualMax(clubId, maxValue) },
                enabled = !maxInvalid,
            ) {
                Text("Save")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = shotText, onValueChange = { shotText = it },
                label = { Text("Add a shot (${u.label})") }, singleLine = true,
                isError = shotInvalid,
                supportingText = if (shotInvalid) ({ Text(rangeHint(u)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    shotValue?.let { vm.addShot(clubId, it) }
                    shotText = ""
                },
                enabled = shotValue != null,
            ) { Text("Add") }
        }
        val shots = state.bag.shots.filter { it.clubId == clubId }.sortedByDescending { it.timestampMillis }
        if (shots.isEmpty()) {
            Text("No shots yet. Use \"Record a shot\" on the course, or add them here.",
                style = MaterialTheme.typography.bodySmall)
        }
        val fmt = DateFormat.getDateInstance(DateFormat.SHORT)
        shots.take(30).forEach { shot ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d(shot.distanceMeters)}  ·  ${fmt.format(Date(shot.timestampMillis))}" +
                        (if (shot.start != null) "  ·  GPS" else "") +
                        (shot.sideMeters?.let { side ->
                            val n = Math.round(u.fromMeters(kotlin.math.abs(side)))
                            if (n < 1) "  ·  on line" else "  ·  $n ${u.label} ${if (side > 0) "right" else "left"}"
                        } ?: ""),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { vm.deleteShot(shot.id) }) { Text("Delete") }
            }
        }
    }
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
