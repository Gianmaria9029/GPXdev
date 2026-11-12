package com.gpxnavigator.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gpxnavigator.R
import com.gpxnavigator.ui.theme.LocalNavigatorPalette

@Composable
internal fun MapControls(followEnabled: Boolean, landscape: Boolean,
    onZoomIn: () -> Unit, onZoomOut: () -> Unit, onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier) {
    if (landscape) {
        FollowControl(followEnabled, onToggleFollow, modifier)
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            ZoomControls(onZoomIn, onZoomOut)
            FollowControl(followEnabled, onToggleFollow)
        }
    }
}

@Composable
private fun FollowControl(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalNavigatorPalette.current
    FilledTonalIconToggleButton(checked = enabled, onCheckedChange = { onToggle() },
        modifier = modifier.size(48.dp), shape = RoundedCornerShape(12.dp),
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = palette.followActive, checkedContentColor = palette.onFollowActive)) {
        Icon(painterResource(R.drawable.ic_follow_location),
            contentDescription = stringResource(if (enabled) R.string.follow_on else R.string.follow_off),
            modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onZoomOut, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_zoom_out), stringResource(R.string.zoom_out), Modifier.size(24.dp))
            }
            VerticalDivider(Modifier.height(24.dp))
            IconButton(onClick = onZoomIn, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_zoom_in), stringResource(R.string.zoom_in), Modifier.size(24.dp))
            }
        }
    }
}

