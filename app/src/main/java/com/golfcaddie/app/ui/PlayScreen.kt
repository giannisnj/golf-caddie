package com.golfcaddie.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golfcaddie.core.ClubType
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.Geo
import com.golfcaddie.core.HazardType
import com.golfcaddie.core.Recommendation
import com.golfcaddie.core.StatSource

@Composable
fun PlayScreen(state: UiState, vm: CaddieViewModel) {
    var showCourses by remember { mutableStateOf(false) }
    val u = state.unit
    fun d(m: Double) = formatDistance(m, u)

    Column(Modifier.fillMaxSize()) {
        // Course + hole header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showCourses = true }, modifier = Modifier.weight(1f)) {
                Text(state.course?.name ?: "Choose a course", maxLines = 1)
            }
            if (state.course != null) {
                TextButton(onClick = { vm.shiftHole(-1) }) { Text("◀") }
                val hole = state.situation?.hole
                val par = hole?.par?.let { " · Par $it" } ?: ""
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hole ${hole?.number ?: "-"}$par", fontWeight = FontWeight.Bold)
                    hole?.name?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
                TextButton(onClick = { vm.shiftHole(1) }) { Text("▶") }
                if (state.holeLocked) TextButton(onClick = vm::setAutoHole) { Text("Auto") }
            }
        }

        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(state.simulate, { vm.setSimulate(!state.simulate) }, label = { Text("Simulate GPS") })
            FilterChip(state.satellite, { vm.setSatellite(!state.satellite) }, label = { Text("Satellite") })
            FilterChip(u == DistanceUnit.YARDS, {
                vm.setUnit(if (u == DistanceUnit.YARDS) DistanceUnit.METERS else DistanceUnit.YARDS)
            }, label = { Text(u.label) })
        }

        CourseMap(
            course = state.course,
            hole = state.situation?.hole,
            player = state.position,
            satellite = state.satellite,
            onTap = vm::onMapTap,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
        )

        if (state.simulate) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tap map to move.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = vm::jumpToTee) { Text("Tee") }
                OutlinedButton(onClick = { vm.stepTowardGreen(u.toMeters(25.0)) }) { Text("+25 ${u.label}") }
            }
        }

        InfoPanel(state, vm, ::d)
    }

    state.message?.let {
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
            text = { Text(it) },
        )
    }
    if (showCourses) CourseDialog(state, vm) { showCourses = false }
}

@Composable
private fun InfoPanel(state: UiState, vm: CaddieViewModel, d: (Double) -> String) {
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            val s = state.situation
            when {
                state.course == null -> Text("Load a course: tap \"Choose a course\" above.")
                state.position == null -> Text(
                    if (state.simulate) "Tap the map to place yourself." else "Waiting for GPS…",
                )
                s == null -> Text("No holes found near you.")
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(d(s.green.center), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.green.pin != null) "to green center" else "to the middle of the green")
                    }
                    Text("Front ${d(s.green.front)}  ·  Back ${d(s.green.back)}" +
                        (s.green.pin?.let { "  ·  Pin ${d(it)}" } ?: ""))
                    s.hazards.take(3).forEach { hz ->
                        val name = when (hz.type) {
                            HazardType.BUNKER -> "Bunker"
                            HazardType.WATER -> "Water"
                            HazardType.LATERAL_WATER -> "Lateral water"
                        }
                        Text("$name: reach ${d(hz.reachMeters)}, carry ${d(hz.carryMeters)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (s.offLineMeters > 60) {
                        Text("You're ${d(s.offLineMeters)} off this hole's line. Wrong hole? Use ◀ ▶.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    state.recommendation?.let { RecommendationView(it, d) }
                }
            }
            state.accuracyMeters?.let {
                Text("GPS accuracy ±${d(it.toDouble())}", style = MaterialTheme.typography.labelSmall)
            }
            ShotTracker(state, vm, d)
        }
    }
}

@Composable
private fun RecommendationView(rec: Recommendation, d: (Double) -> String) {
    val pick = rec.club ?: return
    Spacer(Modifier.padding(top = 6.dp))
    val headline = when (rec.kind) {
        Recommendation.Kind.PUTT -> "On the green: Putter"
        Recommendation.Kind.PARTIAL_SWING -> "${pick.club.name}: partial swing"
        Recommendation.Kind.CANNOT_REACH -> "${pick.club.name} (leaves ${d(rec.leftAfterMeters ?: 0.0)})"
        Recommendation.Kind.FULL_SWING -> pick.club.name
    }
    Text(headline, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    if (rec.kind != Recommendation.Kind.PUTT) {
        val basis = when (pick.source) {
            StatSource.HISTORY -> "you average ${d(pick.typicalMeters)} over ${pick.shotCount} shots"
            StatSource.MAX_ONLY -> "~${d(pick.typicalMeters)}, from your max of ${d(pick.maxMeters ?: 0.0)}"
            StatSource.DEFAULT -> "~${d(pick.typicalMeters)} (typical golfer; add your own data in Clubs)"
        }
        Text(basis, style = MaterialTheme.typography.bodySmall)
        rec.shorterOption?.let {
            Text("Or ${it.club.name} if you catch it well (max ${d(it.maxMeters ?: 0.0)})",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShotTracker(state: UiState, vm: CaddieViewModel, d: (Double) -> String) {
    var picking by remember { mutableStateOf(false) }
    val pending = state.pendingShot
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (pending == null) {
            OutlinedButton(onClick = { picking = true }, enabled = state.position != null) { Text("Record a shot") }
        } else {
            val so = state.position?.let { Geo.distance(pending.start, it) } ?: 0.0
            Text("${pending.club.name}: ${d(so)} so far", modifier = Modifier.weight(1f))
            Button(onClick = vm::finishShot) { Text("Ball is here") }
            TextButton(onClick = vm::cancelShot) { Text("Cancel") }
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            title = { Text("Which club are you hitting?") },
            text = {
                LazyColumn {
                    items(state.bag.clubs.filter { it.inBag && it.type != ClubType.PUTTER }) { club ->
                        TextButton(onClick = { vm.startShot(club); picking = false }) {
                            val star = if (club.id == state.recommendation?.club?.club?.id) " ★" else ""
                            Text(club.name + star)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun CourseDialog(state: UiState, vm: CaddieViewModel, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Golf course") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::findCoursesNearMe, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                    Text("Courses near me")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Course name or town") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchCourses(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { vm.searchCourses(query) }, enabled = !state.loading) { Text("Search") }
                if (state.loading) CircularProgressIndicator()
                LazyColumn {
                    items(state.courses, key = { it.id }) { c ->
                        TextButton(onClick = { vm.selectCourse(c); onDismiss() }) {
                            Text("${c.name} (${c.holes.size} holes)")
                        }
                    }
                }
                Text("Course maps © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}
