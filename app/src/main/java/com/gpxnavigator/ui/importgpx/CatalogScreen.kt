package com.gpxnavigator.ui.importgpx

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.domain.model.SavedTrack
import com.gpxnavigator.domain.model.TrackSortOrder
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.ui.theme.navigatorButtonColors
import com.gpxnavigator.ui.theme.navigatorCardColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogScreen(state: ImportGpxUiState, listState: LazyListState,
    onBack: () -> Unit, onImport: () -> Unit, onTrack: (String) -> Unit, onRetry: () -> Unit,
    onQuery: (String) -> Unit, onFavoritesOnly: (Boolean) -> Unit, onSort: (TrackSortOrder) -> Unit,
    onFavorite: (String, Boolean) -> Unit, onClearFilters: () -> Unit) {
    var sorting by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val ready = state.catalogLoaded && !state.loading && !state.working
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(stringResource(R.string.library_title), onBack)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "library_tools") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val importButton: @Composable (Modifier) -> Unit = { modifier ->
                        Button(onClick = onImport, enabled = ready, colors = navigatorButtonColors(),
                            border = navigatorButtonBorder(), modifier = modifier.heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.import_gpx))
                        }
                    }
                    val searchField: @Composable (Modifier) -> Unit = { modifier ->
                        OutlinedTextField(value = state.query, onValueChange = onQuery,
                            modifier = modifier, singleLine = true,
                            label = { Text(stringResource(R.string.library_search)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                            trailingIcon = if (state.query.isNotEmpty()) {
                                { IconButton(onClick = { onQuery("") }) {
                                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.library_clear_search))
                                } }
                            } else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                    }
                    val fontScale = LocalDensity.current.fontScale
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth >= 600.dp * fontScale) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                importButton(Modifier.width(180.dp * fontScale))
                                searchField(Modifier.weight(1f))
                            }
                        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            importButton(Modifier.fillMaxWidth())
                            searchField(Modifier.fillMaxWidth())
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box {
                            OutlinedButton(onClick = { focus.clearFocus(); sorting = true }, enabled = ready,
                                modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(sortLabel(state.sortOrder)), Modifier.weight(1f, fill = false))
                                Icon(painterResource(R.drawable.ic_dropdown), stringResource(R.string.library_sort),
                                    Modifier.padding(start = 6.dp).size(20.dp))
                            }
                            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                                TrackSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(text = { Text(stringResource(sortLabel(order)),
                                        fontWeight = if (order == state.sortOrder) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { sorting = false; onSort(order) })
                                }
                            }
                        }
                        FilterChip(selected = state.favoritesOnly, onClick = { onFavoritesOnly(!state.favoritesOnly) },
                            modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(R.string.library_only_favorites)) },
                            leadingIcon = { Icon(painterResource(if (state.favoritesOnly) R.drawable.ic_star else R.drawable.ic_star_outline),
                                null, Modifier.size(20.dp)) })
                    }
                    if (state.catalogLoaded) Text(pluralStringResource(R.plurals.library_results,
                        state.visibleTracks.size, state.visibleTracks.size), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading || state.working || state.error != null || state.session.error != null)
                item(key = "library_feedback") { CatalogFeedback(state) }
            if (!state.catalogLoaded && !state.loading) item(key = "library_retry") {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_catalog)) }
            }
            if (state.catalogLoaded && state.visibleTracks.isEmpty()) item(key = "library_empty") {
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = navigatorCardColors()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (state.tracks.isEmpty()) R.string.saved_tracks_empty else R.string.library_no_results),
                            style = MaterialTheme.typography.bodyLarge)
                        if (state.query.isNotEmpty() || state.favoritesOnly) TextButton(onClick = onClearFilters) {
                            Text(stringResource(R.string.library_clear_filters))
                        }
                    }
                }
            }
            items(state.visibleTracks, key = { it.id }) { track ->
                OutlinedCard(onClick = { focus.clearFocus(); onTrack(track.id) }, enabled = !state.working,
                    colors = navigatorCardColors(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.session.isRunning && state.session.trackId == track.id)
                                Text(stringResource(R.string.home_navigation_active), style = MaterialTheme.typography.labelMedium)
                            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (track.activityMode == ActivityMode.COCKPIT)
                                Text(stringResource(R.string.cockpit_saved_origin), style = MaterialTheme.typography.labelMedium)
                            Text(track.totalDistanceMeters?.let { stringResource(R.string.metric_distance_value, it / 1_000) }
                                ?: stringResource(R.string.detail_unavailable), style = MaterialTheme.typography.bodyLarge)
                            track.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        FavoriteButton(track, ready) { onFavorite(track.id, it) }
                        Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.detail_open), Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun FavoriteButton(track: SavedTrack, enabled: Boolean, onChange: (Boolean) -> Unit) {
    IconToggleButton(checked = track.isFavorite, onCheckedChange = onChange, enabled = enabled,
        modifier = Modifier.size(48.dp), colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(painterResource(if (track.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline),
            stringResource(if (track.isFavorite) R.string.library_remove_favorite else R.string.library_add_favorite, track.title))
    }
}

private fun sortLabel(order: TrackSortOrder): Int = when (order) {
    TrackSortOrder.RECENT -> R.string.library_sort_recent
    TrackSortOrder.NAME -> R.string.library_sort_name
    TrackSortOrder.DISTANCE_ASC -> R.string.library_sort_shortest
    TrackSortOrder.DISTANCE_DESC -> R.string.library_sort_longest
}
