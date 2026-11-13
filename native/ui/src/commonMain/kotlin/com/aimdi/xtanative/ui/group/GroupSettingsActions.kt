package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.LanguageAction
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.LanguageFilterAction
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a group feed's settings change (group_model.dart's GroupModel,
 * _settings.dart, group_custom_settings.dart): the group's own choices in the
 * session, and the reader's language filter and warnings in the settings.
 */
class GroupSettingsActions(private val graph: AppGraph, private val groupId: String, private val scope: CoroutineScope) {
    private fun feed(change: (FeedSettings) -> FeedSettings) {
        scope.launch { graph.sessions.update { it.withSettings(groupId, change(it.settingsOf(groupId))) } }
    }

    private fun settings(change: (Settings) -> Settings) {
        scope.launch { graph.settings.update(change) }
    }

    /** The group's own reply choice, which the switch shows over the session's. */
    fun setIncludeReplies(value: Boolean) = feed { it.copy(includeReplies = value) }

    fun setIncludeReposts(value: Boolean) = feed { it.copy(includeReposts = value) }

    fun setCatchUp(value: Boolean) = feed { it.copy(catchUp = value) }

    fun setSort(sort: FeedSort) = feed { GroupFeedRules.withSort(it, sort) }

    fun setContentFilter(filter: ContentFilter) = feed { it.copy(rules = it.rules.copy(contentFilter = filter)) }

    fun setMinLikes(value: Int) = feed { GroupFeedRules.withMinLikes(it, value) }

    fun setMinReposts(value: Int) = feed { GroupFeedRules.withMinReposts(it, value) }

    fun mute(typed: String) = feed { GroupFeedRules.withMuted(it, typed) }

    fun unmute(term: String) = feed { GroupFeedRules.withoutMuted(it, term) }

    fun setKeywordAction(term: String, action: KeywordAction) = feed { GroupFeedRules.withAction(it, term, action) }

    fun setKeywordExpiry(term: String, duration: Duration?) = feed { GroupFeedRules.withExpiry(it, term, duration, Instant.now()) }

    fun toggleDeck() {
        scope.launch { graph.sessions.update { it.toggledDeck(groupId) } }
    }

    /** The reader's language filter, which every feed reads; with no languages it goes off. */
    fun setLanguages(typed: String, action: LanguageAction) {
        val (languages, kept) = GroupFeedRules.languagePrefs(typed, action)
        settings {
            it.with(PostsPrefs.languages, languages)
                .with(PostsPrefs.languageAction, LanguageFilterAction.entries.first { choice -> choice.stored == kept.stored })
        }
    }

    /** The most of one account's posts a load keeps; null keeps them all. */
    fun setPostCap(userId: String, cap: Int?) {
        scope.launch { graph.sessions.update { it.withPostCap(userId, cap) } }
    }

    /** The unrelated-posts warning is not shown again, anywhere. */
    fun hideUnrelatedWarnings() = settings { it.with(PostsPrefs.hideUnrelatedPostWarnings, true) }
}
