package com.gpxnavigator.ui.importgpx

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gpxnavigator.BuildConfig
import com.gpxnavigator.R

internal fun developerWebsiteIntent(): Intent = Intent(Intent.ACTION_VIEW, "https://gianmaria.dev".toUri()).apply {
    addCategory(Intent.CATEGORY_BROWSABLE)
    // Resolve a browser rather than an application claiming this web domain.
    selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
}

@Composable
internal fun DashboardFooter(onChangelog: () -> Unit) {
    val context = LocalContext.current
    val creditDescription = stringResource(R.string.developer_credit_description)
    val versionDescription = stringResource(R.string.open_changelog, BuildConfig.VERSION_NAME)
    val footerColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = {
            try {
                context.startActivity(developerWebsiteIntent())
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.browser_unavailable, Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.browser_unavailable, Toast.LENGTH_LONG).show()
            }
        }, colors = footerColors, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = creditDescription }) {
            Text(stringResource(R.string.developer_credit), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onChangelog, colors = footerColors,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = versionDescription }) {
            Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
