package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_choose_pages
import com.aimdi.xtanative.ui.generated.resources.home_missing_page
import com.aimdi.xtanative.ui.generated.resources.home_two_pages_required
import com.aimdi.xtanative.ui.generated.resources.home_unable_to_load_pages
import com.aimdi.xtanative.ui.generated.resources.problem_oops
import com.aimdi.xtanative.ui.generated.resources.settings_reset_home_pages
import org.jetbrains.compose.resources.stringResource

/**
 * A page on Home's bar that has nothing to show (XTA's MissingScreen): its
 * network's plugin could not be started, or gives no timeline. 🧨, and
 * Choose pages, which opens Settings › Home.
 */
@Composable
internal fun MissingPage(onChoose: () -> Unit) {
    ErrorColumn {
        Text("🧨", fontSize = 36.sp, modifier = Modifier.padding(bottom = 16.dp))
        Text(stringResource(Res.string.home_missing_page), fontSize = 18.sp, textAlign = TextAlign.Center)
        Hint(stringResource(Res.string.home_two_pages_required))
        ElevatedButton(onClick = onChoose, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(Res.string.home_choose_pages)) }
    }
}

/**
 * Home's pages could not be built from the settings (XTA's home error): what
 * went wrong, and Reset pages to default.
 */
@Composable
internal fun PagesFailed(error: Throwable, onReset: () -> Unit) {
    ErrorColumn {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp).size(36.dp))
        Text(stringResource(Res.string.problem_oops), fontSize = 18.sp, textAlign = TextAlign.Center)
        Hint(stringResource(Res.string.home_unable_to_load_pages))
        Hint(error.toString())
        ElevatedButton(onClick = onReset, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(Res.string.settings_reset_home_pages)) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
}

/** XTA's ErrorLayout: centred while it fits, scrolling once larger text makes it taller than the screen. */
@Composable
private fun ErrorColumn(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = height).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}
