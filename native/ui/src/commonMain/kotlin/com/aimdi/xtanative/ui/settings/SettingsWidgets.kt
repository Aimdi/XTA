package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_back
import com.aimdi.xtanative.ui.generated.resources.settings_cancel
import com.aimdi.xtanative.ui.generated.resources.settings_save
import org.jetbrains.compose.resources.stringResource

/** A settings page: its title, Back, and what it holds, on the page's own background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = floating,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.settings_back)) }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}

/** The rows of a page, scrolling. */
@Composable
internal fun SettingsList(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), content = content)
}

/** A group's heading, and what it is about. */
@Composable
internal fun SettingsHeader(title: String?, description: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * The row the settings search opened the page for stands out and scrolls
 * into view, as XTA's SettingsControlTarget does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.focusedSetting(focused: Boolean): Modifier {
    if (!focused) return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    return bringIntoViewRequester(requester).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
}

@Composable
private fun RowText(title: String, description: String?, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!description.isNullOrEmpty()) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A switch, as XTA's PrefSwitch: the whole row toggles it. */
@Composable
internal fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    focused: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().focusedSetting(focused)
            .selectable(selected = checked, enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, description, enabled, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A check box row, as XTA's PrefCheckbox. */
@Composable
internal fun CheckRow(title: String, description: String? = null, checked: Boolean, enabled: Boolean = true, focused: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusedSetting(focused)
            .selectable(selected = checked, enabled = enabled, role = Role.Checkbox) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, description, enabled, Modifier.weight(1f).padding(end = 12.dp))
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A row that opens something: a section, a screen, a dialog. */
@Composable
internal fun NavigationRow(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    chevron: Boolean = true,
    destructive: Boolean = false,
    focused: Boolean = false,
    action: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).focusedSetting(focused).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    destructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (!description.isNullOrEmpty()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A line of information: a title and its value. */
@Composable
internal fun ValueRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A choice among a few values, as XTA's PrefDropdown: the row shows the one
 * chosen and opens the list.
 */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    description: String? = null,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    enabled: Boolean = true,
    focused: Boolean = false,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().focusedSetting(focused).clickable(enabled = enabled) { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, description, enabled, Modifier.weight(1f).padding(end = 12.dp))
        Text(label(selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        ChoiceDialog(title, options, selected, label, onDismiss = { open = false }) { choice ->
            open = false
            onSelect(choice)
        }
    }
}

/** The list a choice opens: each value with a radio button. */
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (header != null) {
                    Text(header, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            description?.invoke(option)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) } },
    )
}

/** A text the reader types in a dialog, saved only with Save, as XTA's PrefDialogButton. */
@Composable
internal fun TextFieldDialog(
    title: String,
    initial: String,
    hint: String? = null,
    secret: Boolean = false,
    url: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = hint?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (url) KeyboardType.Uri else if (secret) KeyboardType.Password else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(Res.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) } },
    )
}

/** A row that edits a text in [TextFieldDialog]; [shown] is what the row says of the value. */
@Composable
internal fun TextRow(
    title: String,
    description: String?,
    value: String,
    hint: String? = null,
    secret: Boolean = false,
    url: Boolean = false,
    focused: Boolean = false,
    enabled: Boolean = true,
    onSave: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    NavigationRow(title, description, enabled = enabled, chevron = false, focused = focused) { open = true }
    if (open) {
        TextFieldDialog(title, value, hint, secret, url, onDismiss = { open = false }) {
            open = false
            onSave(it)
        }
    }
}

/** A slider, as XTA's PrefSlider: the value is saved once the finger lets go. */
@Composable
internal fun SliderRow(
    title: String,
    description: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String,
    focused: Boolean = false,
    onDone: (Float) -> Unit,
) {
    var current by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().focusedSetting(focused).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, description, true, Modifier.weight(1f).padding(end = 12.dp))
            Text(label(current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        androidx.compose.material3.Slider(
            value = current,
            onValueChange = { current = it },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onDone(current) },
        )
    }
}

/** A question asked before something that cannot be undone. */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, dismiss: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/** Between groups of rows. */
@Composable
internal fun SettingsDivider() {
    Box(Modifier.padding(top = 8.dp)) { Hairline() }
}

/** Space between widgets inside a padded form. */
internal val FormSpacing = Arrangement.spacedBy(12.dp)
