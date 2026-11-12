package com.gpxnavigator.ui.cockpit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.recordingNameError
import com.gpxnavigator.ui.theme.navigatorButtonBorder
import com.gpxnavigator.ui.theme.navigatorButtonColors

@Composable
internal fun CockpitSetupScreen(name: String, onNameChange: (String) -> Unit, onBack: () -> Unit,
    onStart: () -> Unit, enabled: Boolean, awaitingPermission: Boolean, error: String?) {
    val focus = LocalFocusManager.current
    val nameError = recordingNameError(name)
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        val showIntroduction = maxHeight >= 360.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text(stringResource(R.string.home_button)) }
                Text(stringResource(R.string.cockpit_setup_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                if (showIntroduction) Text(stringResource(R.string.cockpit_setup_intro),
                    Modifier.padding(bottom = 20.dp), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(name, onNameChange, Modifier.fillMaxWidth(),
                    enabled = enabled && !awaitingPermission, singleLine = true,
                    label = { Text(stringResource(R.string.recording_setup_name)) },
                    isError = nameError != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    supportingText = { Text(stringResource(if (nameError != null) R.string.cockpit_invalid_name
                        else R.string.cockpit_auto_recording), maxLines = 2) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2) }
            }
            Button(onClick = { focus.clearFocus(); onStart() }, enabled = enabled && !awaitingPermission && nameError == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = navigatorButtonColors(), border = navigatorButtonBorder()) {
                Text(stringResource(if (awaitingPermission) R.string.recording_setup_permissions else R.string.cockpit_start))
            }
        }
    }
}
