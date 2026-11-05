package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.XReplyRanking
import com.aimdi.xtanative.core.x.XQuoteOrder
import com.aimdi.xtanative.core.x.XTranslationException
import com.aimdi.xtanative.core.x.model.XArticle
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XWithheld
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

internal fun user(id: String, followers: Long? = null) = XUser(id, id, id.uppercase(), followers = followers)

internal fun post(id: String, by: String = "a", text: String = "post $id", replyTo: String? = null, replyToId: String? = null) =
    XPost(id, user(by), null, text, replyToScreenName = replyTo, replyToPostId = replyToId)

internal fun thread(vararg posts: XPost, id: String = posts.first().id) = XThread(id, posts.toList())

class PostRulesTest {
    @Test
    fun theSettingsReadAsXtaDefaultsThem() {
        val defaults = PostPrefs.of(Settings())
        assertTrue(defaults.sensitiveHidden)
        assertTrue(defaults.threadedReplies)
        assertFalse(defaults.hideCounts)
        assertEquals("https://x.com", defaults.shareBase)

        val calm = PostPrefs.of(Settings().with(PostsPrefs.calmMode, true).with(PostsPrefs.alwaysShowSensitiveMedia, true).with(GeneralPrefs.shareBaseUrl, "https://fxtwitter.com"))
        assertTrue(calm.hideCounts, "calm mode hides the counts without zen's other limits")
        assertFalse(calm.sensitiveHidden, "always showing sensitive media wins over hiding it")
        assertEquals("https://fxtwitter.com", calm.shareBase)
        assertEquals("pt-BR", PostPrefs(locale = "pt_BR").translationTarget("de_DE"))
        assertEquals("de-DE", PostPrefs().translationTarget("de_DE"))
    }

    @Test
    fun theLinesAboveACardFollowXtasOrder() {
        val repost = post("1", by = "tom").copy(reposted = post("2", by = "maya").copy(communityId = "77"))
        assertEquals(
            listOf(ContextLine.Reposted(user("tom")), ContextLine.Pinned, ContextLine.Thread, ContextLine.Community("77")),
            PostView.contextLines(repost, pinned = true, firstOfThread = true, quoted = false),
        )
        assertEquals(emptyList(), PostView.contextLines(post("3").copy(communityId = "77"), pinned = false, firstOfThread = false, quoted = true))
        assertEquals("https://x.com/i/communities/77", ContextLine.Community("77").url)
    }

    @Test
    fun replyingToShowsUnderTheHeaderButNotInAQuoteNorMidThread() {
        val reply = post("2", replyTo = "maya")
        assertEquals("maya", PostView.replyingTo(reply, quoted = false, midThread = false))
        assertNull(PostView.replyingTo(reply, quoted = true, midThread = false))
        assertNull(PostView.replyingTo(reply, quoted = false, midThread = true))
        assertNull(PostView.replyingTo(post("3"), quoted = false, midThread = false))
    }

    @Test
    fun aQuoteXWithheldSaysWhyAndFindsItInTheArchiveWhenItsAuthorIsKnown() {
        val quoted = post("2")
        assertEquals(QuoteBlock.Shown(quoted), PostView.quote(post("1").copy(quoted = quoted), quoted = false))
        assertNull(PostView.quote(post("1").copy(quoted = quoted), quoted = true), "a quote's own quote is never drawn")
        assertEquals(
            QuoteBlock.Withheld("This post is unavailable.", false, "https://web.archive.org/web/*/twitter.com/maya/status/9*"),
            PostView.quote(post("1").copy(quotedWithheld = XWithheld("9", "This post is unavailable.", screenName = "maya")), quoted = false),
        )
        assertEquals(QuoteBlock.Withheld(null, true, null), PostView.quote(post("1").copy(quotedWithheld = XWithheld("9", null, subscribersOnly = true)), quoted = false))
        assertEquals(QuoteBlock.Unsent("9"), PostView.quote(post("1").copy(quotedWithheld = XWithheld("9", null)), quoted = false))
        assertNull(PostView.quote(post("1"), quoted = false))
    }

    @Test
    fun aQuoteNamingNothingAndAPostWithNothingToDrawSaySo() {
        assertEquals(QuoteBlock.Missing, PostView.quote(post("1").copy(quotedWithheld = XWithheld("", null)), quoted = false))
        assertTrue(PostView.textMissing(post("1", text = " ")))
        assertFalse(PostView.textMissing(post("1")))
        assertFalse(PostView.textMissing(post("1", text = "").copy(media = listOf(XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/a.jpg", 1, 1)))), "a picture alone is a post")
    }

    @Test
    fun anArticleLinkWithoutItsArticleIsACardAndCashtagsAreTheFirstFourOnce() {
        val link = XEntity.Link(0, 5, "https://t.co/a", "https://x.com/i/article/42", "x.com/i/article/42")
        assertEquals("https://x.com/i/article/42", PostView.articleLink(post("1").copy(entities = listOf(link))))
        assertNull(PostView.articleLink(post("1").copy(entities = listOf(link), article = XArticle("42", "T", "P", null))))

        val tags = listOf("tsla", "AAPL", "TSLA", "nvda", "msft", "goog").mapIndexed { i, s -> XEntity.Cashtag(i, i + 1, s) }
        assertEquals(listOf("TSLA", "AAPL", "NVDA", "MSFT"), PostView.cashtags(post("1").copy(entities = tags), quoted = false))
        assertEquals(emptyList(), PostView.cashtags(post("1").copy(entities = tags), quoted = true))
    }

    @Test
    fun translationIsOfferedForAPostInAnotherLanguageButNeverForAnArticle() {
        assertTrue(PostView.offersTranslation(post("1").copy(lang = "de"), "en"))
        assertFalse(PostView.offersTranslation(post("1").copy(lang = "en"), "en"))
        assertFalse(PostView.offersTranslation(post("1").copy(lang = "de", article = XArticle("1", "T", "P", null)), "en"))
        assertFalse(PostView.offersTranslation(post("1").copy(lang = "zxx"), "en"))
    }

    @Test
    fun aBroadcastShowsAsItsOwnBlockUnlessItsRecordingIsThePostsVideo() {
        val video = XMedia(XMedia.Kind.VIDEO, "https://pbs.twimg.com/v.jpg", 16, 9)
        assertTrue(PostView.showsLive(post("1").copy(live = XLive(broadcastId = "1abc"))))
        assertFalse(PostView.showsLive(post("1").copy(live = XLive(broadcastId = "1abc"), media = listOf(video))))
        assertTrue(PostView.showsLive(post("1").copy(live = XLive(spaceId = "1room"), media = listOf(video))), "a Space is audio: its block stays")
        assertFalse(PostView.showsLive(post("1")))
    }

    @Test
    fun onlyAPostMarkedSensitiveWithMediaIsGated() {
        val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/p.jpg", 1, 1)
        assertTrue(PostView.gated(post("1").copy(sensitive = true, media = listOf(photo)), hidden = true))
        assertFalse(PostView.gated(post("1").copy(sensitive = true, media = listOf(photo)), hidden = false))
        assertFalse(PostView.gated(post("1").copy(sensitive = true), hidden = true), "nothing to hide")
        assertFalse(PostView.gated(post("1").copy(media = listOf(photo)), hidden = true))
    }

    @Test
    fun theFooterCountsRepostsAndQuotesTogetherAndHidesEverythingInZenOrCalm() {
        val counted = post("1").copy(replyCount = 3, repostCount = 4, quoteCount = 2, likeCount = 9, viewCount = 100)
        assertEquals(FooterCounts(3, 6, 9, 100), FooterCounts.of(counted, PostPrefs(), showViews = true))
        assertEquals(FooterCounts(3, 6, 9, null), FooterCounts.of(counted, PostPrefs(), showViews = false), "an opened post says its views above")
        assertEquals(FooterCounts(0, 0, 0, null), FooterCounts.of(post("2").copy(viewCount = 0), PostPrefs(), showViews = true), "no views item without views")
        assertEquals(FooterCounts(null, null, null, null), FooterCounts.of(counted, PostPrefs(zen = true), showViews = true))
    }

    @Test
    fun anAuthorIsHiddenInNonConfirmationBiasModeExceptOnTheirOwnProfile() {
        val maya = post("1", by = "maya")
        assertTrue(PostView.showsAuthor(maya, PostPrefs(), null))
        assertFalse(PostView.showsAuthor(maya, PostPrefs(hideAuthors = true), null))
        assertTrue(PostView.showsAuthor(maya, PostPrefs(hideAuthors = true), "MAYA"))
        assertEquals("https://fx.example/maya/status/1", PostView.url(maya, PostPrefs(shareBaseUrl = "https://fx.example/")))
    }

    @Test
    fun runsOfTwoOrMoreRepostsFoldIntoOneRow() {
        fun boost(id: String) = thread(post(id).copy(reposted = post("o$id", by = "x")))
        val a = thread(post("1"))
        val b = boost("2")
        val c = boost("3")
        val d = thread(post("4"))
        val e = boost("5")
        val items = BoostRuns.collapse(listOf(a, b, c, d, e))
        assertEquals(
            listOf(BoostRuns.Item.Single(a), BoostRuns.Item.Run(listOf(b, c)), BoostRuns.Item.Single(d), BoostRuns.Item.Single(e)),
            items,
            "a lone repost stays a post",
        )
        assertEquals("boosts-2", (items[1] as BoostRuns.Item.Run).key)
        assertEquals(">,,< fine", BoostRuns.preview("&gt;,,&lt;\nfine"))
        assertEquals(78, BoostRuns.preview("x".repeat(200)).length)
    }

    @Test
    fun theSortsAskXForWhatItRanksItselfAndRepostersSortByFollowersOnlyWithCounts() {
        assertEquals(XReplyRanking.RELEVANCE, Sorts.ranking(ReplySort.RELEVANT))
        assertEquals(XReplyRanking.RECENCY, Sorts.ranking(ReplySort.RECENT))
        assertEquals(XReplyRanking.LIKES, Sorts.ranking(ReplySort.MOST_LIKED))
        assertEquals(XReplyRanking.RELEVANCE, Sorts.ranking(ReplySort.OLDEST), "X has no oldest-first: its default")
        assertEquals(XQuoteOrder.TOP, Sorts.quoteOrder(QuoteSort.TOP))
        assertEquals(XQuoteOrder.LATEST, Sorts.quoteOrder(QuoteSort.MOST_LIKED))
        assertEquals(QuoteSort.RECENT, Sorts.effective(QuoteSort.OLDEST, Sorts.X_QUOTES))

        val people = listOf(user("a", 10), user("b"), user("c", 99))
        assertEquals(listOf(ReposterSort.RECENT, ReposterSort.MOST_FOLLOWERS), Sorts.reposterSorts(people))
        assertEquals(listOf(ReposterSort.RECENT), Sorts.reposterSorts(listOf(user("b"))))
        assertEquals(listOf("c", "a", "b"), Sorts.sortReposters(people, ReposterSort.MOST_FOLLOWERS).map { it.id })
        assertEquals(listOf("a", "b", "c"), Sorts.sortReposters(people, ReposterSort.RECENT).map { it.id })

        Sorts.selectReplies(ReplySort.MOST_LIKED)
        assertEquals(ReplySort.MOST_LIKED, Sorts.state.value.replies, "kept for the rest of the run")
        Sorts.selectReplies(ReplySort.RELEVANT)
    }

    @Test
    fun aKeptTranslationExpiresAndTheOldestGoesFirst(): Unit = runBlocking {
        var now = 0L
        val memory = TranslationMemory(max = 2) { now }
        memory.write("a", "A", ttlMillis = 10)
        memory.write("b", "B", ttlMillis = 100)
        memory.write("c", "C", ttlMillis = 100)
        assertNull(memory.read("a"), "past its size the oldest goes")
        now = 50
        assertEquals("B", memory.read("b"))
        now = 200
        assertNull(memory.read("c"), "a week at most")

        assertEquals(TranslationProblem.TOO_MANY, TranslationProblem.of(XTranslationException(XTranslationException.Kind.TOO_MANY_REQUESTS, "")))
        assertEquals(TranslationProblem.OTHER, TranslationProblem.of(IllegalStateException()))
    }

    @Test
    fun aKeptEntryReadsWhenNotExpired(): Unit = runBlocking {
        val memory = TranslationMemory { 0 }
        memory.write("k", "v", ttlMillis = 1)
        assertEquals("v", memory.read("k"))
    }

    @Test
    fun aLinkCardIsLargeOnlyWhereItsPictureIsTheLink() {
        fun card(url: String, kind: String?, image: String? = "https://pbs.twimg.com/card.jpg", large: Boolean = true) =
            XCard.Link(url, "T", null, null, image, null, null, large = large, kind = kind)
        assertFalse(PostView.drawsLargeCard(card("https://news.example/story", "summary_large_image")), "a story's share image is decoration")
        assertTrue(PostView.drawsLargeCard(card("https://www.youtube.com/watch?v=1", "summary_large_image")))
        assertFalse(PostView.drawsLargeCard(card("https://www.youtube.com/watch?v=1", "player")), "a player is a row beside its picture")
        assertFalse(PostView.drawsLargeCard(card("https://news.example/story", "link")), "another network's article link, as XTA draws every network's")
        assertTrue(PostView.drawsLargeCard(card("https://example.com/buy", null)), "a website card X builds itself")
        assertFalse(PostView.drawsLargeCard(card("https://www.youtube.com/watch?v=1", "summary_large_image", image = null)))
        assertFalse(PostView.drawsLargeCard(card("https://news.example/story", "summary", large = false)))
    }
}
