package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.GroupFeedPlan
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.LanguageAction
import com.aimdi.xtanative.core.feed.LanguageFilter
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.feed.MutedKeywords
import com.aimdi.xtanative.core.feed.PostFacts
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Duration
import java.time.Instant

/** The reader's settings a group feed reads besides the group's own: the language filter, zen mode and the reading position. */
data class FeedPrefs(
    val languages: String = "",
    val languageAction: LanguageAction = LanguageAction.OFF,
    val zen: Boolean = false,
    val zenPageCap: Int = 5,
    val readingPosition: Boolean = true,
    val hideUnrelatedWarning: Boolean = false,
) {
    /** The zen feed's pages per session; null without zen mode. */
    val pageCap: Int? get() = zenPageCap.takeIf { zen }

    companion object {
        fun of(settings: Settings) = FeedPrefs(
            languages = settings[PostsPrefs.languages],
            languageAction = LanguageAction.parse(settings[PostsPrefs.languageAction].stored),
            zen = settings[PostsPrefs.zenMode],
            zenPageCap = settings[PostsPrefs.zenPageCap],
            readingPosition = settings[PostsPrefs.rememberReadingPosition],
            hideUnrelatedWarning = settings[PostsPrefs.hideUnrelatedPostWarnings],
        )
    }
}

/**
 * What a group's feed reads and how (_feed.dart, group_chrome.dart,
 * _settings.dart, group_custom_settings.dart): the session's plan with the
 * reader's global filters added, the order chips and the rules' edits.
 */
object GroupFeedRules {
    /** The thresholds offered for likes and reposts; 0 is off. */
    val THRESHOLDS = listOf(0, 10, 50, 100, 500, 1000)

    /**
     * [groupId]'s plan, with [alsoRead] read alongside, the language filter
     * and zen mode added, and the rules reading what X says of each post.
     */
    fun plan(session: Session, prefs: FeedPrefs, groupId: String, alsoRead: Set<String>): GroupFeedPlan {
        val plan = session.feedPlan(groupId, alsoRead - groupId)
        val language = LanguageFilter(LanguageFilter.parseLanguages(prefs.languages), prefs.languageAction)
        return plan.copy(shaping = plan.shaping.copy(language = language, zen = prefs.zen, facts = XPostFacts))
    }

    /** What the rules read of a post, as XTA reads a tweet: whether X marks it sensitive, and its language; a repost's are the reposted post's. */
    object XPostFacts : PostFacts {
        override fun sensitive(post: XPost): Boolean = post.sensitive || post.reposted?.sensitive == true

        override fun language(post: XPost): String? = (post.reposted ?: post).lang
    }

    /** Whether the feed keeps a read position: only chronological, and with the reader's switch or catch-up mode. */
    fun tracks(plan: GroupFeedPlan, prefs: FeedPrefs): Boolean = plan.tracksReadPosition(prefs.readingPosition)

    /** Catch-up mode, for a chronological feed only. */
    fun catchUp(plan: GroupFeedPlan): Boolean = plan.catchUp && !plan.popular

    /** How many of the custom rules are on, for the Custom chip; none outside custom mode. */
    fun activeFilters(settings: FeedSettings): Int {
        if (settings.sort != FeedSort.CUSTOM) return 0
        val rules = settings.rules
        return (if (rules.contentFilter == ContentFilter.DEFAULT) 0 else 1) +
            (if (rules.minLikes > 0) 1 else 0) +
            (if (rules.minReposts > 0) 1 else 0) +
            rules.mutedKeywords.size
    }

    /** The content filter chip beside the orders: only in custom mode, and only when it filters. */
    fun contentChip(settings: FeedSettings): ContentFilter? =
        settings.rules.contentFilter.takeIf { settings.sort == FeedSort.CUSTOM && it != ContentFilter.DEFAULT }

    /** [settings] in [sort]; picking the order it already has changes nothing. */
    fun withSort(settings: FeedSettings, sort: FeedSort): FeedSettings = if (settings.sort == sort) settings else settings.copy(sort = sort)

    /** [settings] with what was typed muted too: each term once, whatever its case. */
    fun withMuted(settings: FeedSettings, typed: String): FeedSettings {
        val terms = MutedKeywords.parseTyped(typed)
        if (terms.isEmpty()) return settings
        val known = settings.rules.mutedKeywords.mapTo(HashSet()) { it.term.lowercase() }
        val added = terms.filter { known.add(it.lowercase()) }.map { MutedKeyword(it) }
        return withKeywords(settings, settings.rules.mutedKeywords + added)
    }

    fun withoutMuted(settings: FeedSettings, term: String): FeedSettings =
        withKeywords(settings, settings.rules.mutedKeywords.filterNot { it.term == term })

    fun withAction(settings: FeedSettings, term: String, action: KeywordAction): FeedSettings =
        withKeywords(settings, settings.rules.mutedKeywords.map { if (it.term == term) it.copy(action = action) else it })

    /** [term] muted for [duration] from [now], or for good with null. */
    fun withExpiry(settings: FeedSettings, term: String, duration: Duration?, now: Instant): FeedSettings =
        withKeywords(settings, settings.rules.mutedKeywords.map { if (it.term == term) it.copy(until = duration?.let(now::plus)) else it })

    private fun withKeywords(settings: FeedSettings, keywords: List<MutedKeyword>) =
        settings.copy(rules = settings.rules.copy(mutedKeywords = keywords))

    fun withMinLikes(settings: FeedSettings, value: Int) = settings.copy(rules = settings.rules.copy(minLikes = value.coerceAtLeast(0)))

    fun withMinReposts(settings: FeedSettings, value: Int) = settings.copy(rules = settings.rules.copy(minReposts = value.coerceAtLeast(0)))

    /** The thresholds to offer: XTA's, and a value from an earlier edit that is none of them. */
    fun thresholdChoices(value: Int): List<Int> = if (value in THRESHOLDS) THRESHOLDS else THRESHOLDS + value

    /** The expiries a muted word may get, in XTA's order: never, a day, a week, a month. */
    val EXPIRIES: List<Duration?> = listOf(null, Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(30))

    /** The languages as typed, trimmed; with none, the filter has nothing to keep and goes off. */
    fun languagePrefs(typed: String, action: LanguageAction): Pair<String, LanguageAction> {
        val trimmed = typed.trim()
        return trimmed to if (LanguageFilter.parseLanguages(trimmed).isEmpty()) LanguageAction.OFF else action
    }
}
