package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruQueryToken
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * The search field (booru_search_field.dart): finished tags as chips, then
 * the tag being typed. Space or Enter finish a tag, backspace in an empty
 * field takes the last one off, a tapped chip goes back into the field to be
 * edited. [field] is what is typed, which picking a suggestion also sets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagField(search: BooruSearch, state: BooruSearchState, field: MutableState<String>, autofocus: Boolean, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var value by remember { mutableStateOf(TextFieldValue(field.value, TextRange(field.value.length))) }
    LaunchedEffect(field.value) { if (field.value != value.text) value = TextFieldValue(field.value, TextRange(field.value.length)) }
    LaunchedEffect(Unit) { if (autofocus) runCatching { focus.requestFocus() } }
    val run = {
        focusManager.clearFocus()
        field.value = ""
        search.search()
    }
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                Modifier.weight(1f).heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                state.tags.forEach { tag -> FieldChip(tag, state, onEdit = { field.value = search.reopen(tag); runCatching { focus.requestFocus() } }, onRemove = { search.removeTag(tag) }) }
                BasicTextField(
                    value = value,
                    onValueChange = { next ->
                        val rest = search.type(next.text)
                        value = if (rest == next.text) next else TextFieldValue(rest, TextRange(rest.length))
                        field.value = value.text
                    },
                    modifier = Modifier
                        .let { if (state.tags.isEmpty()) it.fillMaxWidth() else it.widthIn(min = 112.dp) }
                        .focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) search.edit() }
                        .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && event.key == Key.Backspace && value.text.isEmpty() && search.removeLast() },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                    decorationBox = { inner ->
                        Box(Modifier.padding(horizontal = 4.dp, vertical = 14.dp)) {
                            if (value.text.isEmpty()) Text(pluginString(Strings.add_tag_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                    },
                )
            }
            IconButton(onClick = run) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
        }
    }
}

/** A finished tag in the field, coloured by the kind its suggestion said: a tap edits it, its cross removes it. */
@Composable
private fun FieldChip(tag: String, state: BooruSearchState, onEdit: () -> Unit, onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onEdit,
        label = { Text(tokenText(tag, state.categories[BooruQueryToken.parse(tag).value])) },
        trailingIcon = {
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, pluginString(Strings.delete), modifier = Modifier.size(18.dp))
            }
        },
    )
}
