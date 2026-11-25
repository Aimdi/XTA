package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.feed.FeedRules
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The words muted in every feed (Settings › Posts): the list's edits, its place in the settings and what it hides. */
class MutedEverywhereTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun post(id: String, text: String, quoted: XPost? = null) = XPost(id, XUser("u$id", "u$id", "U $id"), null, text, quoted = quoted)

    private fun thread(vararg posts: XPost) = XThread(posts.first().id, posts.toList())

    @Test
    fun wordsAreAddedOnceWhateverTheirCaseAndEditedOneByOne() {
        var words = MutedWordList.added(emptyList(), "Spoilers, black friday\nspoilers")
        assertEquals(listOf("Spoilers", "black friday"), words.map { it.term })
        words = MutedWordList.added(words, "SPOILERS,  , crypto")
        assertEquals(listOf("Spoilers", "black friday", "crypto"), words.map { it.term })
        words = MutedWordList.withAction(words, "Spoilers", KeywordAction.FOLD)
        assertEquals(listOf(KeywordAction.FOLD, KeywordAction.HIDE, KeywordAction.HIDE), words.map { it.action })
        words = MutedWordList.withExpiry(words, "crypto", Duration.ofDays(7), now)
        assertEquals(Instant.parse("2026-01-08T00:00:00Z"), words[2].until)
        assertNull(MutedWordList.withExpiry(words, "crypto", null, now)[2].until)
        assertEquals(listOf("black friday", "crypto"), MutedWordList.removed(words, "Spoilers").map { it.term })
        assertEquals(listOf(null, Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(30)), MutedWordList.EXPIRIES)
    }

    @Test
    fun theListLivesInTheSettingsAndReadsBackAsItWasLeft() {
        assertTrue(MutedEverywhere.of(Settings()).isEmpty())
        val words = listOf(MutedKeyword("spoilers", Instant.parse("2026-01-08T00:00:00Z"), KeywordAction.FOLD), MutedKeyword("black friday"))
        val settings = MutedEverywhere.edited(Settings()) { words }
        assertEquals(words, MutedEverywhere.of(settings))
        assertEquals(words, MutedEverywhere.of(Settings(settings.values)), "what is stored is all there is to it")
        val cleared = MutedEverywhere.edited(settings) { emptyList() }
        assertEquals("", cleared[PostsPrefs.mutedWords], "nothing muted is stored as nothing")
        assertEquals(listOf("a", "b"), MutedEverywhere.of(Settings().with(PostsPrefs.mutedWords, "a, b")).map { it.term }, "a list typed by hand reads too")
    }

    @Test
    fun theEditorsActionsWriteTheSettings() {
        var settings = Settings()
        val actions = MutedEverywhereActions({ change -> settings = change(settings) }, { now })
        actions.mute("Spoilers, crypto")
        actions.mute("spoilers")
        actions.setKeywordAction("Spoilers", KeywordAction.FOLD)
        actions.setKeywordExpiry("crypto", Duration.ofDays(1))
        assertEquals(
            listOf(MutedKeyword("Spoilers", action = KeywordAction.FOLD), MutedKeyword("crypto", Instant.parse("2026-01-02T00:00:00Z"))),
            MutedEverywhere.of(settings),
        )
        actions.unmute("Spoilers")
        actions.unmute("crypto")
        assertFalse(settings[PostsPrefs.mutedWords].isNotEmpty())
    }

    @Test
    fun aGroupsOwnWordsComeFirstAndWinOverTheSameWordEverywhere() {
        val own = listOf(MutedKeyword("Spoilers", action = KeywordAction.FOLD), MutedKeyword("ads"))
        val everywhere = listOf(MutedKeyword("spoilers"), MutedKeyword("crypto", Instant.parse("2026-02-01T00:00:00Z")))
        val merged = MutedEverywhere.merged(own, everywhere)
        assertEquals(own + everywhere[1], merged)
        assertSame(own, MutedEverywhere.merged(own, emptyList()))
        val rules = FeedRules(minLikes = 3, mutedKeywords = own)
        assertSame(rules, MutedEverywhere.added(rules, emptyList()))
        assertEquals(rules.copy(mutedKeywords = merged), MutedEverywhere.added(rules, everywhere))
    }

    @Test
    fun aWordHidesAsInAGroupFeedWholeWordsPhrasesAndWhatAPostQuotes() {
        val words = listOf(MutedKeyword("cat"), MutedKeyword("black friday"))
        assertTrue(MutedEverywhere.hides(thread(post("1", "My CAT sleeps")), words, now))
        assertFalse(MutedEverywhere.hides(thread(post("2", "A category of things")), words, now), "a single word matches whole words only")
        assertTrue(MutedEverywhere.hides(thread(post("3", "Preblack fridays")), words, now), "a phrase matches anywhere")
        assertTrue(MutedEverywhere.hides(thread(post("4", "look", quoted = post("5", "a cat"))), words, now))
        assertTrue(MutedEverywhere.hides(thread(post("6", "first"), post("7", "the cat again")), words, now))
        assertFalse(MutedEverywhere.hides(thread(post("8", "nothing here")), words, now))
    }

    @Test
    fun whereNothingFoldsAWordSetToFoldHidesAndAnExpiredOneNoLonger() {
        val threads = listOf(thread(post("1", "spoilers ahead")), thread(post("2", "crypto news")), thread(post("3", "a walk")))
        val words = listOf(MutedKeyword("spoilers", action = KeywordAction.FOLD), MutedKeyword("crypto", until = now.plusSeconds(60)))
        assertEquals(listOf("3"), MutedEverywhere.shown(threads, words, now).map { it.id })
        assertEquals(listOf("2", "3"), MutedEverywhere.shown(threads, words, now.plusSeconds(61)).map { it.id })
        assertSame(threads, MutedEverywhere.shown(threads, emptyList(), now))
        assertSame(threads, MutedEverywhere.shown(threads, listOf(words[1]), now.plusSeconds(61)), "nothing still muted filters nothing")
    }
}
