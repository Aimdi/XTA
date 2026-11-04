package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.mastodon.MastodonClient
import com.aimdi.xtanative.networks.mastodon.MastodonLinkCard
import com.aimdi.xtanative.networks.mastodon.MastodonMedia
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonProfile
import com.aimdi.xtanative.networks.mastodon.MastodonReadPoint
import com.aimdi.xtanative.networks.mastodon.MastodonTag
import com.aimdi.xtanative.networks.mastodon.MastodonTimeline
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineFilter
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineState
import com.aimdi.xtanative.store.settings.MastodonPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.TextRun
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The plugin's own rules: its routes and links, its settings, how a post is drawn, a timeline's rows and the orders of its lists. */
class MastodonPluginRulesTest {
    private val post = MastodonPost(
        id = "9",
        acct = "alice@example.social",
        authorName = "Alice",
        text = "Hi @bob and #cats https://news.example/a",
        url = "https://example.social/@alice/9",
        mentionAccts = listOf("bob@other.social"),
        media = listOf(MastodonMedia(MastodonMedia.Kind.VIDEO, "https://example.social/poster.jpg", "https://example.social/v.mp4", "a clip", durationSeconds = 2.5)),
        linkCard = MastodonLinkCard("https://news.example/a", "A title"),
        quote = MastodonPost(id = "1", acct = "dan@x.social", authorName = "Dan", text = "quoted", url = "https://x.social/@dan/1", spoilerText = "cw"),
    )

    @Test
    fun routesCarryWhatTheirScreensOpenWith() {
        assertEquals(MastodonRoute.Thread(MastodonTarget(post.url, post.acct, post.id)), MastodonRoutes.parse(MastodonRoutes.thread(post)))
        assertEquals(MastodonRoute.Profile("alice@example.social"), MastodonRoutes.parse(MastodonRoutes.profile("alice@example.social")))
        assertEquals(MastodonRoute.Tag("cats"), MastodonRoutes.parse(MastodonRoutes.tag("#cats")))
        assertEquals(MastodonRoute.Reposts(MastodonTarget.of(post)), MastodonRoutes.parse(MastodonRoutes.reposts(post)))
        assertEquals(MastodonRoute.Quotes(MastodonTarget.of(post)), MastodonRoutes.parse(MastodonRoutes.quotes(post)))
        assertEquals(MastodonRoute.Saved, MastodonRoutes.parse(MastodonRoutes.saved()))
        assertNull(MastodonRoutes.parse("thread?url=https%3A%2F%2Fx"))
        assertNull(MastodonRoutes.parse("elsewhere"))
    }

    @Test
    fun linksOnKnownInstancesOpenHere() {
        val hosts = setOf("example.social")
        assertEquals(MastodonRoutes.profile("alice@example.social"), MastodonRoutes.forUrl("https://example.social/@alice", hosts))
        val thread = MastodonRoutes.parse(MastodonRoutes.forUrl("https://example.social/@alice/123", hosts)!!)
        assertEquals(MastodonRoute.Thread(MastodonTarget("https://example.social/@alice/123", "alice@example.social", "123")), thread)
        assertNull(MastodonRoutes.forUrl("https://elsewhere.example/@alice", hosts))
    }

    @Test
    fun settingsAreReadUnderXtasKeys() {
        val settings = Settings()
            .with(MastodonPrefs.instance, "https://home.social")
            .with(MastodonPrefs.instances, listOf("https://b.social"))
            .with(PostsPrefs.calmMode, true)
        val view = MastodonSettings.view(settings)
        assertEquals(listOf("https://home.social", "https://b.social"), view.configured)
        assertTrue(view.hideCounts)
        assertEquals(MastodonPrefsView(), MastodonSettings.view(null))
    }

    @Test
    fun theHomeInstanceIsKeptAsAnAddressAndOthersOnce() {
        val home = MastodonSettings.withHome(Settings(), " mastodon.social/ ")
        assertEquals("https://mastodon.social", home[MastodonPrefs.instance])
        val twice = MastodonSettings.withExtra(MastodonSettings.withExtra(home, "b.social"), "https://b.social")
        assertEquals(listOf("https://b.social"), twice[MastodonPrefs.instances])
        assertEquals(twice, MastodonSettings.withExtra(twice, "not an address"))
        assertEquals(emptyList(), MastodonSettings.withoutExtra(twice, "https://b.social")[MastodonPrefs.instances])
    }

    @Test
    fun aTimelinesFiltersAreKeptButNotItsSearch() {
        val kept = MastodonSettings.withFilter(Settings(), MastodonTimeline.LOCAL, MastodonTimelineFilter(query = "cats", hideBoosts = true))
        assertEquals(MastodonTimelineFilter(hideBoosts = true), MastodonSettings.view(kept).filter(MastodonTimeline.LOCAL))
        assertEquals(MastodonTimelineFilter(), MastodonSettings.view(kept).filter(MastodonTimeline.EXPLORE))
    }

    @Test
    fun aPostIsDrawnWithItsLinksMentionsAndTags() {
        val shown = post.toNetworkPost("en")
        assertEquals(
            listOf(
                TextRun.Plain("Hi "),
                TextRun.Mention("@bob", "bob@other.social"),
                TextRun.Plain(" and "),
                TextRun.Tag("#cats", "#cats"),
                TextRun.Plain(" "),
                TextRun.Link("https://news.example/a", "https://news.example/a"),
            ),
            shown.text,
        )
        assertEquals(post.url, shown.id)
        assertEquals(post.acct, shown.authorId)
        val video = shown.media.single()
        assertEquals(MediaItem.Kind.VIDEO, video.kind)
        assertEquals("https://example.social/v.mp4", video.videoUrl)
        assertEquals(2500L, video.durationMs)
        assertEquals(post.url, video.webUrl)
        assertEquals("cw", shown.quoted?.text?.single()?.text)
        assertEquals("Content warning", shown.quoted?.warning)
    }

    @Test
    fun aContentWarningHoldsEverythingBackUntilOpened() {
        val warned = post.copy(spoilerText = "spoilers", sensitive = true)
        val closed = warned.toNetworkPost("en")
        assertTrue(closed.text.isEmpty() && closed.media.isEmpty() && closed.card == null && closed.quoted == null)
        assertNull(closed.warning)
        val open = warned.toNetworkPost("en", open = true)
        assertEquals(1, open.media.size)
        assertEquals("A title", open.card?.title)
    }

    @Test
    fun sensitiveMediaWaitsBehindXtasNotice() {
        assertEquals("This post contains potentially sensitive content. Would you like to view it?", post.copy(sensitive = true).toNetworkPost("en").warning)
        assertNull(post.toNetworkPost("en").warning)
    }

    @Test
    fun failuresAreSaidInXtasWords() {
        assertEquals(Strings.error_rate_limited, problemText(NetworkRequestException(MastodonClient.NETWORK, 429, "slow down")))
        assertEquals(Strings.error_not_found, problemText(NetworkRequestException(MastodonClient.NETWORK, 404, "gone")))
        assertEquals(Strings.error_network, problemText(IOException("offline")))
        assertEquals(Strings.error_unauthorized, problemText(NetworkRequestException(MastodonClient.NETWORK, 422, "This method requires an authenticated user")))
        assertEquals(Strings.error_unauthorized, problemText(Problem.Refused(422)))
        assertEquals(Strings.activity_unavailable, problemText(Problem.Refused(401), activity = true))
        assertEquals(Strings.error_unauthorized, problemText(Problem.Refused(403)))
        assertEquals(Strings.error_response, problemText(Problem.Refused(500)))
    }

    @Test
    fun aTimelineSaysWhyItIsEmptyBeforeItShowsPosts() {
        assertEquals(listOf(TimelineRow.Loading), TimelineRows.of(MastodonTimelineState(), emptyList(), trending = false))
        val failure = IOException("offline")
        assertEquals(listOf(TimelineRow.Problem(failure)), TimelineRows.of(MastodonTimelineState(loaded = true, error = failure), emptyList(), false))
        assertEquals(listOf(TimelineRow.Empty), TimelineRows.of(MastodonTimelineState(loaded = true), emptyList(), false))
    }

    @Test
    fun aTimelineStacksItsNoticeTagsPostsAndFooter() {
        val failure = IOException("offline")
        val state = MastodonTimelineState(posts = listOf(post), tags = listOf(MastodonTag("cats")), loaded = true, error = failure, next = "9", instance = "https://example.social")
        assertEquals(
            listOf(TimelineRow.RefreshFailed(failure), TimelineRow.Trending, TimelineRow.Post(post), TimelineRow.LoadMore),
            TimelineRows.of(state, listOf(post), trending = true),
        )
        assertEquals(listOf(TimelineRow.Filtered), TimelineRows.of(state.copy(error = null, next = null), emptyList(), trending = false))
    }

    @Test
    fun aListOpensWhereItWasLeftAndRemembersWhereItIs() {
        val rows = TimelineRows.of(MastodonTimelineState(posts = listOf(post), loaded = true), listOf(post), false)
        assertEquals(0 to 40, TimelineRows.start(rows, MastodonReadPoint(listOf(post), post.url, -40.0)))
        assertNull(TimelineRows.start(rows, MastodonReadPoint(listOf(post), "https://elsewhere/1")))
        val place = TimelineRows.place(listOf("trending" to 0, TimelineRow.Post(post).key to -12), MastodonTimelineState(posts = listOf(post), instance = "https://example.social"))
        assertEquals(MastodonReadPoint(listOf(post), post.url, -12.0, "https://example.social"), place)
    }

    @Test
    fun quotesAndBoostersAreOrderedOnTheDevice() {
        val old = post.copy(url = "https://example.social/@alice/1", publishedAt = Instant.parse("2026-01-01T00:00:00Z"), favouritesCount = 9)
        val new = post.copy(url = "https://example.social/@alice/2", publishedAt = Instant.parse("2026-02-01T00:00:00Z"), favouritesCount = 1)
        val undated = post.copy(url = "https://example.social/@alice/3", publishedAt = null, favouritesCount = 5)
        assertEquals(listOf(new, old, undated), MastodonOrders.quotes(listOf(old, undated, new), MastodonOrder.RECENT))
        assertEquals(listOf(old, new, undated), MastodonOrders.quotes(listOf(undated, new, old), MastodonOrder.OLDEST))
        assertEquals(listOf(old, undated, new), MastodonOrders.quotes(listOf(new, old, undated), MastodonOrder.MOST_LIKED))
        val few = MastodonProfile("1", "a@x.social", "a", "A", "", "https://x.social/@a", followersCount = 3)
        val many = few.copy(acct = "b@x.social", followersCount = 300)
        assertEquals(listOf(few, many), MastodonOrders.reposters(listOf(few, many), MastodonOrder.RECENT))
        assertEquals(listOf(many, few), MastodonOrders.reposters(listOf(few, many), MastodonOrder.MOST_FOLLOWERS))
    }
}
