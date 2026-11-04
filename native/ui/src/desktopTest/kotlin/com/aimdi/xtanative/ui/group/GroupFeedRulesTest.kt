package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.FeedRules
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.LanguageAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.settings.LanguageFilterAction
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

class GroupFeedRulesTest {
    private fun account(id: String) = FollowedAccount(id, "user$id", "User $id", null, false, true, id.toLong())

    private val session = Session(
        following = listOf(account("1"), account("2")),
        groups = listOf(FollowGroup("g", "G", listOf("1")), FollowGroup("other", "Other", listOf("2"))),
        feeds = mapOf("g" to FeedSettings(includeReplies = false, sort = FeedSort.POPULAR, catchUp = true)),
        includeReposts = false,
    )

    @Test
    fun theGroupsPlanGetsTheReadersLanguageFilterAndZenMode() {
        val settings = Settings()
            .with(PostsPrefs.languages, "en, fr")
            .with(PostsPrefs.languageAction, LanguageFilterAction.FOLD)
            .with(PostsPrefs.zenMode, true)
        val prefs = FeedPrefs.of(settings)
        assertEquals(5, prefs.pageCap)
        val plan = GroupFeedRules.plan(session, prefs, "g", alsoRead = setOf("other", "g"))
        assertEquals(listOf("1", "2"), plan.members.map { it.id }, "read alongside, never alongside itself")
        assertFalse(plan.includeReplies)
        assertFalse(plan.includeReposts, "the session's switch where the group has no choice of its own")
        assertEquals(listOf("en", "fr"), plan.shaping.language.allowed)
        assertEquals(LanguageAction.FOLD, plan.shaping.language.action)
        assertTrue(plan.shaping.zen)
        assertNull(FeedPrefs().pageCap)
    }

    @Test
    fun aPopularFeedKeepsNoPositionAndHasNoCatchUp() {
        val popular = GroupFeedRules.plan(session, FeedPrefs(), "g", emptySet())
        assertFalse(GroupFeedRules.tracks(popular, FeedPrefs()))
        assertFalse(GroupFeedRules.catchUp(popular))
        val recent = popular.copy(shaping = popular.shaping.copy(sort = FeedSort.RECENT))
        assertTrue(GroupFeedRules.catchUp(recent))
        assertTrue(GroupFeedRules.tracks(recent, FeedPrefs(readingPosition = false)), "catch-up mode keeps one anyway")
    }

    @Test
    fun theCustomChipCountsTheRulesThatAreOn() {
        val rules = FeedRules(ContentFilter.SFW, minLikes = 10, minReposts = 0, mutedKeywords = listOf(MutedKeyword("a"), MutedKeyword("b")))
        assertEquals(4, GroupFeedRules.activeFilters(FeedSettings(sort = FeedSort.CUSTOM, rules = rules)))
        assertEquals(0, GroupFeedRules.activeFilters(FeedSettings(sort = FeedSort.RECENT, rules = rules)), "outside custom mode nothing counts")
        assertEquals(ContentFilter.SFW, GroupFeedRules.contentChip(FeedSettings(sort = FeedSort.CUSTOM, rules = rules)))
        assertNull(GroupFeedRules.contentChip(FeedSettings(sort = FeedSort.CUSTOM)))
        val settings = FeedSettings(sort = FeedSort.POPULAR)
        assertSame(settings, GroupFeedRules.withSort(settings, FeedSort.POPULAR))
        assertEquals(FeedSort.CUSTOM, GroupFeedRules.withSort(settings, FeedSort.CUSTOM).sort)
    }

    @Test
    fun mutedWordsAreAddedOnceAndEditedOneByOne() {
        var settings = GroupFeedRules.withMuted(FeedSettings(), "Spoilers, black friday\nspoilers")
        assertEquals(listOf("Spoilers", "black friday"), settings.rules.mutedKeywords.map { it.term })
        settings = GroupFeedRules.withMuted(settings, "SPOILERS")
        assertEquals(2, settings.rules.mutedKeywords.size)
        settings = GroupFeedRules.withAction(settings, "Spoilers", KeywordAction.FOLD)
        assertEquals(KeywordAction.FOLD, settings.rules.mutedKeywords.first().action)
        val now = Instant.parse("2026-01-01T00:00:00Z")
        settings = GroupFeedRules.withExpiry(settings, "black friday", Duration.ofDays(7), now)
        assertEquals(Instant.parse("2026-01-08T00:00:00Z"), settings.rules.mutedKeywords[1].until)
        assertNull(GroupFeedRules.withExpiry(settings, "black friday", null, now).rules.mutedKeywords[1].until)
        assertEquals(listOf("black friday"), GroupFeedRules.withoutMuted(settings, "Spoilers").rules.mutedKeywords.map { it.term })
        assertSame(settings, GroupFeedRules.withMuted(settings, " , "))
    }

    @Test
    fun floorsAreChoicesAndAnEarlierValueStaysOffered() {
        assertEquals(listOf(0, 10, 50, 100, 500, 1000), GroupFeedRules.thresholdChoices(50))
        assertEquals(listOf(0, 10, 50, 100, 500, 1000, 25), GroupFeedRules.thresholdChoices(25))
        assertEquals(0, GroupFeedRules.withMinLikes(FeedSettings(), -3).rules.minLikes)
        assertEquals(100, GroupFeedRules.withMinReposts(FeedSettings(), 100).rules.minReposts)
    }

    @Test
    fun theRulesReadWhatXSaysOfAPostAndOfWhatItReposts() {
        val author = XUser("a", "a", "A")
        val original = XPost("1", author, null, "bonjour", lang = "fr", sensitive = true)
        val repost = XPost("2", author, null, "RT", reposted = original, lang = "en")
        assertTrue(GroupFeedRules.XPostFacts.sensitive(repost))
        assertEquals("fr", GroupFeedRules.XPostFacts.language(repost))
        assertEquals("fr", GroupFeedRules.XPostFacts.language(original))
        val plan = GroupFeedRules.plan(session, FeedPrefs(), "g", emptySet())
        assertSame(GroupFeedRules.XPostFacts, plan.shaping.facts)
    }

    @Test
    fun theLanguageFilterGoesOffWithoutLanguages() {
        assertEquals("en,fr" to LanguageAction.HIDE, GroupFeedRules.languagePrefs(" en,fr ", LanguageAction.HIDE))
        assertEquals("" to LanguageAction.OFF, GroupFeedRules.languagePrefs("  ", LanguageAction.FOLD))
    }
}
