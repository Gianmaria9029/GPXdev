package com.gpxnavigator.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.gpxnavigator.R
import com.gpxnavigator.navigation.GuidanceStatus
import com.gpxnavigator.navigation.Maneuver
import com.gpxnavigator.navigation.TrackGuidance
import com.gpxnavigator.navigation.SecondaryManeuver
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
internal fun ManeuverBox(guidance: TrackGuidance, modifier: Modifier = Modifier,
    prominent: Boolean = false, boxSize: Dp = 128.dp) {
    val palette = LocalNavigatorPalette.current
    val label = stringResource(when (guidance.status) {
        GuidanceStatus.WAITING_FOR_GPS -> R.string.guidance_waiting
        GuidanceStatus.CHECKING_TRACK -> R.string.guidance_checking
        GuidanceStatus.MATCHING_TRACK -> R.string.guidance_matching
        GuidanceStatus.REVERSE_DIRECTION -> R.string.guidance_reverse
        GuidanceStatus.OFF_TRACK -> R.string.off_track_alert
        GuidanceStatus.SEGMENT_END -> R.string.guidance_segment_end
        GuidanceStatus.TRACK_END -> R.string.guidance_track_end
        GuidanceStatus.ACTIVE -> maneuverLabel(guidance.maneuver)
    })
    val second = guidance.secondManeuver.takeIf {
        guidance.status == GuidanceStatus.ACTIVE && guidance.maneuver != null && guidance.maneuver != Maneuver.STRAIGHT
    }
    Column(modifier.width(boxSize)
        .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(Modifier.size(boxSize),
            shape = RoundedCornerShape(8.dp), shadowElevation = 4.dp,
            border = if (prominent) BorderStroke(1.dp, palette.maneuverArrow) else null,
            color = if (guidance.status == GuidanceStatus.OFF_TRACK) palette.danger else palette.maneuverBackground,
            contentColor = if (guidance.status == GuidanceStatus.OFF_TRACK) Color.White else palette.maneuverContent) {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    ManeuverSymbol(guidance, Modifier.size(minOf(maxWidth, maxHeight,
                        if (prominent) boxSize * 0.46f else 58.dp)))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        maxLines = if (guidance.status == GuidanceStatus.ACTIVE) 1 else 2,
                        overflow = TextOverflow.Ellipsis)
                ManeuverDistance(guidance, if (prominent) boxSize else null)
                if (prominent) maneuverApproachFraction(guidance)?.let { ManeuverApproachBar(it) }
            }
        }
        if (second != null) Surface(Modifier.fillMaxWidth(),
            color = palette.maneuverBackground, contentColor = palette.maneuverContent,
            shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp) {
            SecondaryManeuverRow(second, Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun ManeuverApproachBar(fraction: Float) {
    val color = LocalNavigatorPalette.current.maneuverArrow
    val description = stringResource(R.string.guidance_approach)
    Canvas(Modifier.fillMaxWidth().height(6.dp).semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
    }) {
        drawRect(color.copy(alpha = 0.18f))
        drawRect(color, size = size.copy(width = size.width * fraction))
    }
}

private fun maneuverLabel(maneuver: Maneuver?): Int = when (maneuver) {
    Maneuver.SLIGHT_LEFT -> R.string.guidance_slight_left
    Maneuver.SLIGHT_RIGHT -> R.string.guidance_slight_right
    Maneuver.LEFT -> R.string.guidance_left
    Maneuver.RIGHT -> R.string.guidance_right
    Maneuver.HAIRPIN_LEFT -> R.string.guidance_hairpin_left
    Maneuver.HAIRPIN_RIGHT -> R.string.guidance_hairpin_right
    else -> R.string.guidance_straight
}

@Composable
private fun SecondaryManeuverRow(second: SecondaryManeuver, modifier: Modifier) {
    val meters = (second.distanceAfterMeters / 5).roundToInt().times(5).coerceAtLeast(5)
    val description = pluralStringResource(R.plurals.guidance_second_description, meters,
        stringResource(maneuverLabel(second.maneuver)), meters)
    Row(modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ManeuverArrow(second.maneuver, Modifier.size(20.dp))
        Text(stringResource(R.string.guidance_second_after, meters), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ManeuverSymbol(guidance: TrackGuidance, modifier: Modifier) {
    if (guidance.status == GuidanceStatus.ACTIVE && guidance.maneuver != null) {
        ManeuverArrow(guidance.maneuver, modifier)
    } else {
        Text(stringResource(R.string.metric_unavailable), style = MaterialTheme.typography.headlineLarge,
            color = if (guidance.status == GuidanceStatus.OFF_TRACK) Color.White
                else LocalNavigatorPalette.current.maneuverArrow)
    }
}

@Composable
private fun ManeuverDistance(guidance: TrackGuidance, prominentSize: Dp? = null) {
    if (guidance.status != GuidanceStatus.ACTIVE) return
    val distance = guidance.distanceMeters?.takeIf { it.isFinite() && it >= 0 } ?: return
    val distanceStyle = if (prominentSize == null) MaterialTheme.typography.titleLarge else {
        val fontSize = minOf(34f, prominentSize.value / 5.5f) / LocalDensity.current.fontScale
        MaterialTheme.typography.headlineMedium.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.15f).sp)
    }
    Text(if (distance >= 1_000) stringResource(R.string.guidance_distance_km, distance / 1_000)
        else stringResource(R.string.guidance_distance_m, (distance / 5).roundToInt() * 5),
        style = distanceStyle,
        color = if (prominentSize != null) LocalNavigatorPalette.current.maneuverDistance
            else LocalNavigatorPalette.current.maneuverContent,
        fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ManeuverArrow(maneuver: Maneuver, modifier: Modifier) {
    val arrowColor = LocalNavigatorPalette.current.maneuverArrow
    Canvas(modifier) {
        val mirrored = maneuver == Maneuver.SLIGHT_LEFT || maneuver == Maneuver.LEFT || maneuver == Maneuver.HAIRPIN_LEFT
        fun point(x: Float, y: Float) = Offset((if (mirrored) 1 - x else x) * size.width, y * size.height)
        val path = Path()
        fun move(x: Float, y: Float) { val p = point(x, y); path.moveTo(p.x, p.y) }
        fun line(x: Float, y: Float) { val p = point(x, y); path.lineTo(p.x, p.y) }
        fun curve(cx: Float, cy: Float, x: Float, y: Float) {
            val c = point(cx, cy); val p = point(x, y)
            path.quadraticTo(c.x, c.y, p.x, p.y)
        }
        val tip: Offset
        val beforeTip: Offset
        when (maneuver) {
            Maneuver.STRAIGHT -> {
                move(0.5f, 0.88f); line(0.5f, 0.15f)
                tip = point(0.5f, 0.15f); beforeTip = point(0.5f, 0.55f)
            }
            Maneuver.SLIGHT_LEFT, Maneuver.SLIGHT_RIGHT -> {
                move(0.32f, 0.88f); line(0.32f, 0.62f)
                curve(0.32f, 0.5f, 0.43f, 0.39f); line(0.77f, 0.15f)
                tip = point(0.77f, 0.15f); beforeTip = point(0.43f, 0.39f)
            }
            Maneuver.LEFT, Maneuver.RIGHT -> {
                move(0.3f, 0.88f); line(0.3f, 0.45f)
                curve(0.3f, 0.3f, 0.45f, 0.3f); line(0.83f, 0.3f)
                tip = point(0.83f, 0.3f); beforeTip = point(0.45f, 0.3f)
            }
            Maneuver.HAIRPIN_LEFT, Maneuver.HAIRPIN_RIGHT -> {
                // The head points down: the outgoing direction is opposite to the incoming one.
                move(0.25f, 0.88f); line(0.25f, 0.38f)
                curve(0.25f, 0.13f, 0.5f, 0.13f)
                curve(0.78f, 0.13f, 0.78f, 0.38f); line(0.78f, 0.66f)
                tip = point(0.78f, 0.66f); beforeTip = point(0.78f, 0.38f)
            }
        }
        val direction = tip - beforeTip
        val length = hypot(direction.x, direction.y)
        val unit = direction / length
        val headLength = size.minDimension * 0.20f
        val headWidth = size.minDimension * 0.16f
        val base = tip - unit * headLength
        val perpendicular = Offset(-unit.y, unit.x) * headWidth
        path.moveTo((base + perpendicular).x, (base + perpendicular).y)
        path.lineTo(tip.x, tip.y)
        path.lineTo((base - perpendicular).x, (base - perpendicular).y)
        drawPath(path, arrowColor, style = Stroke(width = size.minDimension * 0.105f,
            cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
