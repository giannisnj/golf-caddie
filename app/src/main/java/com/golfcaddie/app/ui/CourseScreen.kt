package com.golfcaddie.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golfcaddie.app.ui.theme.GolfIcons
import com.golfcaddie.app.ui.theme.golf
import com.golfcaddie.core.Course

/** Finding a course, plus the settings that belong to it: live weather and the offline map. */
@Composable
fun CourseScreen(state: UiState, vm: CaddieViewModel, onCoursePicked: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.background)) {
        PageHeader("Course", "Find a course, offline map, settings")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.course?.let { CurrentCourse(it, state) }
            LiveWeather(state, vm)
            if (state.course != null) OfflineMap(state.offlineMap, vm)
            FindCourse(state, vm, onCoursePicked)
            Text(
                "Course maps © OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private fun holes(c: Course) = "${c.holes.size} holes"

@Composable
private fun CurrentCourse(course: Course, state: UiState) {
    val c = MaterialTheme.colorScheme
    val g = MaterialTheme.golf
    SectionCard(spacing = 6.dp) {
        Overline("Current course")
        Text(course.name, style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(holes(course), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.onSurfaceVariant)
            when {
                course.elevation != null -> Tag("Slopes included", g.goodContainer, g.onGoodContainer, icon = GolfIcons.Check)
                state.fetchingElevation -> Tag("Fetching slopes…", c.surfaceContainer, c.onSurfaceVariant)
                else -> Tag("No slopes yet: needs signal once", c.surfaceContainer, c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LiveWeather(state: UiState, vm: CaddieViewModel) {
    val c = MaterialTheme.colorScheme
    SectionCard(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(GolfIcons.Wind, 40.dp, 22.dp, c.surfaceContainer, c.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Live wind and temperature", style = MaterialTheme.typography.titleSmall)
                Text("Needs a data connection.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            Switch(
                checked = state.liveWeather, onCheckedChange = vm::setLiveWeather,
                colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
            )
        }
    }
}

/** Distances always work offline; this card only manages the satellite photo. */
@Composable
private fun OfflineMap(status: OfflineMapStatus, vm: CaddieViewModel) {
    val c = MaterialTheme.colorScheme
    val g = MaterialTheme.golf
    val title = MaterialTheme.typography.titleSmall
    val small = MaterialTheme.typography.bodySmall
    SectionCard {
        Overline("Offline map")
        Text(
            "Distances always work offline. Saving the satellite map keeps the course photo too.",
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
        )
        when (status) {
            OfflineMapStatus.Unknown -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spinner(28.dp)
                Text("Checking if the satellite map can be saved here…", style = title)
            }
            OfflineMapStatus.NotAvailable -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(GolfIcons.DownloadOff, 44.dp, 22.dp, c.surfaceContainerHigh, c.onSurface)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Satellite map can't be saved here", style = title)
                    Text(
                        "Only ACT Government imagery allows saving for offline use, and it covers Canberra. " +
                            "With no signal, use the Course plan map style.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp), color = c.onSurfaceVariant,
                    )
                }
            }
            is OfflineMapStatus.NotSaved -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Save satellite map for offline", onClick = vm::saveOfflineMap, icon = GolfIcons.Download, modifier = Modifier.fillMaxWidth())
                Text("${status.tiles} map images", style = small, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            is OfflineMapStatus.Saving -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (status.total == 0) "Getting ready to save…" else "Saving ${status.done} of ${status.total} map images", style = title)
                LinearProgressIndicator(
                    progress = { if (status.total == 0) 0f else status.done.toFloat() / status.total },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = c.primary, trackColor = c.surfaceContainerHighest, gapSize = 0.dp, drawStopIndicator = {},
                )
            }
            is OfflineMapStatus.Saved -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(GolfIcons.Check, 44.dp, 24.dp, g.goodContainer, g.onGoodContainer)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Satellite map saved for offline", style = title)
                    if (status.tiles > 0) Text("${status.tiles} map images", style = small, color = c.onSurfaceVariant)
                }
            }
            is OfflineMapStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GolfIcon(GolfIcons.Warning, 24.dp, c.error)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (status.total > 0) "Saving stopped at ${status.done} of ${status.total}" else "Saving stopped", style = title)
                        Text("Check your signal and try again.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp), color = c.onSurfaceVariant)
                    }
                }
                PillButton(
                    "Retry", onClick = vm::saveOfflineMap, icon = GolfIcons.Refresh,
                    container = c.secondaryContainer, content = c.onSecondaryContainer, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FindCourse(state: UiState, vm: CaddieViewModel, onCoursePicked: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    SectionCard(spacing = 12.dp) {
        Overline("Find a course")
        PillButton(
            "Courses near me", onClick = vm::findCoursesNearMe, icon = GolfIcons.NearMe,
            container = c.secondaryContainer, content = c.onSecondaryContainer,
            enabled = !state.loading, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Course name or town", style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = { GolfIcon(GolfIcons.Search, 22.dp, c.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; vm.clearSearchStatus() }) {
                        GolfIcon(GolfIcons.Close, 22.dp, c.onSurfaceVariant, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchCourses(query) }),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.outline, focusedBorderColor = c.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.builtInCourses.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GolfIcon(GolfIcons.Info, 18.dp, c.onSurfaceVariant)
                Text(
                    "Canberra's ${state.builtInCourses.size} courses are built in and found instantly, even offline.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }
        SearchStatusBlock(state.search, vm)

        val pick = { course: Course, builtIn: Boolean ->
            if (builtIn) vm.pickBuiltIn(course) else vm.selectCourse(course)
            onCoursePicked()
        }
        if (state.search == SearchStatus.Idle && state.courses.isNotEmpty()) {
            CourseList(state.courses, state.course?.id) { pick(it, false) }
        } else if (state.builtInCourses.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline("Built in: Canberra")
                CourseList(state.builtInCourses, state.course?.id) { pick(it, true) }
            }
        }
    }
}

@Composable
private fun CourseList(courses: List<Course>, current: Long?, onPick: (Course) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column {
        courses.forEach { course ->
            HorizontalDivider(color = c.outlineVariant)
            Row(
                Modifier.fillMaxWidth().height(56.dp).clickable { onPick(course) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(course.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(holes(course), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                if (course.id == current) Tag("Current", c.primaryContainer, c.onPrimaryContainer, icon = GolfIcons.Check)
            }
        }
    }
}

/** Searching (slow servers, so say so), nothing found, no position, or servers down with Try again. */
@Composable
private fun SearchStatusBlock(status: SearchStatus, vm: CaddieViewModel) {
    val c = MaterialTheme.colorScheme
    val big = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp)
    @Composable
    fun Info(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
        Column(
            Modifier.fillMaxWidth().background(c.surfaceContainer, MaterialTheme.shapes.large).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(icon, 56.dp, 26.dp, c.surfaceContainerHigh, c.onSurface)
            Text(title, style = big)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
    when (status) {
        SearchStatus.Idle -> Unit
        SearchStatus.Searching -> Column(
            Modifier.fillMaxWidth().background(c.surfaceContainer, MaterialTheme.shapes.large).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spinner(28.dp)
                Text("Searching course servers…", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "These servers are often slow. It can take a minute or two, so hang tight. It hasn't frozen.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = c.primary, trackColor = c.surfaceContainerHighest, gapSize = 0.dp,
            )
        }
        SearchStatus.NoPosition -> Info(GolfIcons.Gps, "No position yet", "Search by name, or wait for GPS.")
        is SearchStatus.NothingFound -> Info(
            GolfIcons.Search, "Nothing found for '${status.query}'", "Check the spelling, or try a course name or a nearby town.",
        )
        is SearchStatus.NoHoles -> Info(
            GolfIcons.Search,
            if (status.query != null) "No mapped holes near '${status.query}'" else "No mapped holes near you",
            "OpenStreetMap has no golf holes mapped there yet. Anyone can add them at openstreetmap.org.",
        )
        is SearchStatus.Failed -> Column(
            Modifier.fillMaxWidth().background(c.errorContainer, MaterialTheme.shapes.large).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GolfIcon(GolfIcons.Refresh, 24.dp, c.onErrorContainer)
                Text("Servers busy or down", style = big, color = c.onErrorContainer)
            }
            Text("Try again in a few minutes.", style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer)
            PillButton(
                "Try again", onClick = vm::retrySearch, icon = GolfIcons.Refresh,
                container = c.error, content = c.onError, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
