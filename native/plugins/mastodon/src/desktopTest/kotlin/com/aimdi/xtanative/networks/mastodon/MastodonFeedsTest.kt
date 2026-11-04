package com.aimdi.xtanative.networks.mastodon

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Putting posts together, with the rules of XTA's mastodon_feed_reliability_test.dart and mastodon_timeline_controls_test.dart. */
class MastodonFeedsTest {
    private fun day(day: Int): Instant = Instant.parse("2026-09-%02dT00:00:00Z".format(day))

    private fun post(
        id: String,
        host: String = "origin.example",
        url: String? = null,
        boosted: Boolean = false,
        timelineAt: Instant? = null,
        publishedAt: Instant? = day(1),
        replyToId: String? = null,
    ) = MastodonPost(
        id = id,
        acct = "reader@$host",
        authorName = "Reader",
        text = id,
        url = url ?: "https://$host/@reader/$id",
        boosted = boosted,
        timelineAt = timelineAt,
        publishedAt = publishedAt,
        replyToId = replyToId,
    )

    // ---- merging ----

    @Test
    fun uniqueKeepsTheOrderAndDropsWhatIsAlreadyThere() {
        assertEquals(listOf("3", "2", "1"), MastodonFeeds.unique(listOf(post("3"), post("2")), listOf(post("2"), post("1"))).map { it.id })
    }

    @Test
    fun copiesOnOtherInstancesCollapseButTheSameIdOnAnotherOriginStays() {
        val original = post("local-id", url = "https://origin.example/@reader/status")
        val remote = post("remote-id", url = "https://ORIGIN.example/@reader/status/")
        val different = post("local-id", host = "another.example")
        assertEquals(listOf(original, different), MastodonFeeds.unique(listOf(original, original), listOf(remote, different)))
    }

    @Test
    fun anOriginalTakesTheFirstPlaceOfABoostOfIt() {
        val original = post("shared")
        val boost = post("shared", boosted = true, timelineAt = day(3))
        val other = post("other")
        listOf(true, false).forEach { boostFirst ->
            val (first, second) = if (boostFirst) boost to original else original to boost
            assertEquals(listOf(original, other), MastodonFeeds.unique(listOf(first, other), listOf(second)), "boost first: $boostFirst")
        }
    }

    @Test
    fun pinsComeFirstWithoutRepeating() {
        assertEquals(listOf("1", "2"), MastodonFeeds.withPinned(listOf(post("1")), listOf(post("2"), post("1"))).map { it.id })
    }

    // ---- shared timelines ----

    @Test
    fun aSharedFeedTakesRepliesAndBoostsAsItsSettingsSay() {
        val plain = post("plain")
        val reply = post("reply", replyToId = "parent")
        val boostOfAReply = post("boost", boosted = true, replyToId = "parent")
        val posts = listOf(plain, reply, boostOfAReply)
        assertEquals(listOf("plain", "boost"), MastodonFeeds.interleaved(posts, replies = false).map { it.id }, "a boost follows the repost setting")
        assertEquals(listOf("plain", "reply"), MastodonFeeds.interleaved(posts, reposts = false).map { it.id })
        assertEquals(listOf("plain"), MastodonFeeds.interleaved(posts, replies = false, reposts = false).map { it.id })
    }

    @Test
    fun aSharedFeedTakesTenPostsAndOnlyDatedOnes() {
        val posts = listOf(post("undated", publishedAt = null)) + (1..11).map { post("p$it") }
        val taken = MastodonFeeds.interleaved(posts)
        assertEquals((1..9).map { "p$it" }, taken.map { it.id }, "the undated post counts against the ten, then is left out")
    }

    // ---- local filters ----

    private fun entry(id: String, text: String? = null, boost: Boolean = false, reply: Boolean = false, media: Boolean = false, day: Int? = null, boostedDay: Int? = null, url: String? = null) =
        MastodonPost(
            id = id,
            acct = "a@social.example",
            authorName = "Alice",
            text = text ?: id,
            url = url ?: "https://social.example/@a/$id",
            boosted = boost,
            replyToId = if (reply) "parent" else null,
            media = if (media) listOf(MastodonMedia(MastodonMedia.Kind.IMAGE, "https://media.example/$id.png")) else emptyList(),
            publishedAt = day?.let(::day),
            timelineAt = boostedDay?.let(::day),
        )

    @Test
    fun filtersUseEveryQueryWordAndKeepTheInstancesOrder() {
        val posts = listOf(
            entry("boost", text = "alice flower garden", boost = true, media = true),
            entry("reply", text = "flower garden", reply = true, media = true),
            entry("plain", text = "flower garden"),
            entry("match", text = "A FLOWER in the garden", media = true),
            entry("miss", text = "flower", media = true),
        )
        val filter = MastodonTimelineFilter(query = "  garden  flower Alice ", content = MastodonTimelineFilter.Content.MEDIA, hideBoosts = true, hideReplies = true)
        assertEquals(listOf("match"), filter.apply(posts).map { it.id })
        assertEquals(4, filter.activeFilters)
        assertEquals(0, MastodonTimelineFilter(order = MastodonTimelineFilter.Order.NEWEST).activeFilters)
    }

    @Test
    fun linksMatchAddressesInTheTextAndPreviewCardsButNotOrdinaryText() {
        val posts = listOf(
            entry("url", text = "https://example.org"),
            entry("text"),
            MastodonPost(id = "card", acct = "a", authorName = "A", text = "", url = "https://social.example/3", linkCard = MastodonLinkCard("https://example.org", "A card")),
        )
        assertEquals(listOf("url", "card"), MastodonTimelineFilter(content = MastodonTimelineFilter.Content.LINKS).apply(posts).map { it.id })
    }

    @Test
    fun datesSortByBoostTimeWithStableTiesAndUndatedLast() {
        val posts = listOf(entry("unknown"), entry("old", day = 1), entry("boost", day = 1, boostedDay = 9), entry("same-a", day = 3), entry("same-b", day = 3))
        assertEquals(listOf("old", "same-a", "same-b", "boost", "unknown"), MastodonTimelineFilter(order = MastodonTimelineFilter.Order.OLDEST).apply(posts).map { it.id })
        assertEquals(listOf("boost", "same-a", "same-b", "old", "unknown"), MastodonTimelineFilter(order = MastodonTimelineFilter.Order.NEWEST).apply(posts).map { it.id })
        assertEquals("unknown", posts.first().id, "the posts given are left as they were")
    }

    @Test
    fun copiesCollapseWithoutCollidingAcrossHosts() {
        val posts = listOf(entry("one"), entry("duplicate", url = "https://SOCIAL.example/@a/one/?tracking=1"), entry("one", url = "https://other.example/@a/one"))
        assertEquals(2, MastodonTimelineFilter().apply(posts).size)
    }

    @Test
    fun hidingBoostsKeepsTheFollowedAuthorsOwnPost() {
        val original = post("shared")
        val boost = post("shared", boosted = true, timelineAt = day(3))
        val other = post("other", publishedAt = day(2))
        val merged = MastodonFeeds.unique(emptyList(), listOf(boost, original, other))
        assertEquals(listOf(original, other), MastodonTimelineFilter(hideBoosts = true).apply(merged))
    }
}
