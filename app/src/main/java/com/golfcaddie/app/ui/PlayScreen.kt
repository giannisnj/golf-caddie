package com.golfcaddie.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.golfcaddie.app.map.MapStyle
import com.golfcaddie.app.ui.theme.Barlow
import com.golfcaddie.app.ui.theme.GolfIcons
import com.golfcaddie.app.ui.theme.golf
import com.golfcaddie.core.Advice
import com.golfcaddie.core.ClubType
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.Geo
import com.golfcaddie.core.HazardType
import com.golfcaddie.core.Recommendation
import com.golfcaddie.core.StatSource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Test tag of the big middle-of-green number. */
const val HERO_TAG = "hero"

/** Accuracy worse than this turns the GPS status amber: distances are rough. */
private const val ROUGH_GPS_METERS = 15f

/** How much of the map the caddie sheet leaves showing until it's pulled up. */
private val MAP_VISIBLE = 180.dp

/** The sheet's rounded top overlaps the map by this much. */
private val SHEET_OVERLAP = 20.dp

private fun TextStyle.sized(size: Int, line: Int, weight: FontWeight) =
    copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

@Composable
fun PlayScreen(
    state: UiState,
    vm: CaddieViewModel,
    onOpenCourse: () -> Unit,
    /** Asks for location again, or opens Settings when Android won't ask any more. */
    onFixLocation: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HoleBar(state, vm, onOpenCourse)
        val empty = when {
            state.course == null -> EmptyKind.NO_COURSE
            state.simulate -> null
            state.preciseLocationOff -> EmptyKind.APPROXIMATE
            !state.hasLocationPermission -> EmptyKind.PERMISSION
            state.position != null && state.situation == null -> EmptyKind.NO_HOLES
            else -> null
        }
        if (empty != null) {
            EmptyState(empty, state, onOpenCourse, onFixLocation)
        } else {
            PlayBody(state, vm)
        }
    }
}

// ---- Hole bar ---------------------------------------------------------------------------------

/** Reads like a tee sign: hole and par big, the course under it, arrows either side. */
@Composable
private fun HoleBar(state: UiState, vm: CaddieViewModel, onOpenCourse: () -> Unit) {
    val g = MaterialTheme.golf
    Surface(color = g.holeBar, contentColor = g.onHoleBar) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val course = state.course
            if (course == null) {
                Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Play", style = MaterialTheme.typography.titleLarge)
                    Text("No course loaded", style = HeaderSubtitle, color = g.onHoleBarVariant)
                }
            } else {
                IconButton(onClick = { vm.shiftHole(-1) }, Modifier.size(48.dp)) {
                    GolfIcon(GolfIcons.ChevronLeft, 28.dp, g.onHoleBar, contentDescription = "Previous hole")
                }
                Column(Modifier.weight(1f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val hole = state.hole
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Hole ${hole?.number ?: "—"}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
                        val par = listOfNotNull(hole?.par?.let { "Par $it" }, hole?.name).joinToString(" · ")
                        if (par.isNotEmpty()) {
                            Text(
                                par, style = MaterialTheme.typography.titleLarge.sized(20, 24, FontWeight.SemiBold),
                                color = g.onHoleBarVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    Row(
                        Modifier.clickable(onClick = onOpenCourse),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            course.name, style = HeaderSubtitle, color = g.onHoleBarVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        GolfIcon(GolfIcons.ChevronRight, 16.dp, g.onHoleBarVariant)
                    }
                }
                if (state.holeLocked) {
                    // Only after you've picked a hole yourself: back to following GPS.
                    Row(
                        Modifier
                            .height(40.dp)
                            .background(g.holeBarButton, CircleShape)
                            .clickable(onClick = vm::setAutoHole)
                            .padding(start = 10.dp, end = 14.dp)
                            .semantics { contentDescription = "Go back to automatic hole" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GolfIcon(GolfIcons.Gps, 18.dp, g.onHoleBar)
                        Text("Auto", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp))
                    }
                }
                IconButton(onClick = { vm.shiftHole(1) }, Modifier.size(48.dp)) {
                    GolfIcon(GolfIcons.ChevronRight, 28.dp, g.onHoleBar, contentDescription = "Next hole")
                }
            }
            MoreMenu(state, vm)
        }
    }
}

/** The ⋮ menu. Simulate GPS lives here: it's for testing, so it stays out of the way. */
@Composable
private fun MoreMenu(state: UiState, vm: CaddieViewModel) {
    val g = MaterialTheme.golf
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(48.dp).background(if (open) g.holeBarButton else Color.Transparent, CircleShape),
        ) {
            GolfIcon(GolfIcons.More, 24.dp, g.onHoleBar, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(260.dp),
        ) {
            DropdownMenuItem(
                text = {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Simulate GPS", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "For testing. Tap the map to move.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingIcon = { if (state.simulate) GolfIcon(GolfIcons.Check, 24.dp, MaterialTheme.colorScheme.onSurface) },
                onClick = { open = false; vm.setSimulate(!state.simulate) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

// ---- Map and caddie sheet -----------------------------------------------------------------------

@Composable
private fun PlayBody(state: UiState, vm: CaddieViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val inPlay = state.advice.filterIsInstance<Advice.InPlay>()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val collapsedTop = MAP_VISIBLE.coerceAtMost(maxHeight / 2)
        val baseTop by animateDpAsState(if (expanded) 0.dp else collapsedTop, label = "sheet")
        val top = (baseTop + with(density) { drag.toDp() }).coerceIn(0.dp, collapsedTop)

        Box(Modifier.fillMaxWidth().height(collapsedTop + SHEET_OVERLAP)) {
            CourseMap(
                course = state.course,
                hole = state.hole,
                player = state.position,
                accuracyMeters = state.accuracyMeters,
                style = state.mapStyle,
                onTap = vm::onMapTap,
                inPlay = inPlay.map { it.hazard.polygon },
                creditBottom = SHEET_OVERLAP + 6.dp,
                modifier = Modifier.fillMaxSize(),
            )
            MapControls(state, vm, Modifier.align(Alignment.TopStart).padding(12.dp))
            if (state.simulate) SimulateControls(state, vm)
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(0, top.roundToPx()) }
                .fillMaxWidth()
                .height(maxHeight - top)
                .shadow(18.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = MaterialTheme.golf.shadow, spotColor = MaterialTheme.golf.shadow),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                // Grab handle: drag or tap to pull the panel up over the map, and back.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .draggable(
                            rememberDraggableState { drag += it },
                            Orientation.Vertical,
                            onDragStopped = { velocity ->
                                val movedDp = with(density) { drag.toDp() }
                                expanded = when {
                                    velocity < -800 || movedDp < -40.dp -> true
                                    velocity > 800 || movedDp > 40.dp -> false
                                    else -> expanded
                                }
                                drag = 0f
                            },
                        )
                        .clickable { expanded = !expanded }
                        .semantics { contentDescription = if (expanded) "Lower the caddie panel" else "Pull up the caddie panel" },
                    contentAlignment = Alignment.TopCenter,
                ) { SheetHandle(Modifier.padding(top = 8.dp)) }

                CaddiePanel(state, Modifier.weight(1f))
                BottomAction(state, vm)
            }
        }
    }
}

/** Map style and units: small pills, 40 dp to the eye, 48 to the thumb. */
@Composable
private fun MapControls(state: UiState, vm: CaddieViewModel, modifier: Modifier) {
    val g = MaterialTheme.golf
    val pill = RoundedCornerShape(20.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var styles by remember { mutableStateOf(false) }
        Box {
            Row(
                Modifier
                    .height(40.dp)
                    .shadow(4.dp, pill)
                    .background(g.control, pill)
                    .clickable { styles = true }
                    .padding(start = 10.dp, end = 14.dp)
                    .semantics { contentDescription = "Map style: ${state.mapStyle.label}. Change map style" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GolfIcon(GolfIcons.Layers, 20.dp, g.onControl)
                Text(state.mapStyle.label, style = ControlText, color = g.onControl)
            }
            DropdownMenu(
                expanded = styles,
                onDismissRequest = { styles = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                MapStyle.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) },
                        trailingIcon = { if (s == state.mapStyle) GolfIcon(GolfIcons.Check, 22.dp, MaterialTheme.colorScheme.primary) },
                        onClick = { styles = false; vm.setMapStyle(s) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        Row(
            Modifier.height(40.dp).shadow(4.dp, pill).background(g.control, pill).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DistanceUnit.entries.forEach { u ->
                val on = u == state.unit
                Box(
                    Modifier
                        .height(34.dp)
                        .widthIn(min = 48.dp)
                        .background(if (on) g.controlSelected else Color.Transparent, RoundedCornerShape(17.dp))
                        .clickable { vm.setUnit(u) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        u.label,
                        style = ControlText.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                        color = if (on) g.onControlSelected else g.onControl,
                    )
                }
            }
        }
    }
}

private val ControlText = TextStyle(fontFamily = Barlow, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)

/** While simulating: a badge so it can't be mistaken for real GPS, and the test moves. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SimulateControls(state: UiState, vm: CaddieViewModel) {
    val g = MaterialTheme.golf
    val inv = MaterialTheme.colorScheme.inverseSurface
    val onInv = MaterialTheme.colorScheme.inverseOnSurface
    Column(
        Modifier.align(Alignment.TopEnd).padding(12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.height(28.dp).background(inv, CircleShape).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GolfIcon(GolfIcons.Simulated, 14.dp, onInv)
            Text("SIMULATED GPS", style = MaterialTheme.typography.labelMedium.sized(12, 14, FontWeight.Bold).copy(letterSpacing = 0.06.em), color = onInv)
        }
        val pill = RoundedCornerShape(20.dp)
        listOf("Tee" to vm::jumpToTee, "+25 ${state.unit.label}" to { vm.stepTowardGreen(state.unit.toMeters(25.0)) }).forEach { (label, action) ->
            Box(
                Modifier.height(40.dp).shadow(4.dp, pill).background(g.control, pill).clickable(onClick = action).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = ControlText.copy(fontWeight = FontWeight.Bold), color = g.onControl) }
        }
    }
    Text(
        "Tap the map to move",
        style = MaterialTheme.typography.bodySmall.sized(12, 16, FontWeight.SemiBold),
        color = onInv,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = SHEET_OVERLAP + 6.dp)
            .background(inv, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---- The caddie panel -----------------------------------------------------------------------------

@Composable
private fun CaddiePanel(state: UiState, modifier: Modifier) {
    val scroll = rememberScrollState()
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = state.situation
            val u = state.unit
            fun n(m: Double) = u.fromMeters(m).roundToInt().toString()
            if (s != null && s.offLineMeters > 60) WrongHoleHint("You're ${formatDistance(s.offLineMeters, u)} off this hole's line. Wrong hole?")
            DistanceBlock(state)
            if (s == null) {
                WaitingCard(state.simulate)
                ClubPlaceholder()
                return@Column
            }
            val walking = state.pendingShot != null
            state.playsLike?.let { p ->
                if (!walking && CaddieText.playsLike(p, u) != null) PlaysLikeCard(p, u)
            }
            s.green.pin?.let { FlagLine("Flag ${n(it)} ${u.label}") }
            state.recommendation?.let { ClubCard(it, u) }
            val inPlay = state.advice.filterIsInstance<Advice.InPlay>()
            if (walking) {
                inPlay.firstOrNull()?.let { CompactWarning(it, u) }
            } else {
                inPlay.forEach { a ->
                    HazardWarning(a, state.advice.filter { it !is Advice.InPlay && it.hazard == a.hazard }, u)
                }
                if (s.hazards.isNotEmpty()) NearTheLine(s.hazards.take(3), inPlay.map { it.hazard }, u)
            }
        }
        // Fades the last line out where the panel cuts it off, so it reads as "more below".
        if (scroll.canScrollForward) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
                    .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))),
            )
        }
    }
}

/** The hero: middle of the green, huge; front and back beside it. A dash while waiting, never a zero. */
@Composable
private fun DistanceBlock(state: UiState) {
    val onVar = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVar = MaterialTheme.colorScheme.outlineVariant
    val s = state.situation
    val u = state.unit
    fun n(m: Double?) = m?.let { u.fromMeters(it).roundToInt().toString() } ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Middle of green", Modifier.weight(1f))
            GpsStatus(state)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    n(s?.green?.center), style = MaterialTheme.typography.displayLarge,
                    color = if (s == null) onVar else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline().testTag(HERO_TAG).lineBox(86.dp),
                )
                Text(u.label, style = MaterialTheme.typography.headlineSmall.sized(24, 28, FontWeight.SemiBold), color = onVar, modifier = Modifier.alignByBaseline())
            }
            Column(
                Modifier
                    .width(128.dp)
                    .drawBehind { drawLine(outlineVar, Offset.Zero, Offset(0f, size.height), 1.dp.toPx()) }
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // On the green the front edge is behind you; only the back still matters.
                if (s?.onGreen != true) FrontBack("Front", n(s?.green?.front), s == null)
                FrontBack("Back", n(s?.green?.back), s == null)
            }
        }
    }
}

@Composable
private fun FrontBack(label: String, value: String, waiting: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        Overline(label, Modifier.weight(1f).alignByBaseline())
        Text(
            value, style = MaterialTheme.typography.headlineMedium,
            color = if (waiting) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** On the overline, next to the numbers it qualifies. Poor accuracy turns it amber. */
@Composable
private fun GpsStatus(state: UiState) {
    val onVar = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.em)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val accuracy = state.accuracyMeters
        when {
            state.position == null && !state.simulate -> {
                Spinner(16.dp, onVar)
                Text("Searching…", style = style, color = onVar)
            }
            accuracy != null -> {
                val rough = accuracy > ROUGH_GPS_METERS
                val color = if (rough) MaterialTheme.golf.hazard else onVar
                GolfIcon(GolfIcons.Gps, 16.dp, color)
                Text(
                    "GPS ±${formatDistance(accuracy.toDouble(), state.unit)}" + if (rough) " · distances rough" else "",
                    style = style, color = color,
                )
            }
        }
    }
}

@Composable
private fun WrongHoleHint(text: String) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(c.tertiaryContainer, MaterialTheme.shapes.medium).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GolfIcon(GolfIcons.Info, 22.dp, c.onTertiaryContainer)
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.onTertiaryContainer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaysLikeCard(p: com.golfcaddie.core.PlaysLike, u: DistanceUnit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("Plays like", Modifier.alignByBaseline(), c.onPrimaryContainer)
            Text(formatDistance(p.playsMeters, u), style = MaterialTheme.typography.headlineMedium, color = c.onPrimaryContainer, modifier = Modifier.alignByBaseline())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CaddieText.playsLikeReasons(p, u).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val icon = when (r.kind) {
                        CaddieText.Reason.UPHILL -> GolfIcons.Uphill
                        CaddieText.Reason.DOWNHILL -> GolfIcons.Downhill
                        CaddieText.Reason.WIND -> GolfIcons.Wind
                        CaddieText.Reason.TEMPERATURE -> GolfIcons.Thermometer
                    }
                    GolfIcon(icon, 16.dp, c.onPrimaryContainer)
                    Text(
                        r.text.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium.sized(15, 20, FontWeight.SemiBold), color = c.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlagLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GolfIcon(GolfIcons.Flag, 18.dp, MaterialTheme.golf.flag)
        Text(text, style = MaterialTheme.typography.bodyMedium.sized(16, 20, FontWeight.SemiBold))
        // An OSM flag is where it stood on mapping day, so it's shown but not planned on.
        Text("(may have moved)", style = MaterialTheme.typography.bodyMedium.sized(16, 20, FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The club to hit: name big on the caddie's green, then why, then the optional extras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClubCard(rec: Recommendation, u: DistanceUnit) {
    val pick = rec.club ?: return
    val g = MaterialTheme.golf
    fun n(m: Double) = u.fromMeters(m).roundToInt()
    val putt = rec.kind == Recommendation.Kind.PUTT
    val name = if (putt) "Putter" else pick.club.name
    val kind = when (rec.kind) {
        Recommendation.Kind.FULL_SWING -> "Full swing"
        Recommendation.Kind.PARTIAL_SWING -> "Partial swing"
        Recommendation.Kind.CANNOT_REACH -> "Can't reach"
        Recommendation.Kind.PUTT -> "On the green"
    }
    Column(
        Modifier.fillMaxWidth().background(g.clubCard, MaterialTheme.shapes.large).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 12.dp)) {
                // Long names ("Pitching Wedge") need the room more than the label does.
                if (name.length <= 8) Overline("Club", Modifier.alignByBaseline(), g.onClubCardVariant)
                Text(name, style = MaterialTheme.typography.displaySmall, color = g.onClubCard, modifier = Modifier.alignByBaseline())
            }
            Tag(kind, g.clubChip, g.onClubCard, Modifier.align(Alignment.CenterVertically), caps = true)
        }
        if (putt) return@Column
        val body = MaterialTheme.typography.bodyMedium
        val range = CaddieText.range(pick, u)
        val basis = when (pick.source) {
            StatSource.HISTORY -> "You hit it $range, avg ${n(pick.averageMeters ?: pick.typicalMeters)}, ${pick.shotCount} shots"
            StatSource.MAX_ONLY -> "About ${n(pick.typicalMeters)} from your max of ${n(pick.maxMeters ?: 0.0)}"
            StatSource.DEFAULT -> "About ${n(pick.typicalMeters)} for a typical golfer. Add your shots in Clubs."
        }
        Text(basis, style = body, color = g.onClubCardVariant)
        val left = rec.leftAfterMeters?.takeIf { rec.kind == Recommendation.Kind.CANNOT_REACH }
        val side = CaddieText.side(pick, u)
        val shorter = rec.shorterOption
        if (left != null || side != null || shorter != null) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp).height(1.dp).background(g.clubChip))
        }
        left?.let {
            Text("Leaves ${formatDistance(it, u)} to the green", style = body.copy(fontWeight = FontWeight.SemiBold), color = g.onClubCard)
        }
        side?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sm = pick.sideMeters ?: 0.0
                if (!it.startsWith("straight")) GolfIcon(if (sm > 0) GolfIcons.ArrowRight else GolfIcons.ArrowLeft, 18.dp, g.onClubCard)
                Text("Your ${pick.club.name} ${if (it.startsWith("straight")) "flies $it" else it}", style = body.copy(fontWeight = FontWeight.SemiBold), color = g.onClubCard)
            }
        }
        shorter?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GolfIcon(GolfIcons.ArrowDown, 18.dp, g.onClubCardVariant)
                Text(
                    "Or ${it.club.name} if you catch it well (max ${n(it.maxMeters ?: it.typicalMeters)})",
                    style = body.sized(15, 19, FontWeight.Medium), color = g.onClubCardVariant,
                )
            }
        }
    }
}

/** The most important secondary message. Amber is kept for warnings only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HazardWarning(a: Advice.InPlay, alternatives: List<Advice>, u: DistanceUnit) {
    val g = MaterialTheme.golf
    Column(
        Modifier
            .fillMaxWidth()
            .background(g.hazardContainer, MaterialTheme.shapes.large)
            .border(2.dp, g.hazardBorder, MaterialTheme.shapes.large)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Hazard warning" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GolfIcon(GolfIcons.Warning, 22.dp, g.hazardTitle, Modifier.padding(top = 1.dp))
            Text(CaddieText.inPlayTitle(a.hazard, u), style = MaterialTheme.typography.titleSmall, color = g.hazardTitle)
        }
        Text(CaddieText.inPlayBody(a), style = MaterialTheme.typography.bodyMedium, color = g.onHazardContainer)
        if (alternatives.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alternatives.forEach { alt ->
                    Text(
                        CaddieText.advice(alt, u),
                        style = MaterialTheme.typography.bodyMedium.sized(15, 18, FontWeight.Bold),
                        color = g.onHazardContainer,
                        modifier = Modifier.background(g.hazardChip, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** One line, while walking: the warning still shows but gives the shot its room. */
@Composable
private fun CompactWarning(a: Advice.InPlay, u: DistanceUnit) {
    val g = MaterialTheme.golf
    val (lead, rest) = CaddieText.inPlayCompact(a, u)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(g.hazardContainer, MaterialTheme.shapes.medium)
            .border(2.dp, g.hazardBorder, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GolfIcon(GolfIcons.Warning, 22.dp, g.hazardTitle)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = g.hazardTitle)) { append(lead) }
                append(rest)
            },
            style = MaterialTheme.typography.titleSmall, color = g.onHazardContainer,
        )
    }
}

/** Up to 3 hazards near the line, nearest first; the one in play gets the amber tile. */
@Composable
private fun NearTheLine(hazards: List<com.golfcaddie.core.HazardOnLine>, inPlay: List<com.golfcaddie.core.HazardOnLine>, u: DistanceUnit) {
    val c = MaterialTheme.colorScheme
    val g = MaterialTheme.golf
    fun n(m: Double) = u.fromMeters(m).roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Overline("Near the line")
        hazards.forEach { h ->
            val amber = h in inPlay
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(
                    if (h.type == HazardType.BUNKER) GolfIcons.Bunker else GolfIcons.Water, 32.dp, 18.dp,
                    if (amber) g.hazardContainer else c.surfaceContainer,
                    if (amber) g.onHazardContainer else c.onSurfaceVariant,
                )
                Text(CaddieText.hazardLabel(h), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text(
                    "reach ${n(h.reachMeters)} · carry ${n(h.carryMeters)}",
                    style = MaterialTheme.typography.bodyMedium.sized(15, 20, FontWeight.Medium), color = c.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WaitingCard(simulate: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(c.surfaceContainer, MaterialTheme.shapes.large).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (simulate) GolfIcon(GolfIcons.Simulated, 28.dp, c.onSurfaceVariant) else Spinner(28.dp, c.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (simulate) "Simulated GPS" else "Waiting for GPS", style = MaterialTheme.typography.titleMedium)
            Text(
                if (simulate) "Tap the map to place yourself." else "Finding your position. Outdoors this usually takes a few seconds.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClubPlaceholder() {
    val dash = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    dash, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Overline("Club")
        Text("Shows up once your position is found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Recording a shot -------------------------------------------------------------------------------

@Composable
private fun BottomAction(state: UiState, vm: CaddieViewModel) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.shotNotice?.let { ShotNoticeCard(it, state.unit, vm) }
        val pending = state.pendingShot
        if (pending != null) {
            val soFar = state.position?.let { Geo.distance(pending.start, it) } ?: 0.0
            ShotInProgress("${pending.club.name}: ${formatDistance(soFar, state.unit)} so far", vm::finishShot, vm::cancelShot)
        } else {
            PillButton(
                "Record a shot", onClick = { picking = true }, icon = GolfIcons.Record,
                container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer,
                enabled = state.position != null, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (picking) PickClubSheet(state, onPick = { vm.startShot(it); picking = false }, onDismiss = { picking = false })
}

/** Takes the place of Record a shot while you walk. Inverse colours so it can't be missed. */
@Composable
private fun ShotInProgress(text: String, onBallIsHere: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(c.inverseSurface, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(c.inversePrimary, CircleShape))
                Overline("Recording", color = c.inversePrimary)
            }
            Text(text, style = MaterialTheme.typography.headlineLarge.sized(28, 34, FontWeight.Bold), color = c.inverseOnSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = c.inverseOnSurface)
            }
            PillButton(
                "Ball is here", onClick = onBallIsHere, icon = GolfIcons.Pin,
                container = c.inversePrimary, content = if (MaterialTheme.golf.dark) Color.White else Color(0xFF00381F),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Confirms in plain words, just above the main action. A too-short shot gets the warning look. */
@Composable
private fun ShotNoticeCard(notice: ShotNotice, u: DistanceUnit, vm: CaddieViewModel) {
    val g = MaterialTheme.golf
    LaunchedEffect(notice) {
        delay(if (notice is ShotNotice.Saved) 6_000 else 10_000)
        vm.clearShotNotice()
    }
    when (notice) {
        is ShotNotice.Saved -> Row(
            Modifier.fillMaxWidth().background(g.goodContainer, MaterialTheme.shapes.medium).clickable(onClick = vm::clearShotNotice)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(GolfIcons.Check, 32.dp, 20.dp, g.good, g.goodContainer)
            Text("Saved ${notice.club}: ${formatDistance(notice.meters, u)}", style = MaterialTheme.typography.titleMedium.sized(18, 22, FontWeight.Bold), color = g.onGoodContainer)
        }
        is ShotNotice.TooShort -> Row(
            Modifier.fillMaxWidth().background(g.hazardContainer, MaterialTheme.shapes.medium).border(2.dp, g.hazardBorder, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GolfIcon(GolfIcons.Warning, 22.dp, g.hazardTitle)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Only ${formatDistance(notice.meters, u)} from where you hit", style = MaterialTheme.typography.titleSmall, color = g.hazardTitle)
                Text("Walk to your ball first, or tap Cancel.", style = MaterialTheme.typography.bodyMedium, color = g.onHazardContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickClubSheet(state: UiState, onPick: (com.golfcaddie.core.Club) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val g = MaterialTheme.golf
    val clubs = state.bag.clubs.filter { it.inBag && it.type != ClubType.PUTTER }
    val pickId = state.recommendation?.club?.club?.id
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.surface,
        dragHandle = { SheetHandle(Modifier.padding(top = 8.dp, bottom = 16.dp)) },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Which club are you hitting?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Then walk to your ball and tap Ball is here.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                clubs.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { club ->
                            val mine = club.id == pickId
                            Column(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 60.dp)
                                    .background(if (mine) g.clubCard else c.surfaceContainer, MaterialTheme.shapes.medium)
                                    .border(1.dp, if (mine) g.clubCard else c.outlineVariant, MaterialTheme.shapes.medium)
                                    .clickable { onPick(club) }
                                    .padding(horizontal = 6.dp, vertical = 8.dp)
                                    .semantics(mergeDescendants = true) { if (mine) contentDescription = "${club.name}, the caddie's pick" },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (mine) {
                                    Text("CADDIE'S PICK", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.08.em), color = g.onClubCardVariant)
                                }
                                Text(
                                    club.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                                    color = if (mine) g.onClubCard else c.onSurface,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp).height(48.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = c.primary)
            }
        }
    }
}

// ---- Empty and error states -------------------------------------------------------------------------

private enum class EmptyKind { NO_COURSE, PERMISSION, APPROXIMATE, NO_HOLES }

@Composable
private fun EmptyState(kind: EmptyKind, state: UiState, onOpenCourse: () -> Unit, onFixLocation: () -> Unit) {
    val c = MaterialTheme.colorScheme
    data class Copy(val title: String, val body: String, val cta: String, val note: String?, val error: Boolean, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val copy = when (kind) {
        EmptyKind.NO_COURSE -> Copy(
            "No course loaded", "Pick a course and the caddie works out which hole you're on from GPS.",
            "Find a course", "Canberra's ${state.builtInCourses.size} courses are built in, even offline.", false, GolfIcons.Course,
        )
        EmptyKind.PERMISSION -> Copy(
            "Location is off", "Golf Caddie needs your location to measure distances to the green.",
            "Allow location", null, true, GolfIcons.LocationOff,
        )
        EmptyKind.APPROXIMATE -> Copy(
            "Location is set to Approximate",
            "Approximate location can be out by a kilometre or more. That's too rough for golf. Switch Golf Caddie to Precise location.",
            "Use precise location", null, true, GolfIcons.Gps,
        )
        EmptyKind.NO_HOLES -> Copy(
            "No holes near you",
            "You don't seem to be on ${state.course?.name ?: "this course"}. Distances start once you're on the course.",
            "Choose another course", "Or pick a hole yourself with the arrows above.", false, GolfIcons.Pin,
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(
                copy.icon, 72.dp, 34.dp,
                if (copy.error) c.errorContainer else c.primaryContainer,
                if (copy.error) c.onErrorContainer else c.onPrimaryContainer,
                Modifier.padding(bottom = 4.dp),
            )
            Text(copy.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                copy.body, style = MaterialTheme.typography.bodyLarge.sized(17, 23, FontWeight.Medium),
                color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    copy.cta,
                    onClick = if (kind == EmptyKind.PERMISSION || kind == EmptyKind.APPROXIMATE) onFixLocation else onOpenCourse,
                    modifier = Modifier.fillMaxWidth(),
                )
                copy.note?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodyMedium.sized(15, 20, FontWeight.Medium),
                        color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
