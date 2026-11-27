package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.aimdi.xtanative.ui.common.FlatFilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.groups_expire_day
import com.aimdi.xtanative.ui.generated.resources.groups_expire_month
import com.aimdi.xtanative.ui.generated.resources.groups_expire_never
import com.aimdi.xtanative.ui.generated.resources.groups_expire_week
import com.aimdi.xtanative.ui.generated.resources.groups_muted_hint
import com.aimdi.xtanative.ui.generated.resources.posts_mute_confirm
import com.aimdi.xtanative.ui.generated.resources.posts_mute_word_title
import java.time.Duration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How long a mute lasts, as a group's muted words offer it: for good, a day, a week, a month. */
internal object MuteExpiries {
    val choices: List<Pair<Duration?, StringResource>> = listOf<Duration?>(null, Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(30)).zip(
        listOf(Res.string.groups_expire_never, Res.string.groups_expire_day, Res.string.groups_expire_week, Res.string.groups_expire_month),
    )
}

/** The expiries as chips, [picked] marked; a tap picks one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpiryChips(picked: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MuteExpiries.choices.forEachIndexed { index, (_, label) ->
            FlatFilterChip(selected = index == picked, onClick = { onPick(index) }, label = { Text(stringResource(label)) })
        }
    }
}

/**
 * "Mute a word…" from a post's menu: the word or phrase, [initial] filled in,
 * and for how long; [onMute] gets the trimmed term and the duration (null for good).
 */
@Composable
internal fun MuteWordDialog(initial: String, onDismiss: () -> Unit, onMute: (String, Duration?) -> Unit) {
    var term by rememberSaveable { mutableStateOf(initial) }
    var expiry by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = { if (term.isNotBlank()) onMute(term.trim(), MuteExpiries.choices[expiry].first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.posts_mute_word_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.groups_muted_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).focusRequester(focus),
                )
                ExpiryChips(expiry) { expiry = it }
            }
        },
        confirmButton = { TextButton(onClick = submit, enabled = term.isNotBlank()) { Text(stringResource(Res.string.posts_mute_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
