package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.ColumnScrollbar
import com.aimdi.xtanative.ui.common.bleed
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.common.dialogDefault
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings_cancel
import com.aimdi.xtanative.ui.generated.resources.settings_no_results
import org.jetbrains.compose.resources.stringResource

/** How a choice's list is laid out: its height, and when it has a filter. Pure, so the rules are tested. */
internal object ChoiceList {
    /** The most of the window's height the dialog takes: a long list (29 languages) scrolls in it. */
    const val MAX_HEIGHT_FRACTION = 0.7f

    /** More options than this, and a field over them filters them by name. */
    const val FILTER_FROM = 12

    fun filtered(count: Int): Boolean = count > FILTER_FROM

    /** The dialog's greatest height for a window [windowHeight] tall. */
    fun maxHeight(windowHeight: Dp): Dp = windowHeight * MAX_HEIGHT_FRACTION

    /** The indices of [labels] that [query] matches: in any case, anywhere in the name; all of them for a blank query. */
    fun matching(labels: List<String>, query: String): List<Int> {
        val wanted = query.trim()
        return labels.indices.filter { wanted.isEmpty() || labels[it].contains(wanted, ignoreCase = true) }
    }

    /** The option Enter in the filter picks: the one match left of [shown] (indices), none while several or none are. */
    fun picked(shown: List<Int>): Int? = shown.singleOrNull()
}

/** How far the rows reach past the dialog's margin on either side, so their lit shape sits round the radio button. */
private val ROW_BLEED = 16.dp

/**
 * The list a choice opens: each value with a radio button, at most
 * [ChoiceList.MAX_HEIGHT_FRACTION] of the window tall with a scrollbar, and a
 * filter over a long list ([ChoiceList.filtered]).
 */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    description: (@Composable (T) -> String?)? = null,
    header: String? = null,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val labels = options.map { label(it) }
    var query by remember { mutableStateOf("") }
    val shown = ChoiceList.matching(labels, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = if (windowHeight > 0.dp) Modifier.heightIn(max = ChoiceList.maxHeight(windowHeight)) else Modifier,
        title = { Text(title) },
        text = {
            Column {
                if (ChoiceList.filtered(options.size)) {
                    // On the desktop the keys go to the filter at once, so typing a name finds it; a phone keeps its keyboard down.
                    val keys = if (isDesktop) Modifier.dialogDefault() else Modifier
                    // Enter picks the one option the filter has left.
                    val pick = { ChoiceList.picked(shown)?.let { onSelect(options[it]) } }
                    SearchField(query, { query = it }, stringResource(Res.string.search), Modifier.fillMaxWidth().padding(bottom = 8.dp).then(keys), onSearch = { pick() })
                }
                val scroll = rememberScrollState()
                // The scrollbar in the margin the rows reach into, clear of their lit shapes and their text.
                Box(Modifier.bleed(ROW_BLEED)) {
                    Column(Modifier.verticalScroll(scroll)) {
                        if (header != null) {
                            Text(
                                header,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = ROW_BLEED, end = ROW_BLEED, bottom = 8.dp),
                            )
                        }
                        shown.forEach { index -> ChoiceOption(options[index], labels[index], selected, description, onSelect) }
                        if (shown.isEmpty()) {
                            Text(
                                stringResource(Res.string.settings_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = ROW_BLEED, vertical = 12.dp),
                            )
                        }
                    }
                    // As tall as the rows, not the room: the dialog shrinks round a filter's few matches (at most 70% still).
                    Box(Modifier.matchParentSize()) { ColumnScrollbar(scroll, Modifier.align(Alignment.TopEnd)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) } },
    )
}

@Composable
private fun <T> ChoiceOption(option: T, label: String, selected: T?, description: (@Composable (T) -> String?)?, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
            .padding(horizontal = ROW_BLEED, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.invoke(option)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

