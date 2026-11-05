package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.aimdi.xtanative.ui.generated.resources.problem_oops
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's FullPageErrorWidget for what is no read failure: its mark, "Oops!
 * Something went wrong", [prefix] saying where it broke ("Something broke in
 * XTA."), the [error] and its [stack], to select and copy, and [action]'s
 * button when there is one.
 */
@Composable
fun FullPageError(prefix: String, error: String, stack: String?, action: Pair<String, () -> Unit>? = null) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp).size(36.dp))
        Text(stringResource(Res.string.problem_oops), fontSize = 18.sp, textAlign = TextAlign.Center)
        Text(prefix, color = hint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        SelectionContainer(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Column {
                Text(error, color = hint, modifier = Modifier.padding(top = 12.dp))
                if (!stack.isNullOrBlank()) Text(stack, color = hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (action != null) ElevatedButton(onClick = action.second, modifier = Modifier.padding(top = 12.dp)) { Text(action.first) }
    }
}
