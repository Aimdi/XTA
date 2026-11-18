package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_menu_xta
import com.aimdi.xtanative.ui.generated.resources.home_more_options
import org.jetbrains.compose.resources.stringResource

/**
 * The app's part of a network's one menu on Home (the unified plugin
 * shell): the network's name to head the plugin's own entries, and the
 * app's entries under XTA's (keep controls visible, the network's options
 * Home offers, the plugin store, XTA's settings). Home makes it; a plugin's
 * [ShellMenuButton] draws it, and while one does Home leaves its own menu
 * out, so the bar has one.
 */
@Stable
internal class ShellMenu(val network: String, val entries: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    /** How many of the plugin's [ShellMenuButton]s are drawn now. */
    var claimed by mutableIntStateOf(0)

    /** Whether the plugin draws the one menu, so Home's own stays out. */
    val drawnByPlugin: Boolean get() = claimed > 0
}

/** The app's part of the menu where a network's bar is drawn on Home; null elsewhere. */
internal val LocalShellMenu = staticCompositionLocalOf<ShellMenu?> { null }

/**
 * A plugin's one menu (⋮): the plugin's own [items] and, where the plugin
 * is a timeline on Home, the app's after them under XTA's name, the
 * plugin's headed by the network's, so the bar keeps one menu instead of
 * the plugin's and Home's side by side. Off Home it is the plugin's menu
 * alone. [items] get what closes the menu; [attention] puts a dot on the
 * button.
 */
@Composable
fun ShellMenuButton(attention: Boolean = false, items: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val shell = LocalShellMenu.current
    if (shell != null) {
        DisposableEffect(shell) {
            shell.claimed++
            onDispose { shell.claimed-- }
        }
    }
    var open by remember { mutableStateOf(false) }
    val close = { open = false }
    Box {
        IconButton(onClick = { open = true }) {
            val more = stringResource(Res.string.home_more_options)
            if (attention) BadgedBox(badge = { Badge() }) { Icon(Icons.Filled.MoreVert, more) } else Icon(Icons.Filled.MoreVert, more)
        }
        DropdownMenu(expanded = open, onDismissRequest = close) {
            if (shell != null) MenuHeading(shell.network)
            items(close)
            if (shell != null) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                MenuHeading(stringResource(Res.string.home_menu_xta))
                shell.entries(this, close)
            }
        }
    }
}

/** A small grey heading over a part of a menu. */
@Composable
internal fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp).semantics { heading() },
    )
}
