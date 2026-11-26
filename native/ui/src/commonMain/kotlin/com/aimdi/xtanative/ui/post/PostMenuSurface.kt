package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.keys_ctrl
import com.aimdi.xtanative.ui.generated.resources.keys_shift
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where a post's ⋯ menu shows its entries ([content]): on a phone, a sheet
 * rising from the foot of the screen; on the desktop, a menu beside the
 * pointer, as a desktop opens a thing's menu. Both close at [onDismiss]: a
 * swipe or a tap outside, a click outside or Escape.
 */
@Composable
internal expect fun PostMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)

/**
 * A Material drop-down menu's rows ([content]) as the desktop's keys walk
 * them, as [PostMenuSurface]'s: opened with the keyboard (Enter on its
 * button), the first row has the focus at once; Home and End (Page Up and
 * Page Down) go to the first row and the last, beside the ↑ and ↓ Material
 * already takes. As they are on Android.
 */
@Composable
internal expect fun ColumnScope.KeyedMenuRows(content: @Composable ColumnScope.() -> Unit)

/**
 * How a menu's row is laid out: a phone sheet's rows are tall for a finger;
 * the desktop's pointer menu ([PostMenuSurface]) lays its own out denser, as
 * a desktop's menus are, with lines between its groups of entries
 * ([dividers]) and the keys that do an entry at its end ([hints]).
 */
internal data class MenuRowMetrics(
    val minHeight: Dp,
    val padding: Dp,
    val gap: Dp,
    val iconSize: Dp,
    val verticalPadding: Dp = 0.dp,
    val dividers: Boolean = false,
    val hints: Boolean = false,
) {
    companion object {
        val Sheet = MenuRowMetrics(minHeight = 52.dp, padding = 24.dp, gap = 24.dp, iconSize = 24.dp)
        val Pointer = MenuRowMetrics(minHeight = 36.dp, padding = 16.dp, gap = 12.dp, iconSize = 20.dp, verticalPadding = 6.dp, dividers = true, hints = true)
    }
}

/** The [MenuRowMetrics] the rows below lay themselves out with; a sheet's unless a pointer menu says otherwise. */
internal val LocalMenuRowMetrics = staticCompositionLocalOf { MenuRowMetrics.Sheet }

/**
 * The keys that do a menu entry, as the desktop's list of keys (F1) names
 * them: the keys held ([held], by name: Ctrl is Strg in German) and the one
 * pressed with them ([key], as its sign).
 */
internal data class MenuHint(val held: List<StringResource>, val key: String) {
    companion object {
        /** b: saves the post on this device, or unsaves it. */
        val SAVE = MenuHint(emptyList(), "B")

        /** Ctrl+Shift+C: copies the post's link, as Share link does on the desktop, which has no share sheet. */
        val COPY_LINK = MenuHint(listOf(Res.string.keys_ctrl, Res.string.keys_shift), "C")
    }
}

/** [hint] as a menu shows it: its keys joined by +. */
@Composable
internal fun hintText(hint: MenuHint): String = (hint.held.map { stringResource(it) } + hint.key).joinToString("+")

/** A line between two groups of a pointer menu's entries ([MenuRowMetrics.dividers]); nothing in a sheet. */
@Composable
internal fun MenuGroupDivider() {
    if (!LocalMenuRowMetrics.current.dividers) return
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * A menu entry laid out as the plugins' [com.aimdi.xtanative.ui.plugins.PluginMenuRow]
 * lays one out, with the keys that do it ([hint]) at its end in a pointer
 * menu ([MenuRowMetrics.hints]).
 */
@Composable
internal fun HintedMenuRow(icon: ImageVector, label: String, hint: MenuHint, onClick: () -> Unit) {
    val metrics = LocalMenuRowMetrics.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = metrics.minHeight).clickable(onClick = onClick).padding(horizontal = metrics.padding, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(metrics.iconSize))
        Text(label, modifier = Modifier.padding(start = metrics.gap).weight(1f))
        if (metrics.hints) {
            Text(
                hintText(hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = HINT_GAP),
            )
        }
    }
}

/** The room between an entry's label and its keys. */
private val HINT_GAP = 24.dp
