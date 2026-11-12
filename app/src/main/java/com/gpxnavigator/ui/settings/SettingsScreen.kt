package com.gpxnavigator.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpxnavigator.ui.theme.navigatorCardColors
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.domain.model.ScreenOrientation
import com.gpxnavigator.ui.components.ThemeSelector
import com.gpxnavigator.ui.importgpx.PageHeader
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.ui.theme.navigatorButtonColors

@Composable
internal fun SettingsScreen(theme: AppTheme, onThemeChange: (AppTheme) -> Unit, saveFailed: Boolean,
    onBack: () -> Unit, footer: @Composable () -> Unit,
    displayModel: DisplaySettingsViewModel = viewModel(), mapModel: OfflineMapSettingsViewModel = viewModel()) {
    val display by displayModel.state.collectAsStateWithLifecycle()
    val map by mapModel.state.collectAsStateWithLifecycle()
    var pickingMap by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickingMap = false
        if (uri != null) mapModel.importMap(uri)
    }
    LaunchedEffect(mapModel) { mapModel.reload() }
    SettingsContent(theme, onThemeChange, saveFailed, onBack, footer, display, map, pickingMap,
        onOrientation = displayModel::setOrientation,
        onRetrySave = displayModel::retrySave, onReloadMap = mapModel::reload,
        onImportMap = { pickingMap = true; picker.launch(arrayOf("*/*")) })
}

@Composable
internal fun SettingsContent(theme: AppTheme, onThemeChange: (AppTheme) -> Unit, saveFailed: Boolean,
    onBack: () -> Unit, footer: @Composable () -> Unit, display: DisplaySettingsUiState,
    map: OfflineMapSettingsState, pickingMap: Boolean,
    onOrientation: (ScreenOrientation) -> Unit, onRetrySave: () -> Unit,
    onReloadMap: () -> Unit, onImportMap: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(stringResource(R.string.settings_title), onBack)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { ThemeSelector(theme, onThemeChange, saveFailed) }
            item {
                SettingsSection(R.string.settings_maps) {
                    val selected = map.map
                    if (selected != null) {
                        Text(stringResource(R.string.settings_active_map), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(Formatter.formatFileSize(LocalContext.current, selected.sizeBytes), style = MaterialTheme.typography.bodyLarge)
                    } else if (!map.busy) Text(stringResource(R.string.settings_no_map), style = MaterialTheme.typography.bodyLarge)
                    if (map.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(if (map.importing) R.string.settings_map_importing else R.string.settings_map_loading))
                    }
                    Text(stringResource(R.string.settings_map_hint), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onImportMap, enabled = !map.busy && !pickingMap,
                        colors = navigatorButtonColors(), border = navigatorButtonBorder(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.settings_import_map))
                    }
                    if (map.imported) Text(stringResource(R.string.settings_map_imported), style = MaterialTheme.typography.bodyMedium)
                    map.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onReloadMap, enabled = !map.busy) { Text(stringResource(R.string.detail_retry)) }
                    }
                }
            }
            item {
                SettingsSection(R.string.settings_orientation) {
                    Text(stringResource(R.string.settings_orientation_hint), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.selectableGroup()) {
                        ScreenOrientation.entries.forEach { option ->
                            val label = when (option) {
                                ScreenOrientation.AUTOMATIC -> R.string.settings_orientation_auto
                                ScreenOrientation.PORTRAIT -> R.string.settings_orientation_portrait
                                ScreenOrientation.LANDSCAPE -> R.string.settings_orientation_landscape
                            }
                            Row(Modifier.fillMaxWidth().selectable(selected = display.settings?.orientation == option,
                                enabled = display.settings != null, role = Role.RadioButton,
                                onClick = { onOrientation(option) }).heightIn(min = 56.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = display.settings?.orientation == option, onClick = null,
                                    enabled = display.settings != null)
                                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            if (display.saveFailed) item {
                Text(stringResource(R.string.settings_save_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetrySave) { Text(stringResource(R.string.theme_retry_save)) }
            }
            item { footer() }
        }
    }
}

@Composable
private fun SettingsSection(title: Int, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = navigatorCardColors()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
