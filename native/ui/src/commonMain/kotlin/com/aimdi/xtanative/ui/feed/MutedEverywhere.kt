package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.feed.FeedRules
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.feed.MutedKeywords
import com.aimdi.xtanative.core.feed.Verdict
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SettingsStore
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the editor of a list of muted words changes: a group feed's own list
 * (in the session) or the reader's list for every feed (in the settings).
 * One editor serves both, so the two cannot come to work differently.
 */
interface MutedWordsActions {
    /** Mutes what was typed, split at commas and line breaks. */
    fun mute(typed: String)

    fun unmute(term: String)

    fun setKeywordAction(term: String, action: KeywordAction)

    /** [term] muted for [duration] from now, or for good with null. */
    fun setKeywordExpiry(term: String, duration: Duration?)
}

/** The edits of a list of muted words, the same for a group's list and the reader's list for every feed. */
object MutedWordList {
    /** The expiries a muted word may get, in XTA's order: never, a day, a week, a month. */
    val EXPIRIES: List<Duration?> = listOf(null, Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(30))

    /** [keywords] with what was typed muted too: each term once, whatever its case. */
    fun added(keywords: List<MutedKeyword>, typed: String): List<MutedKeyword> {
        val known = keywords.mapTo(HashSet()) { it.term.lowercase() }
        return keywords + MutedKeywords.parseTyped(typed).filter { known.add(it.lowercase()) }.map { MutedKeyword(it) }
    }

    fun removed(keywords: List<MutedKeyword>, term: String): List<MutedKeyword> = keywords.filterNot { it.term == term }

    fun withAction(keywords: List<MutedKeyword>, term: String, action: KeywordAction): List<MutedKeyword> =
        keywords.map { if (it.term == term) it.copy(action = action) else it }

    /** [term] muted for [duration] from [now], or for good with null. */
    fun withExpiry(keywords: List<MutedKeyword>, term: String, duration: Duration?, now: Instant): List<MutedKeyword> =
        keywords.map { if (it.term == term) it.copy(until = duration?.let(now::plus)) else it }
}

/**
 * The editor's changes to the list for every feed, written to the settings
 * through [update] (the settings pages' own way of saving); [now] dates an
 * expiry.
 */
class MutedEverywhereActions(private val update: ((Settings) -> Settings) -> Unit, private val now: () -> Instant = Instant::now) : MutedWordsActions {
    private fun edit(change: (List<MutedKeyword>) -> List<MutedKeyword>) = update { MutedEverywhere.edited(it, change) }

    override fun mute(typed: String) = edit { MutedWordList.added(it, typed) }

    override fun unmute(term: String) = edit { MutedWordList.removed(it, term) }

    override fun setKeywordAction(term: String, action: KeywordAction) = edit { MutedWordList.withAction(it, term, action) }

    override fun setKeywordExpiry(term: String, duration: Duration?) = edit { MutedWordList.withExpiry(it, term, duration, now()) }
}

/**
 * The words the reader muted in every X feed (Settings › Posts), kept in
 * [PostsPrefs.mutedWords]. XTA mutes words one group at a time, which leaves
 * For you, communities and antennas with nothing; this list is read by all of
 * them, and a group's own words add to it. Matching is the group feeds'
 * ([FeedRules.verdict] over [FeedRules.searchText]): a single word on word
 * boundaries, a phrase anywhere, in a post and what it reposts and quotes.
 * Nothing leaves the device.
 */
object MutedEverywhere {
    fun of(settings: Settings): List<MutedKeyword> = MutedKeywords.parseStored(settings[PostsPrefs.mutedWords])

    /** The list as [store] holds it, each time it changes: a feed on screen narrows as a word is muted. */
    fun of(store: SettingsStore): Flow<List<MutedKeyword>> = store.settings.map(::of).distinctUntilChanged()

    /** [settings] with the list put through [change]. */
    fun edited(settings: Settings, change: (List<MutedKeyword>) -> List<MutedKeyword>): Settings =
        settings.with(PostsPrefs.mutedWords, MutedKeywords.encodeStored(change(of(settings))))

    /**
     * A feed's [own] words with [everywhere]'s after them. A term in both
     * (whatever its case) keeps the feed's own entry: what was chosen for one
     * feed says more than the general rule, so a word folded in a group stays
     * folded there though it is hidden elsewhere.
     */
    fun merged(own: List<MutedKeyword>, everywhere: List<MutedKeyword>): List<MutedKeyword> {
        if (everywhere.isEmpty()) return own
        val known = own.mapTo(HashSet()) { it.term.lowercase() }
        return own + everywhere.filter { known.add(it.term.lowercase()) }
    }

    /** [rules] muting [everywhere]'s words beside their own, for the feeds that hide and fold by their rules. */
    fun added(rules: FeedRules, everywhere: List<MutedKeyword>): FeedRules =
        if (everywhere.isEmpty()) rules else rules.copy(mutedKeywords = merged(rules.mutedKeywords, everywhere))

    /**
     * Whether a word of [keywords] still muted at [now] is in [thread]. For
     * the feeds without folded posts (For you, a community, an antenna): there
     * a word set to fold hides too, since a post it left in plain view would
     * not be muted at all.
     */
    fun hides(thread: XThread, keywords: List<MutedKeyword>, now: Instant): Boolean = hidesActive(thread, MutedKeywords.active(keywords, now))

    /** [threads] without those [hides] hides; the same list when nothing is muted. */
    fun shown(threads: List<XThread>, keywords: List<MutedKeyword>, now: Instant = Instant.now()): List<XThread> {
        val active = MutedKeywords.active(keywords, now)
        return if (active.isEmpty()) threads else threads.filterNot { hidesActive(it, active) }
    }

    /** Rules with no filter of their own: only the words handed to [FeedRules.verdict] decide. */
    private val WORDS_ONLY = FeedRules()

    private fun hidesActive(thread: XThread, active: List<MutedKeyword>): Boolean = WORDS_ONLY.verdict(thread, active) != Verdict.Keep
}
