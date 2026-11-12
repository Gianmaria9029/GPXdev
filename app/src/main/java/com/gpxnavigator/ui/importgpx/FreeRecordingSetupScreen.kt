package com.gpxnavigator.ui.importgpx

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gpxnavigator.ui.theme.navigatorCardColors
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.MAX_RECORDING_NAME_LENGTH
import com.gpxnavigator.domain.model.RecordingNameError
import com.gpxnavigator.domain.model.normalizedRecordingName
import com.gpxnavigator.domain.model.recordingNameError
import com.gpxnavigator.ui.theme.LocalNavigatorPalette

@Composable
internal fun FreeRecordingSetupScreen(name: String, onNameChange: (String) -> Unit,
    onBack: () -> Unit, onStart: () -> Unit, enabled: Boolean,
    awaitingPermission: Boolean, error: String?) {
    val palette = LocalNavigatorPalette.current
    val focus = LocalFocusManager.current
    val nameError = recordingNameError(name)
    val available = enabled && !awaitingPermission
    val header: @Composable () -> Unit = {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_home), null, Modifier.size(22.dp))
            Text(stringResource(R.string.recording_setup_back), Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge)
        }
    }
    val form: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recording_setup_title), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.recording_setup_intro), style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiaryContainer),
                colors = navigatorCardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(), enabled = available, singleLine = true,
                        label = { Text(stringResource(R.string.recording_setup_name)) },
                        placeholder = { Text(stringResource(R.string.recording_setup_name_example)) },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        isError = nameError != null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        supportingText = {
                            Text(when (nameError) {
                                RecordingNameError.TOO_LONG -> pluralStringResource(R.plurals.recording_setup_name_too_long,
                                    MAX_RECORDING_NAME_LENGTH, MAX_RECORDING_NAME_LENGTH)
                                RecordingNameError.INVALID_CHARACTERS -> stringResource(R.string.recording_setup_name_invalid)
                                null -> stringResource(if (normalizedRecordingName(name) == null)
                                    R.string.recording_setup_name_automatic else R.string.recording_setup_name_hint)
                            }, style = MaterialTheme.typography.bodyMedium)
                        })
                }
            }
        }
    }
    val summary: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.recording_setup_summary), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            RecordingDataCard(R.drawable.ic_recording_route, R.string.recording_setup_route_title, R.string.recording_setup_route_detail)
            RecordingDataCard(R.drawable.ic_recording_speed, R.string.recording_setup_speed_title, R.string.recording_setup_speed_detail)
            RecordingDataCard(R.drawable.ic_recording_altitude, R.string.recording_setup_altitude_title, R.string.recording_setup_altitude_detail)
            RecordingDataCard(R.drawable.ic_recording_time, R.string.recording_setup_time_title, R.string.recording_setup_time_detail)
            Text(stringResource(R.string.recording_setup_availability), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val actions: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            Button(onClick = { focus.clearFocus(); onStart() }, enabled = available && nameError == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), border = navigatorButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.action, contentColor = palette.onAction)) {
                Icon(painterResource(R.drawable.ic_track_start), null, Modifier.size(24.dp))
                Text(stringResource(if (awaitingPermission) R.string.recording_setup_permissions else R.string.recording_setup_start),
                    Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val pinnedActions = maxHeight.value >= 320f * scale
        val twoPanes = maxWidth.value >= 700f * scale && maxHeight.value >= 280f * scale
        if (!pinnedActions) {
            // Includes the button in the scroll area when the keyboard or large fonts leave little room.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) { header(); form(); summary(); actions() }
        } else Column(Modifier.fillMaxSize()) {
            header()
            if (twoPanes) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) { form() }
                Column(Modifier.weight(1.15f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) { summary() }
            } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) { form(); summary() }
            actions()
        }
    }
}

@Composable
private fun RecordingDataCard(icon: Int, title: Int, detail: Int) {
    val palette = LocalNavigatorPalette.current
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = navigatorCardColors()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = palette.recordAction, contentColor = palette.onRecordAction) {
                Icon(painterResource(icon), null, Modifier.padding(10.dp).size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(detail), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
