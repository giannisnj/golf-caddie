package com.golfcaddie.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.golfcaddie.app.ui.theme.Barlow
import com.golfcaddie.app.ui.theme.CssLineHeight
import com.golfcaddie.app.ui.theme.GolfIcons
import com.golfcaddie.app.ui.theme.golf

/** The small capitals label above a value: "MIDDLE OF GREEN", "CLUB", "NEAR THE LINE". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun GolfIcon(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}

/** The design's spinner: a faint ring with a turning quarter arc. */
@Composable
fun Spinner(size: Dp, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin",
    )
    GolfIcon(GolfIcons.Spinner, size, tint, modifier.rotate(turn))
}

/** A 56 dp pill: the main action of a screen. 48 dp is the minimum target, 56 for the main one. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.38f), disabledContentColor = content.copy(alpha = 0.38f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (icon != null) {
            GolfIcon(icon, 22.dp, if (enabled) content else content.copy(alpha = 0.38f))
            Box(Modifier.size(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A pill-shaped tag: "FULL SWING", "Current", "Slopes included". */
@Composable
fun Tag(
    text: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    caps: Boolean = false,
) {
    Row(
        modifier
            .background(background, CircleShape)
            .padding(start = if (icon != null) 8.dp else 10.dp, end = if (icon != null) 12.dp else 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) GolfIcon(icon, 16.dp, color)
        if (caps) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.06.em), color = color, maxLines = 1)
        } else {
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
        }
    }
}

/** A white (or dark) rounded card on the screen's tinted background: the Clubs and Course cards. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** The green bar at the top of Clubs and Course: a title and a line under it, below the status bar. */
@Composable
fun PageHeader(title: String, subtitle: String?, leading: (@Composable () -> Unit)? = null) {
    val g = MaterialTheme.golf
    Surface(color = g.holeBar, contentColor = g.onHoleBar) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(68.dp)
                .padding(start = if (leading != null) 4.dp else 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            leading?.invoke()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = HeaderSubtitle, color = g.onHoleBarVariant, maxLines = 1) }
            }
        }
    }
}

/**
 * Gives a text exactly [height] of room, centred, as CSS does with a line height smaller than the
 * font (the hero is 104 sp in an 86 sp line). Compose would otherwise keep the font's full height
 * and push the number away from its label.
 */
fun Modifier.lineBox(height: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val h = height.roundToPx()
    layout(p.width, h) { p.place(0, (h - p.height) / 2) }
}

/** The line under a header's title: the course name, "12 clubs in your bag". */
val HeaderSubtitle = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum", lineHeightStyle = CssLineHeight)

/** The 36 × 4 grab handle at the top of a sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier.size(36.dp, 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
    )
}

/** A round icon tile: 32–72 dp, used for empty states, hazards and status rows. */
@Composable
fun IconTile(icon: ImageVector, size: Dp, iconSize: Dp, background: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        GolfIcon(icon, iconSize, tint)
    }
}
