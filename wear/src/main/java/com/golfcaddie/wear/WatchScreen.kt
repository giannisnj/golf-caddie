package com.golfcaddie.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.golfcaddie.core.WatchState

/**
 * The watch face of the caddie: what the phone worked out, big enough to read mid-swing-thought,
 * plus buttons to start and finish a shot without taking the phone out.
 */
@Composable
fun WatchScreen(
    state: WatchState,
    onStartShot: (clubId: String) -> Unit,
    onFinishShot: () -> Unit,
    onCancelShot: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    var pickingClub by remember { mutableStateOf(false) }
    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val center = state.center
                if (center == null) {
                    item {
                        Text(
                            state.status ?: "Open Golf Caddie on your phone",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    return@ScalingLazyColumn
                }
                state.hole?.let { item { Text(it, style = MaterialTheme.typography.caption1) } }
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(center, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Text(" ${state.unit}", style = MaterialTheme.typography.caption1)
                    }
                }
                val front = state.front
                val back = state.back
                if (front != null && back != null) {
                    item { Text("F $front · B $back", style = MaterialTheme.typography.caption1) }
                }
                state.playsLike?.let { item { Text(it, fontWeight = FontWeight.Bold) } }
                state.club?.let { club ->
                    item {
                        Text(club, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    }
                }
                state.clubRange?.let { item { Text(it, style = MaterialTheme.typography.caption2) } }
                state.warning?.let { w ->
                    item {
                        Text("⚠ $w", style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.error, textAlign = TextAlign.Center)
                    }
                }

                val shot = state.shotInProgress
                if (shot != null) {
                    item { Text(shot, textAlign = TextAlign.Center) }
                    item {
                        Chip(
                            onClick = onFinishShot,
                            label = { Text("Ball is here") },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { CompactChip(onClick = onCancelShot, label = { Text("Cancel") }) }
                } else if (state.clubs.isNotEmpty()) {
                    val recommended = state.clubs.firstOrNull { it.id == state.recommendedClubId }
                    recommended?.let { club ->
                        item {
                            Chip(
                                onClick = { onStartShot(club.id) },
                                label = { Text("Hit ${club.name}") },
                                secondaryLabel = { Text("Walk to the ball, then tap Ball is here") },
                                colors = ChipDefaults.primaryChipColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        CompactChip(
                            onClick = { pickingClub = !pickingClub },
                            label = { Text(if (pickingClub) "Hide clubs" else "Other club") },
                        )
                    }
                    if (pickingClub) {
                        items(state.clubs.filter { it.id != state.recommendedClubId }) { club ->
                            Chip(
                                onClick = { pickingClub = false; onStartShot(club.id) },
                                label = { Text(club.name) },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
