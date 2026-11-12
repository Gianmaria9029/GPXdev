package com.gpxnavigator.ui.importgpx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.SavedTrack
import com.gpxnavigator.domain.model.TrackNameError
import com.gpxnavigator.domain.model.trackNameError

@Composable
internal fun RenameTrackDialog(track: SavedTrack, busy: Boolean, allowed: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(track.id) { mutableStateOf(track.title) }
    val validation = trackNameError(name)
    val canSave = !busy && allowed && validation == null
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.library_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.library_rename_hint))
                OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy && allowed,
                    singleLine = true, label = { Text(stringResource(R.string.library_name)) }, isError = validation != null,
                    supportingText = { Text(stringResource(when (validation) {
                        TrackNameError.EMPTY -> R.string.library_name_empty
                        TrackNameError.TOO_LONG -> R.string.library_name_long
                        TrackNameError.INVALID_CHARACTERS -> R.string.library_name_invalid
                        null -> R.string.library_name_limit
                    })) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(name) }))
                if (!allowed) Text(stringResource(R.string.library_rename_blocked), color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = canSave) {
            Text(stringResource(R.string.library_save_name))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}

internal fun canRenameTrack(state: ImportGpxUiState, id: String): Boolean =
    state.session.status != com.gpxnavigator.navigation.SessionStatus.RESTORING &&
        (state.session.trackId != id || state.session.status == com.gpxnavigator.navigation.SessionStatus.IDLE)
