package com.gpxnavigator.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxnavigator.R
import com.gpxnavigator.navigation.ArrivalStatus
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import kotlin.math.roundToInt

@Composable
internal fun LandscapePanel(state: NavigationUiState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val unavailable = stringResource(R.string.metric_unavailable)
    val active = state.session.status == SessionStatus.ACTIVE
    val speed = state.location.point?.speedMetersPerSecond
        ?.takeIf { it.isFinite() && it >= 0f }?.let { (it * 3.6f).roundToInt().toString() } ?: unavailable
    val maximum = state.maximumSpeedMetersPerSecond?.takeIf { active }
        ?.let { (it * 3.6f).roundToInt().toString() } ?: unavailable
    val arrival = if (active) arrivalClockText(LocalContext.current, state.arrival) else unavailable
    val free = state.isFreeRecording
    BoxWithConstraints(modifier.padding(6.dp)) {
        val squareSize = (maxWidth - 6.dp) / 2
        val fontScale = LocalDensity.current.fontScale
        // Fill the available height; scroll only when the window or enlarged fonts need extra room.
        val arrivalHeight = maxOf((52f * fontScale).dp, (maxHeight - squareSize - 12.dp) * 0.28f)
        val speedHeight = maxOf((88f * fontScale).dp, maxHeight - squareSize - arrivalHeight - 12.dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InstrumentCard(Modifier.fillMaxWidth().height(speedHeight), accent = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FittedInstrumentText(stringResource(R.string.metric_speed_label),
                        Modifier.weight(1f).height(20.dp), 16f, color = scheme.onSurfaceVariant)
                    FittedInstrumentText(stringResource(R.string.metric_speed_unit),
                        Modifier.weight(0.55f).height(20.dp), 14f, color = scheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    FittedInstrumentText(speed, Modifier.fillMaxWidth(), 120f,
                        color = scheme.primary, numeric = true)
                }
                FittedInstrumentText(stringResource(R.string.metric_maximum_speed, maximum),
                    Modifier.fillMaxWidth().height(20.dp), 16f, color = scheme.onSurfaceVariant)
            }
            InstrumentCard(Modifier.fillMaxWidth().height(arrivalHeight)) {
                FittedInstrumentText(stringResource(if (free) R.string.metric_recording_time_label else R.string.metric_arrival_label),
                    Modifier.fillMaxWidth().height(18.dp), 15f, color = scheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    FittedInstrumentText(if (free) recordingElapsedText(state.recordingElapsedMillis) ?: unavailable else arrival,
                        Modifier.fillMaxWidth(),
                        if (free) 40f else if (state.arrival.status == ArrivalStatus.READY && active) 48f else 22f,
                        numeric = free || (state.arrival.status == ArrivalStatus.READY && active), maxLines = if (free) 1 else 2)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DistanceInstrument(stringResource(R.string.metric_travelled_tile_label),
                    if (free) state.recording.distanceMeters else state.progress?.travelledDistanceMeters,
                    Modifier.weight(1f).height(squareSize))
                if (free) ValueInstrument(stringResource(R.string.metric_elevation_label),
                    state.location.point?.elevationMeters?.takeIf { it.isFinite() }
                        ?.let { stringResource(R.string.metric_elevation_number, it) } ?: unavailable,
                    stringResource(R.string.metric_elevation_unit), Modifier.weight(1f).height(squareSize), scheme.primary)
                else DistanceInstrument(stringResource(R.string.metric_remaining_tile_label),
                    state.progress?.remainingDistanceMeters, Modifier.weight(1f).height(squareSize), valueColor = scheme.primary)
            }
        }
    }
}

@Composable
private fun InstrumentCard(modifier: Modifier, accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (accent) LocalNavigatorPalette.current.selectionBorder
            else LocalNavigatorPalette.current.instrumentBorder)) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            content = content)
    }
}

@Composable
private fun DistanceInstrument(label: String, meters: Double?, modifier: Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    val value = meters?.takeIf { it.isFinite() && it >= 0.0 }?.let {
        stringResource(R.string.metric_distance_number, it / 1_000)
    } ?: stringResource(R.string.metric_unavailable)
    ValueInstrument(label, value, stringResource(R.string.metric_distance_unit), modifier, valueColor)
}

@Composable
private fun ValueInstrument(label: String, value: String, unit: String, modifier: Modifier, valueColor: Color) {
    InstrumentCard(modifier) {
        FittedInstrumentText(label, Modifier.fillMaxWidth().height(20.dp), 16f,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            FittedInstrumentText(value, Modifier.fillMaxWidth(), 48f, color = valueColor, numeric = true)
        }
        FittedInstrumentText(unit,
            Modifier.fillMaxWidth().height(16.dp), 13f, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FittedInstrumentText(text: String, modifier: Modifier, maximumSp: Float,
    color: Color = MaterialTheme.colorScheme.onSurface, numeric: Boolean = false, maxLines: Int = 1) {
    BasicText(text, modifier = modifier,
        style = TextStyle(color = color, textAlign = TextAlign.Center,
            fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (numeric) FontWeight.Bold else FontWeight.Medium),
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(
            minFontSize = (8f / LocalDensity.current.fontScale.coerceAtLeast(1f)).sp,
            maxFontSize = maximumSp.sp, stepSize = 1.sp))
}
