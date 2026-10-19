package com.golfcaddie.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Finding a course, plus the settings that belong to it: live weather and the offline map. */
@Composable
fun CourseScreen(state: UiState, vm: CaddieViewModel, onCoursePicked: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.course?.let { c ->
            Text(c.name, style = MaterialTheme.typography.titleLarge)
            val heights = when {
                c.elevation != null -> "slopes included"
                state.fetchingElevation -> "fetching slopes…"
                else -> "no slope data yet (needs signal once)"
            }
            Text("${c.holes.size} holes · $heights", style = MaterialTheme.typography.bodySmall)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live wind & temperature")
                Text("For \"plays like\" distances. Needs a data connection.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.liveWeather, onCheckedChange = vm::setLiveWeather)
        }
        if (state.course != null) OfflineMapSection(state, vm)

        HorizontalDivider()
        Text("Find a course", fontWeight = FontWeight.Bold)
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
        state.courses.forEach { c ->
            TextButton(onClick = { vm.selectCourse(c); onCoursePicked() }) {
                Text("${c.name} (${c.holes.size} holes)" + if (c.id == state.course?.id) " ✓" else "")
            }
        }
        Text("Course maps © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OfflineMapSection(state: UiState, vm: CaddieViewModel) {
    val status = state.offlineMap
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Offline map", fontWeight = FontWeight.Bold)
        Text(
            "Distances, slopes and advice always work offline. The Course plan map style needs no " +
                "signal either; satellite images do, unless saved here.",
            style = MaterialTheme.typography.bodySmall,
        )
        when (status) {
            OfflineMapStatus.Unknown -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            OfflineMapStatus.NotAvailable -> Text(
                "No free satellite imagery here allows saving for offline use (only ACT Government imagery " +
                    "does). Use the Course plan map style when there's no signal.",
                style = MaterialTheme.typography.bodySmall,
            )
            is OfflineMapStatus.NotSaved -> OutlinedButton(onClick = vm::saveOfflineMap) {
                Text("Save satellite map for offline")
            }
            is OfflineMapStatus.Saving -> {
                val progress = if (status.total == 0) 0f else status.done.toFloat() / status.total
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Saving ${status.done} of ${status.total} map images…", style = MaterialTheme.typography.bodySmall)
            }
            OfflineMapStatus.Saved -> Text("✓ Satellite map saved: works with no signal.", style = MaterialTheme.typography.bodySmall)
            is OfflineMapStatus.Failed -> {
                Text("Couldn't save: ${status.reason}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::saveOfflineMap) { Text("Try again") }
            }
        }
    }
}
