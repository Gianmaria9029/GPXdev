package com.gpxnavigator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import com.gpxnavigator.ui.theme.LocalAppTheme

@Composable
fun ThemeSelector(theme: AppTheme, onThemeChange: (AppTheme) -> Unit, saveFailed: Boolean,
    compact: Boolean = false) {
    val palette = LocalNavigatorPalette.current
    val colors = MaterialTheme.colorScheme
    val cyber = LocalAppTheme.current == AppTheme.CYBER
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!compact) Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppTheme.entries.forEachIndexed { index, option ->
                SegmentedButton(selected = theme == option, onClick = { onThemeChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppTheme.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = if (cyber) palette.followActive else colors.primaryContainer,
                        activeContentColor = if (cyber) palette.onFollowActive else colors.onPrimaryContainer,
                        activeBorderColor = if (cyber) palette.followActive else palette.selectionBorder,
                        inactiveContainerColor = colors.surface,
                        inactiveContentColor = colors.onSurface,
                        inactiveBorderColor = colors.outline),
                    modifier = Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 56.dp)) {
                    Text(stringResource(if (option == AppTheme.SOFT)
                        R.string.theme_soft else R.string.theme_cyber))
                }
            }
        }
        if (saveFailed) {
            Text(stringResource(R.string.theme_save_failed), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onThemeChange(theme) }) { Text(stringResource(R.string.theme_retry_save)) }
        }
    }
}
