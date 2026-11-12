package com.gpxnavigator.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gpxnavigator.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ChangelogDialog(onDismiss: () -> Unit) {
    val assets = LocalResources.current.assets
    val errorMessage = stringResource(R.string.changelog_read_error)
    val content by produceState<String?>(null, assets, errorMessage) {
        value = withContext(Dispatchers.IO) {
            try {
                assets.open("CHANGELOG.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: IOException) {
                errorMessage
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.changelog_title)) },
        text = {
            val text = content
            Box(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                if (text == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    SelectionContainer {
                        Text(text, modifier = Modifier.verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_details)) }
        },
    )
}
