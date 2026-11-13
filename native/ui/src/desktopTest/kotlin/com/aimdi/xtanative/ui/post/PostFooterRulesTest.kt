package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostFooterRulesTest {
    private val now = Instant.parse("2026-11-04T12:00:00Z")

    private fun endsIn(duration: Duration) = PollWords.end(now.plus(duration), now)

    private fun endedAgo(duration: Duration) = PollWords.end(now.minus(duration), now)

    @Test
    fun aPollThatIsStillOpenSaysWhenItEndsInMinutesHoursOrDays() {
        assertEquals(PollEnd(PollPhrase.ENDS_MINUTES, 1), endsIn(Duration.ofSeconds(20)), "under a minute and a half reads as a minute")
        assertEquals(PollEnd(PollPhrase.ENDS_MINUTES, 12), endsIn(Duration.ofMinutes(12)))
        assertEquals(PollEnd(PollPhrase.ENDS_HOURS, 1), endsIn(Duration.ofMinutes(50)), "from 45 minutes, about an hour")
        assertEquals(PollEnd(PollPhrase.ENDS_HOURS, 2), endsIn(Duration.ofMinutes(110)))
        assertEquals(PollEnd(PollPhrase.ENDS_DAYS, 1), endsIn(Duration.ofHours(30)), "from a day to two, a day")
        assertEquals(PollEnd(PollPhrase.ENDS_DAYS, 6), endsIn(Duration.ofDays(6)))
        assertEquals(PollEnd(PollPhrase.ENDS_DAYS, 45), endsIn(Duration.ofDays(45)), "what is left of a poll never reads in months")
    }

    @Test
    fun aClosedPollSaysWhenItEndedUpToYearsAgo() {
        assertEquals(PollEnd(PollPhrase.ENDED_MINUTES, 30), endedAgo(Duration.ofMinutes(30)))
        assertEquals(PollEnd(PollPhrase.ENDED_HOURS, 5), endedAgo(Duration.ofHours(5)))
        assertEquals(PollEnd(PollPhrase.ENDED_DAYS, 3), endedAgo(Duration.ofDays(3)))
        assertEquals(PollEnd(PollPhrase.ENDED_MONTHS, 1), endedAgo(Duration.ofDays(40)))
        assertEquals(PollEnd(PollPhrase.ENDED_MONTHS, 4), endedAgo(Duration.ofDays(120)))
        assertEquals(PollEnd(PollPhrase.ENDED_YEARS, 1), endedAgo(Duration.ofDays(400)))
        assertEquals(PollEnd(PollPhrase.ENDED_YEARS, 3), endedAgo(Duration.ofDays(1100)))
        assertNull(PollWords.end(null, now), "X did not say when: the votes alone")
    }

    @Test
    fun votesAndSharesAreWrittenAsTheReadersLocaleWritesNumbers() {
        assertEquals("1,234", PollWords.votes(1234, Locale.ENGLISH))
        assertEquals("1.234", PollWords.votes(1234, Locale.GERMAN))
        assertEquals("62.5%", PollWords.percent(0.625, Locale.ENGLISH))
        assertEquals("100%", PollWords.percent(1.0, Locale.ENGLISH), "no decimal where there is none")
        assertEquals("33.3%", PollWords.percent(1.0 / 3, Locale.ENGLISH))
    }

    @Test
    fun theShareSheetOffersXtasEntriesAndAnArticleItsLinkAndPicture() {
        assertEquals(listOf(ShareEntry.CONTENT, ShareEntry.LINK, ShareEntry.CONTENT_AND_LINK, ShareEntry.IMAGE), PostShare.entries(article = false))
        assertEquals(listOf(ShareEntry.LINK, ShareEntry.IMAGE), PostShare.entries(article = true))
        assertEquals(listOf(ShareEntry.LINK), PostShare.entries(article = true, picture = false))
    }

    @Test
    fun sharedWordsLeadToTheirPagesWithoutXsRedirectorAndTrackers() {
        val shown = post("9", by = "maya", text = "Fish &amp; chips https://t.co/a1 https://t.co/m1").copy(
            entities = listOf(XEntity.Link(20, 40, "https://t.co/a1", "https://example.com/read?utm_source=x&id=7", "example.com/read")),
            media = listOf(XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/p.jpg", 10, 10, shortUrl = "https://t.co/m1")),
        )
        assertEquals("Fish & chips https://example.com/read?id=7 https://x.com/maya/status/9/photo/1", PostShare.text(shown, clean = true))
        assertEquals("Fish & chips https://example.com/read?utm_source=x&id=7 https://x.com/maya/status/9/photo/1", PostShare.text(shown, clean = false))
        assertEquals(PostShare.text(shown, clean = true) + "\n\nhttps://x.com/maya/status/9", PostShare.textAndLink(shown, "https://x.com/maya/status/9", clean = true))
        assertEquals("maya-9.png", PostShare.imageName(shown))
    }

    @Test
    fun theFooterDropsTheViewsFirstThenTheNumbersThenWraps() {
        val labels = listOf(20f, 20f, 20f)
        val counts = labels.sumOf { FooterFit.countWidth(it).toDouble() }.toFloat()
        val buttons = 2 * FooterFit.BUTTON + FooterFit.GROUP_GAP
        val views = FooterFit.countWidth(30f)
        assertEquals(FooterFit(showCounts = true, showViews = true, wraps = false), FooterFit.of(counts + views + buttons, labels, 30f))
        assertEquals(FooterFit(showCounts = true, showViews = false, wraps = false), FooterFit.of(counts + buttons, labels, 30f))
        assertEquals(FooterFit(showCounts = true, showViews = false, wraps = false), FooterFit.of(counts + buttons, labels, null), "a post without views")
        val bare = 3 * FooterFit.countWidth(0f) + buttons
        assertEquals(FooterFit(showCounts = false, showViews = false, wraps = false), FooterFit.of(bare, labels, 30f))
        assertEquals(FooterFit(showCounts = false, showViews = false, wraps = true), FooterFit.of(bare - 1, labels, 30f))
        assertEquals(FooterFit.PADDING * 2 + FooterFit.GLYPH, FooterFit.countWidth(0f), "a count without a number is its glyph")
    }

    @Test
    fun doubleTapToLikeIsReadFromSettingsAccessibilityAndOffAsXtaHasIt() {
        assertFalse(PostPrefs.of(Settings()).doubleTapLike)
        assertTrue(PostPrefs.of(Settings().with(AccessibilityPrefs.doubleTapLike, true)).doubleTapLike)
    }
}
