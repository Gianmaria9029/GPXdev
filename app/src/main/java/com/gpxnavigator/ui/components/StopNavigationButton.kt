package com.gpxnavigator.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import com.gpxnavigator.R
import com.gpxnavigator.ui.theme.LocalNavigatorPalette
import com.gpxnavigator.ui.theme.navigatorButtonBorder

@Composable
fun StopNavigationButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    compact: Boolean = false, inHome: Boolean = false,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge) {
    val palette = LocalNavigatorPalette.current
    Button(onClick = onClick, modifier = if (compact) modifier.size(48.dp) else modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(8.dp), border = if (inHome) navigatorButtonBorder() else null,
        contentPadding = if (compact) PaddingValues(0.dp) else PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (inHome) palette.homeStopAction else palette.stopAction,
            contentColor = if (inHome) palette.onHomeStopAction else palette.onStopAction)) {
        Icon(painterResource(R.drawable.ic_stop_navigation),
            contentDescription = if (compact) stringResource(R.string.stop_navigation) else null,
            modifier = Modifier.size(if (compact) 18.dp else 14.dp))
        if (!compact) {
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.stop_navigation), style = labelStyle, maxLines = 1)
        }
    }
}
