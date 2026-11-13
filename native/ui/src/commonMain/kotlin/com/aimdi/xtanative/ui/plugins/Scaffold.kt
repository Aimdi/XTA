package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import org.jetbrains.compose.resources.stringResource

/**
 * A screen as the app's own are drawn: [title] in a bar on the theme's
 * background, a back arrow when there is [onBack], [actions] at its end, and
 * [content] below, given the padding that keeps it clear of the bar.
 */
@Composable
fun PluginScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = PluginScaffold({ Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onBack, modifier, actions, content)

/**
 * [PluginScaffold] with a title of the plugin's own drawing, where XTA's bar
 * holds more than words: a community's picture before its name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScaffold(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}
