package com.aimdi.xtanative.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.KeyedSheet
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.rememberSheetState
import com.aimdi.xtanative.ui.feed.MutedWordList
import com.aimdi.xtanative.ui.feed.MutedWordsActions
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_expire_day
import com.aimdi.xtanative.ui.generated.resources.groups_expire_month
import com.aimdi.xtanative.ui.generated.resources.groups_expire_never
import com.aimdi.xtanative.ui.generated.resources.groups_expire_week
import com.aimdi.xtanative.ui.generated.resources.groups_filter_fold
import com.aimdi.xtanative.ui.generated.resources.groups_filter_hide
import com.aimdi.xtanative.ui.generated.resources.groups_filter_timed
import com.aimdi.xtanative.ui.generated.resources.groups_muted_hint
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.stringResource

/**
 * A list of muted words: a field to add some, and a chip for each, whose
 * options set what it does and for how long. A group's custom settings and
 * Settings › Posts both show it, each over its own list ([actions]).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun MutedWords(keywords: List<MutedKeyword>, actions: MutedWordsActions) {
    var typed by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MutedKeyword?>(null) }
    val add = {
        actions.mute(typed)
        typed = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.groups_muted_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { add() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
            modifier = Modifier.weight(1f),
        )
        IconTip(stringResource(Res.string.groups_muted_hint)) { IconButton(onClick = add) { Icon(Icons.Filled.Add, stringResource(Res.string.groups_muted_hint), tint = MaterialTheme.colorScheme.primary) } }
    }
    if (keywords.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keywords.forEach { keyword ->
                val action = stringResource(if (keyword.action == KeywordAction.FOLD) Res.string.groups_filter_fold else Res.string.groups_filter_hide)
                val label = listOfNotNull(keyword.term, action, keyword.until?.let { stringResource(Res.string.groups_filter_timed) }).joinToString(" · ")
                InputChip(
                    selected = false,
                    onClick = { editing = keyword },
                    label = { TipText(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    avatar = {
                        Icon(
                            if (keyword.action == KeywordAction.FOLD) GroupIcons.UnfoldMore else GroupIcons.VisibilityOffOutlined,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp).clickable { actions.unmute(keyword.term) })
                    },
                )
            }
        }
    }
    editing?.let { keyword ->
        KeyedSheet(sheetState = rememberSheetState(), onDismissRequest = { editing = null }, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(bottom = 16.dp)) {
                OptionRow(GroupIcons.VisibilityOffOutlined, stringResource(Res.string.groups_filter_hide)) {
                    editing = null
                    actions.setKeywordAction(keyword.term, KeywordAction.HIDE)
                }
                OptionRow(GroupIcons.UnfoldMore, stringResource(Res.string.groups_filter_fold)) {
                    editing = null
                    actions.setKeywordAction(keyword.term, KeywordAction.FOLD)
                }
                HorizontalDivider()
                val expiries = listOf(Res.string.groups_expire_never, Res.string.groups_expire_day, Res.string.groups_expire_week, Res.string.groups_expire_month)
                MutedWordList.EXPIRIES.zip(expiries).forEach { (duration, label) ->
                    OptionRow(null, stringResource(label)) {
                        editing = null
                        actions.setKeywordExpiry(keyword.term, duration)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(16.dp))
        }
        Text(label)
    }
}
