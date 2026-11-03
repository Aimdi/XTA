package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.post
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's custom_feed_rules_test, muted_keyword_storage_test and language_filter_test, for the native port. */
class FeedRulesTest {
    /** Stands in for what XPost does not carry yet: sensitive posts by text, languages by post id. */
    private class Facts(val sensitiveText: Set<String> = emptySet(), val languages: Map<String, String> = emptyMap()) : PostFacts {
        override fun sensitive(post: XPost) = post.text in sensitiveText

        override fun language(post: XPost) = languages[post.id]
    }

    private fun texts(threads: List<XThread>) = threads.map { it.posts.first().text }

    private fun keyword(term: String, action: KeywordAction = KeywordAction.HIDE, until: Instant? = null) = MutedKeyword(term, until, action)

    @Test
    fun typedTermsSplitOnCommasAndLineBreaksKeepingPhrasesWhole() {
        assertEquals(listOf("bitcoin", "black friday", "spoilers"), MutedKeywords.parseTyped("bitcoin, black friday\nspoilers"))
        assertTrue(MutedKeywords.parseTyped(null).isEmpty())
        assertTrue(MutedKeywords.parseTyped("   ").isEmpty())
        assertTrue(MutedKeywords.parseTyped(",,\n,").isEmpty())
        assertEquals(listOf("bitcoin", "black friday"), MutedKeywords.parseTyped(MutedKeywords.join(listOf("bitcoin", "black friday"))))
    }

    @Test
    fun aSingleWordMatchesWholeWordsOnlyAndAPhraseOrPunctuationAsWritten() {
        assertFalse(MutedKeywords.matches("I like cats", "cat"))
        assertTrue(MutedKeywords.matches("Look, a cat!", "cat"))
        assertFalse(MutedKeywords.matches("category error", "cat"))
        assertTrue(MutedKeywords.matches("BITCOIN is up", "bitcoin"))
        assertTrue(MutedKeywords.matches("the black friday sales", "black friday"))
        assertTrue(MutedKeywords.matches("buying \$TSLA today", "\$TSLA"))
        assertFalse(MutedKeywords.matches("no ticker here", "\$TSLA"))
        assertTrue(MutedKeywords.matches("Мне нравится кот", "кот"))
        assertFalse(MutedKeywords.matches("котлета на обед", "кот"))
        assertFalse(MutedKeywords.matches("anything", "  "))
    }

    @Test
    fun theStoredFormReadsXtasLegacyTermsAndItsJson() {
        val legacy = MutedKeywords.parseStored("bitcoin, black friday")
        assertEquals(listOf(MutedKeyword("bitcoin"), MutedKeyword("black friday")), legacy)

        val json = MutedKeywords.parseStored(
            """[{"term":"spoilers","until":"2099-01-01T00:00:00.000Z","action":"fold"},{"term":"ads","action":"hide"},{"term":"  "}]""",
        )
        assertEquals(listOf(MutedKeyword("spoilers", Instant.parse("2099-01-01T00:00:00Z"), KeywordAction.FOLD), MutedKeyword("ads")), json)

        val keywords = listOf(MutedKeyword("nft", action = KeywordAction.FOLD), MutedKeyword("ads", Instant.parse("2099-01-01T00:00:00Z")))
        assertEquals(keywords, MutedKeywords.parseStored(MutedKeywords.encodeStored(keywords)))
        assertEquals("", MutedKeywords.encodeStored(emptyList()))
        assertTrue(MutedKeywords.parseStored("[not json").isEmpty())
    }

    @Test
    fun anExpiredMuteNoLongerApplies() {
        val now = Instant.parse("2025-01-01T00:00:00Z")
        assertTrue(MutedKeywords.active(listOf(keyword("old", until = Instant.parse("2020-01-01T00:00:00Z"))), now).isEmpty())
        val threads = listOf(thread(post(1, text = "spoilers inside")))
        assertEquals(1, FeedRules(mutedKeywords = listOf(keyword("spoilers", until = Instant.parse("2020-01-01T00:00:00Z")))).apply(threads, now).threads.size)
    }

    @Test
    fun withNoRuleSetEverythingPassesEvenSensitivePosts() {
        val threads = listOf(thread(post(1, text = "a")), thread(post(2, text = "b")))
        assertEquals(threads, FeedRules().apply(threads, facts = Facts(setOf("a"))).threads)
    }

    @Test
    fun sfwKeepsOnlyPostsNotMarkedSensitiveAndNsfwOnlyThoseMarked() {
        val threads = listOf(thread(post(1, text = "clean")), thread(post(2, text = "spicy")))
        val facts = Facts(setOf("spicy"))
        assertEquals(listOf("clean"), texts(FeedRules(ContentFilter.SFW).apply(threads, facts = facts).threads))
        assertEquals(listOf("spicy"), texts(FeedRules(ContentFilter.NSFW).apply(threads, facts = facts).threads))
    }

    @Test
    fun theFloorsReadTheFirstPostAndCountRepostsAndQuotesTogether() {
        val likes = listOf(thread(post(1, text = "quiet", likes = 9)), thread(post(2, text = "loud", likes = 10)))
        assertEquals(listOf("loud"), texts(FeedRules(minLikes = 10).apply(likes).threads))

        val reposts = listOf(
            thread(post(1, text = "reposts only", reposts = 6)),
            thread(post(2, text = "split", reposts = 3, quotes = 3)),
            thread(post(3, text = "too few", reposts = 2, quotes = 1)),
        )
        assertEquals(listOf("reposts only", "split"), texts(FeedRules(minReposts = 6).apply(reposts).threads))
    }

    @Test
    fun aMutedWordHidesThePostAlsoInsideAQuoteOrARepost() {
        val rules = FeedRules(mutedKeywords = listOf(keyword("bitcoin"), keyword("spoilers")))
        val threads = listOf(
            thread(post(1, text = "the bitcoin thread")),
            thread(post(2, text = "unrelated")),
            thread(post(3, text = "look at this", quoted = post(30, text = "spoilers ahead"))),
            thread(post(4, text = "RT", repostOf = post(40, text = "spoilers again"))),
        )
        assertEquals(listOf("unrelated"), texts(rules.apply(threads).threads))
    }

    @Test
    fun aMutedWordMatchesTheTextAsShownNotAsXEscapesIt() {
        val threads = listOf(thread(post(1, text = "AT&amp;T outage")))
        assertTrue(FeedRules(mutedKeywords = listOf(keyword("AT&T"))).apply(threads).threads.isEmpty())
    }

    @Test
    fun foldKeepsTheThreadWithItsReason() {
        val outcome = FeedRules(mutedKeywords = listOf(keyword("spoilers", KeywordAction.FOLD))).apply(listOf(thread(post(7, text = "spoilers inside"))))
        assertEquals(1, outcome.threads.size)
        assertEquals("spoilers", outcome.foldReasons["7"])
    }

    @Test
    fun aPostMustSatisfyEveryRule() {
        val threads = listOf(
            thread(post(1, text = "popular but muted", likes = 500)),
            thread(post(2, text = "popular and fine", likes = 500)),
            thread(post(3, text = "fine but quiet", likes = 1)),
        )
        assertEquals(listOf("popular and fine"), texts(FeedRules(minLikes = 100, mutedKeywords = listOf(keyword("muted"))).apply(threads).threads))
    }

    @Test
    fun theCacheKeyChangesWithEveryRuleSoFeedsDoNotShareWhatTheyRead() {
        val keys = setOf(
            FeedRules().cacheKey,
            FeedRules(ContentFilter.SFW).cacheKey,
            FeedRules(minLikes = 10).cacheKey,
            FeedRules(minReposts = 10).cacheKey,
            FeedRules(mutedKeywords = listOf(keyword("x"))).cacheKey,
        )
        assertEquals(5, keys.size)
        assertTrue(FeedRules().isEmpty)
    }

    @Test
    fun theLanguageFilterIsOffHidesOrFoldsAndKeepsEarlierFolds() {
        val facts = Facts(languages = mapOf("1" to "en", "2" to "de"))
        val threads = listOf(thread(1), thread(2), thread(3))
        assertEquals(threads, LanguageFilter(listOf("en"), LanguageAction.OFF).apply(threads, facts = facts).threads)
        assertEquals(listOf("1", "3"), Posts.ids(LanguageFilter(listOf("en"), LanguageAction.HIDE).apply(threads, facts = facts).threads))

        val folded = LanguageFilter(listOf("EN"), LanguageAction.FOLD).apply(threads, priorFolds = mapOf("9" to "spoilers"), facts = facts)
        assertEquals(listOf("1", "2", "3"), Posts.ids(folded.threads))
        assertEquals(mapOf("9" to "spoilers", "2" to "de"), folded.foldReasons)
        assertNull(folded.foldReasons["3"], "a post whose language X did not say passes")
    }

    @Test
    fun theLanguageSettingIsCommaSeparatedPrefixes() {
        assertEquals(listOf("en", "ja", "fr"), LanguageFilter.parseLanguages("en, ja ,fr"))
        assertEquals(LanguageAction.FOLD, LanguageAction.parse("fold"))
        assertEquals(LanguageAction.OFF, LanguageAction.parse("nonsense"))
    }
}
