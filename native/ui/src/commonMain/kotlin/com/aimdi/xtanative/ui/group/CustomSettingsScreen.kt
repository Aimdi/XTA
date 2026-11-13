package com.aimdi.xtanative.ui.group

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.LanguageAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.groups_content_filter
import com.aimdi.xtanative.ui.generated.resources.groups_content_filter_description
import com.aimdi.xtanative.ui.generated.resources.groups_custom
import com.aimdi.xtanative.ui.generated.resources.groups_engagement
import com.aimdi.xtanative.ui.generated.resources.groups_engagement_description
import com.aimdi.xtanative.ui.generated.resources.groups_expire_day
import com.aimdi.xtanative.ui.generated.resources.groups_expire_month
import com.aimdi.xtanative.ui.generated.resources.groups_expire_never
import com.aimdi.xtanative.ui.generated.resources.groups_expire_week
import com.aimdi.xtanative.ui.generated.resources.groups_filter_fold
import com.aimdi.xtanative.ui.generated.resources.groups_filter_hide
import com.aimdi.xtanative.ui.generated.resources.groups_filter_timed
import com.aimdi.xtanative.ui.generated.resources.groups_language_action
import com.aimdi.xtanative.ui.generated.resources.groups_language_filter
import com.aimdi.xtanative.ui.generated.resources.groups_language_filter_description
import com.aimdi.xtanative.ui.generated.resources.groups_language_fold
import com.aimdi.xtanative.ui.generated.resources.groups_language_hide
import com.aimdi.xtanative.ui.generated.resources.groups_language_off
import com.aimdi.xtanative.ui.generated.resources.groups_languages
import com.aimdi.xtanative.ui.generated.resources.groups_min_likes
import com.aimdi.xtanative.ui.generated.resources.groups_min_reposts
import com.aimdi.xtanative.ui.generated.resources.groups_muted
import com.aimdi.xtanative.ui.generated.resources.groups_muted_description
import com.aimdi.xtanative.ui.generated.resources.groups_muted_hint
import com.aimdi.xtanative.ui.generated.resources.groups_quiet_account
import com.aimdi.xtanative.ui.generated.resources.groups_quiet_account_description
import com.aimdi.xtanative.ui.generated.resources.groups_quiet_account_off
import com.aimdi.xtanative.ui.generated.resources.groups_threshold_off
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.PostCapDialog
import com.aimdi.xtanative.ui.subscriptions.SubscriptionList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A group's custom feed mode in full (group_custom_settings.dart): its content
 * filter, like and repost floors and muted words, then the reader's language
 * filter and each member's post cap, which the feed applies too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSettingsScreen(state: GroupFeedUi, actions: GroupSettingsActions, onBack: () -> Unit) {
    var capping by remember { mutableStateOf<FollowedAccount?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.groups_custom)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "content") {
                Section(GroupIcons.FilterAltOutlined, Res.string.groups_content_filter, Res.string.groups_content_filter_description) {
                    ContentFilterRow(state.settings.rules.contentFilter, actions::setContentFilter)
                }
            }
            item(key = "engagement") {
                Section(GroupIcons.TrendingUp, Res.string.groups_engagement, Res.string.groups_engagement_description) {
                    Threshold(Res.string.groups_min_likes, state.settings.rules.minLikes, actions::setMinLikes)
                    Spacer(Modifier.size(12.dp))
                    Threshold(Res.string.groups_min_reposts, state.settings.rules.minReposts, actions::setMinReposts)
                }
            }
            item(key = "muted") {
                Section(GroupIcons.SpeakerNotesOffOutlined, Res.string.groups_muted, Res.string.groups_muted_description) {
                    MutedWords(state.settings.rules.mutedKeywords, actions)
                }
            }
            item(key = "languages") {
                Section(GroupIcons.Language, Res.string.groups_language_filter, Res.string.groups_language_filter_description) {
                    LanguageFilterRow(state.prefs, actions)
                }
            }
            if (state.accounts.isNotEmpty()) {
                item(key = "caps") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        SectionTitle(GroupIcons.Speed, Res.string.groups_quiet_account, Res.string.groups_quiet_account_description)
                    }
                }
                items(state.accounts, key = { it.id }) { account ->
                    CapRow(account, SubscriptionList.capChoice(state.postCaps[account.id])) { capping = account }
                }
            }
            item(key = "end") { Spacer(Modifier.size(32.dp)) }
        }
    }
    capping?.let { account ->
        PostCapDialog(account.screenName, SubscriptionList.capChoice(state.postCaps[account.id]), onPick = { actions.setPostCap(account.id, it) }, onDismiss = { capping = null })
    }
}

/** A titled part with a short explanation, a flat surface over a hairline, as XTA's group settings sections. */
@Composable
private fun Section(icon: ImageVector, title: StringResource, description: StringResource, content: @Composable () -> Unit) {
    Column {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) { SectionTitle(icon, title, description) }
        Column(Modifier.padding(horizontal = 16.dp)) { content() }
        Spacer(Modifier.size(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: StringResource, description: StringResource) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentFilterRow(current: ContentFilter, onPick: (ContentFilter) -> Unit) {
    val filters = listOf(ContentFilter.SFW, ContentFilter.DEFAULT, ContentFilter.NSFW)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        filters.forEachIndexed { index, filter ->
            SegmentedButton(selected = current == filter, onClick = { onPick(filter) }, shape = SegmentedButtonDefaults.itemShape(index, filters.size), icon = {}) {
                Text(contentFilterLabel(filter), maxLines = 1)
            }
        }
    }
}

/** One floor as a row of choices, a tap rather than typing a number; 0 is off. A value from an earlier edit stays offered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Threshold(label: StringResource, value: Int, onPick: (Int) -> Unit) {
    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupFeedRules.thresholdChoices(value).forEach { choice ->
            ChoicePill(if (choice == 0) stringResource(Res.string.groups_threshold_off) else "$choice+", value == choice) { onPick(choice) }
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .let { if (selected) it.background(accent, shape) else it.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

/** The feed's muted words: a field to add some, and a chip for each, whose options set what it does and for how long. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MutedWords(keywords: List<MutedKeyword>, actions: GroupSettingsActions) {
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
        IconButton(onClick = add) { Icon(Icons.Filled.Add, stringResource(Res.string.groups_muted_hint), tint = MaterialTheme.colorScheme.primary) }
    }
    if (keywords.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keywords.forEach { keyword ->
                val action = stringResource(if (keyword.action == KeywordAction.FOLD) Res.string.groups_filter_fold else Res.string.groups_filter_hide)
                val label = listOfNotNull(keyword.term, action, keyword.until?.let { stringResource(Res.string.groups_filter_timed) }).joinToString(" · ")
                InputChip(
                    selected = false,
                    onClick = { editing = keyword },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = MaterialTheme.colorScheme.surface) {
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
                GroupFeedRules.EXPIRIES.zip(expiries).forEach { (duration, label) ->
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

/**
 * The reader's language filter (Settings › Posts in XTA): the languages kept,
 * as prefixes (`en, fr, ja`), and what happens to the others. Saved as the
 * field is left or submitted; with no languages it goes off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageFilterRow(prefs: FeedPrefs, actions: GroupSettingsActions) {
    var typed by rememberSaveable(prefs.languages) { mutableStateOf(prefs.languages) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.languages) { typed = prefs.languages }
    TextField(
        value = typed,
        onValueChange = { typed = it },
        singleLine = true,
        label = { Text(stringResource(Res.string.groups_languages)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { actions.setLanguages(typed, prefs.languageAction) }),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
            if (focused && !focus.isFocused) actions.setLanguages(typed, prefs.languageAction)
            focused = focus.isFocused
        },
    )
    Text(stringResource(Res.string.groups_language_action), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    val choices = listOf(LanguageAction.OFF to Res.string.groups_language_off, LanguageAction.HIDE to Res.string.groups_language_hide, LanguageAction.FOLD to Res.string.groups_language_fold)
    val active = typed.isNotBlank()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (action, label) ->
            SegmentedButton(
                selected = prefs.languageAction == action,
                onClick = { actions.setLanguages(typed, action) },
                enabled = active || action == LanguageAction.OFF,
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                icon = {},
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@Composable
private fun CapRow(account: FollowedAccount, cap: Int?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(account.avatarUrl, 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${account.screenName}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(cap?.toString() ?: stringResource(Res.string.groups_quiet_account_off), color = if (cap == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
    }
}
