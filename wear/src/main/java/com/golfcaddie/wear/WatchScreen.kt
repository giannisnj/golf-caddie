package com.golfcaddie.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.golfcaddie.core.WatchState

/** Shown until the phone sends anything. */
const val WAITING_FOR_PHONE = "Waiting for phone"

private val Barlow = FontFamily(
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

private val Mint = Color(0xFF8ED8AC)
private val OnMint = Color(0xFF00381F)
private val Grey = Color(0xFFB9C3BB)
private val Amber = Color(0xFFFFB547)
private val OnAmber = Color(0xFF241300)

private fun icon(width: Float, vararg paths: String) = ImageVector.Builder("i", 24.dp, 24.dp, 24f, 24f).apply {
    paths.forEach {
        addPath(
            addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

private val WarningIcon = icon(2.5f, "M12 3.5L22 20.5H2L12 3.5z", "M12 10v4.5", "M12 17.4h0.01")
private val PhoneIcon = icon(2f, "M9.5 2.5h5a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 7 19V5a2.5 2.5 0 0 1 2.5-2.5z", "M11 18.5h2")

/**
 * The watch face of the caddie, laid out on a 454-unit round grid and scaled to the screen
 * (16 units lower than the design board, to leave the top edge to the clock): hole, the distance huge, front/back and plays like, the club and its range, the main hazard,
 * and one big button at the bottom edge. Hit starts a shot with the caddie's club; press and
 * hold it to pick another. While walking, Ball is here finishes the shot; hold it to cancel.
 */
@Composable
fun WatchScreen(
    state: WatchState,
    onStartShot: (clubId: String) -> Unit,
    onFinishShot: () -> Unit,
    onCancelShot: () -> Unit,
) {
    var pickingClub by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val unit = minOf(maxWidth, maxHeight) / 454f
        val density = LocalDensity.current
        val s = Scale(unit) { px -> with(density) { (unit * px).toSp() } }
        when {
            pickingClub -> ClubPicker(state) { id -> pickingClub = false; id?.let(onStartShot) }
            state.shotClub != null || state.shotInProgress != null -> ShotFace(state, s, onFinishShot, onCancelShot)
            state.center == null -> StatusFace(state.status ?: WAITING_FOR_PHONE, s)
            else -> MainFace(state, s, onHit = {
                val id = state.recommendedClubId
                if (id != null) onStartShot(id) else pickingClub = true
            }, onPickClub = { pickingClub = true })
        }
        // The time along the top edge, as Wear OS apps show it; quiet grey so the numbers lead.
        TimeText(timeTextStyle = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Grey, fontSize = s.sp(20f)))
    }
}

/** Exactly [height] of room for a text, centred: CSS's way with a line height under the font size. */
private fun Modifier.lineBox(height: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val h = height.roundToPx()
    layout(p.width, h) { p.place(0, (h - p.height) / 2) }
}

/** Design units to dp and sp. */
private class Scale(val unit: Dp, val sp: (Float) -> TextUnit) {
    fun dp(px: Float) = unit * px
}

private fun Scale.style(size: Float, line: Float, weight: FontWeight, color: Color = Color.White, spacing: Double = 0.0) = TextStyle(
    fontFamily = Barlow, fontWeight = weight, fontSize = sp(size), lineHeight = sp(line),
    letterSpacing = spacing.em, color = color, fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BottomButton(
    label: String, s: Scale, onClick: () -> Unit, onLongClick: () -> Unit, description: String,
) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .size(s.dp(236f), s.dp(84f))
            .background(Mint, RoundedCornerShape(topStart = s.dp(42f), topEnd = s.dp(42f)))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = s.dp(18f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = s.style(24f, 28f, FontWeight.Bold, OnMint))
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MainFace(state: WatchState, s: Scale, onHit: () -> Unit, onPickClub: () -> Unit) {
    val top = Modifier.align(Alignment.TopCenter)
    // "Hole 7 · Par 3": the number white, the rest grey.
    state.hole?.let { hole ->
        val parts = hole.split(" ", limit = 3)
        Row(top.offset(y = s.dp(56f)), horizontalArrangement = Arrangement.spacedBy(s.dp(6f))) {
            val grey = s.style(22f, 26f, FontWeight.SemiBold, Grey)
            Text(parts.getOrElse(0) { "" }, style = grey, modifier = Modifier.alignByBaseline())
            parts.getOrNull(1)?.let { Text(it, style = s.style(22f, 26f, FontWeight.Bold), modifier = Modifier.alignByBaseline()) }
            parts.getOrNull(2)?.let { Text(it, style = grey, modifier = Modifier.alignByBaseline()) }
        }
    }
    Row(top.offset(y = s.dp(86f)), horizontalArrangement = Arrangement.spacedBy(s.dp(6f))) {
        Text(state.center ?: "—", style = s.style(120f, 100f, FontWeight.Bold, spacing = -0.02), modifier = Modifier.alignByBaseline().lineBox(s.dp(100f)))
        Text(state.unit, style = s.style(24f, 28f, FontWeight.SemiBold, Grey), modifier = Modifier.alignByBaseline())
    }
    Row(top.offset(y = s.dp(194f)), horizontalArrangement = Arrangement.spacedBy(s.dp(8f))) {
        val fb = listOfNotNull(state.front?.let { "F $it" }, state.back?.let { "B $it" }).joinToString(" · ")
        val line = s.style(24f, 30f, FontWeight.SemiBold, Grey)
        Text(if (state.playsLike != null && fb.isNotEmpty()) "$fb ·" else fb, style = line)
        state.playsLike?.let { Text(it, style = line.copy(color = Mint)) }
    }
    state.club?.let { club ->
        Row(top.offset(y = s.dp(232f)), horizontalArrangement = Arrangement.spacedBy(s.dp(10f))) {
            Text(club, style = s.style(36f, 42f, FontWeight.Bold), modifier = Modifier.alignByBaseline())
            state.clubRange?.let {
                Text(it.removeSuffix(" ${state.unit}"), style = s.style(22f, 26f, FontWeight.SemiBold, Grey), modifier = Modifier.alignByBaseline())
            }
        }
    }
    state.warning?.let { w ->
        Row(
            top.offset(y = s.dp(286f)).background(Amber, CircleShape).padding(horizontal = s.dp(14f), vertical = s.dp(6f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.dp(8f)),
        ) {
            Icon(WarningIcon, null, Modifier.size(s.dp(20f)), tint = OnAmber)
            Text(w, style = s.style(20f, 24f, FontWeight.Bold, OnAmber), maxLines = 1)
        }
    }
    if (state.clubs.isNotEmpty()) {
        BottomButton("Hit", s, onHit, onPickClub, "Hit: start recording a shot. Press and hold to pick another club")
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ShotFace(state: WatchState, s: Scale, onFinish: () -> Unit, onCancel: () -> Unit) {
    val top = Modifier.align(Alignment.TopCenter)
    val club = state.shotClub ?: state.shotInProgress?.substringBefore(" · ")
    val soFar = state.shotSoFar ?: state.shotInProgress?.substringAfter(" · ")?.substringBefore(" ")
    Column(top.offset(y = s.dp(56f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.dp(4f))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(6f))) {
            Box(Modifier.size(s.dp(9f)).background(Mint, CircleShape))
            Text("RECORDING", style = s.style(14f, 18f, FontWeight.Bold, Mint, spacing = 0.1))
        }
        club?.let { Text(it, style = s.style(30f, 36f, FontWeight.Bold)) }
    }
    Column(top.offset(y = s.dp(130f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.dp(6f))) {
        Text(soFar ?: "—", style = s.style(120f, 100f, FontWeight.Bold, spacing = -0.02), modifier = Modifier.lineBox(s.dp(100f)))
        Text("${state.unit} so far", style = s.style(24f, 28f, FontWeight.SemiBold, Grey))
    }
    BottomButton("Ball is here", s, onFinish, onCancel, "Ball is here. Press and hold to cancel the shot")
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StatusFace(status: String, s: Scale) {
    val waiting = status == WAITING_FOR_PHONE
    Column(
        Modifier.align(Alignment.Center).padding(s.dp(60f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.dp(16f)),
    ) {
        Box(Modifier.size(s.dp(88f)).background(Color(0xFF1B5440), CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhoneIcon, null, Modifier.size(s.dp(48f)), tint = Mint)
        }
        Text(status, style = s.style(30f, 36f, FontWeight.Bold), textAlign = TextAlign.Center)
        if (waiting) {
            Text(
                "Golf Caddie on your phone sends the numbers here.",
                style = s.style(20f, 26f, FontWeight.Medium, Grey), textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = s.dp(300f)),
            )
        }
    }
}

/** Another club than the caddie's: a scrolling list, the caddie's pick first. */
@Composable
private fun ClubPicker(state: WatchState, onPick: (String?) -> Unit) {
    val clubs = state.clubs.sortedBy { if (it.id == state.recommendedClubId) 0 else 1 }
    ScalingLazyColumn(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Which club?", style = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, color = Color.White)) }
        items(clubs) { club ->
            Chip(
                onClick = { onPick(club.id) },
                label = { Text(club.name, style = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold)) },
                colors = if (club.id == state.recommendedClubId) ChipDefaults.chipColors(backgroundColor = Mint, contentColor = OnMint)
                else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(onClick = { onPick(null) }, label = { Text("Cancel", style = TextStyle(fontFamily = Barlow)) }, colors = ChipDefaults.secondaryChipColors())
        }
    }
}
