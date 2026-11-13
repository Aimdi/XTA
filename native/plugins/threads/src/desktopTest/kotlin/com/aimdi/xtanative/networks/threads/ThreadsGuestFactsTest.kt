package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round 20: what a guest is shown on posts and profiles beside round 19's
 * content, read from the keys CI saw (`community_badge.badge_type` MEMBER,
 * `profile_tags.edges[].node`, `platform_podcast_info`) and under the
 * likely names of those it has not; each harmless when gone. Every value is
 * made up.
 */
class ThreadsGuestFactsTest {
    private fun read(info: Map<String, Any?>? = null, extra: Map<String, Any?> = emptyMap(), text: String = "words"): ThreadsPost? =
        ThreadsParser.post(j(postJson("1", text = text, info = info, extra = extra)))

    @Test
    fun aMembersBadgeInACommunityIsReadAsCiSawItAndAnUnknownBadgeIsNone() {
        val member = read(info = mapOf("community_badge" to mapOf("badge_type" to "MEMBER")))!!
        assertEquals(ThreadsCommunityBadge.MEMBER, member.facts.communityBadge)
        assertEquals(ThreadsCommunityBadge.FOUNDER, ThreadsCommunityBadge.of("community_founder"))
        assertEquals(ThreadsCommunityBadge.MODERATOR, ThreadsCommunityBadge.of("ADMIN"))
        assertEquals(ThreadsCommunityBadge.CHAMPION, ThreadsCommunityBadge.of("Champion"))
        assertEquals(ThreadsCommunityBadge.TOP_CONTRIBUTOR, ThreadsCommunityBadge.of("TOP_CONTRIBUTOR"))
        assertNull(ThreadsCommunityBadge.of("NONE"))
        assertNull(ThreadsCommunityBadge.of("SOMETHING_NEW"), "a word not known draws nothing")
        assertNull(ThreadsCommunityBadge.of(null))
        assertNull(read(info = mapOf("community_badge" to mapOf("badge_type" to 3)))!!.facts.communityBadge)
        assertNull(read(info = mapOf("community_badge" to null))!!.facts.communityBadge)
        assertEquals(ThreadsCommunityBadge.MEMBER, read(extra = mapOf("community_badge" to "member"))!!.facts.communityBadge, "a bare word on the post")
        assertTrue(read()!!.facts.isEmpty, "a post without the key has no badge")
    }

    @Test
    fun aQuoteThreadsNoLongerShowsIsMarkedAndKeepsAPostWithNothingElse() {
        val gone = mapOf("share_info" to mapOf("quoted_attachment_post_unavailable" to true, "can_quote_attachment" to true))
        val post = read(info = gone, text = "")
        assertTrue(post != null && post.facts.quoteUnavailable, "a post that only quoted the vanished one still shows")
        assertNull(post.quoted)
        assertTrue(read(info = mapOf("share_info" to mapOf("quoted_post_unavailable" to true)))!!.facts.quoteUnavailable)
        assertFalse(read(info = mapOf("share_info" to mapOf("quoted_attachment_post_unavailable" to false)))!!.facts.quoteUnavailable)
        assertFalse(read(info = mapOf("share_info" to mapOf("reposted_post_unavailable" to true)))!!.facts.quoteUnavailable, "only a quote's flag")
        assertNull(read(info = mapOf("share_info" to mapOf("quoted_attachment_post_unavailable" to false)), text = ""), "nothing to show is still nothing")

        val quoted = postJson("2", user = "other", text = "still here")
        val shown = read(info = mapOf("share_info" to mapOf("quoted_post" to quoted, "quoted_post_unavailable" to true)))!!
        assertEquals("still here", shown.quoted?.text)
        assertFalse(shown.facts.quoteUnavailable, "a quote that is there is drawn, whatever the flag")
    }

    @Test
    fun aVideosPlaysAreTheLargestCountOnThePostOrItsCarouselAndNeverMadeUp() {
        val carousel = listOf(mapOf("pk" to "a", "play_count" to 120), mapOf("pk" to "b", "video_play_count" to "4500"), mapOf("pk" to "c", "play_count" to -1))
        assertEquals(4_500L, ThreadsGuestMarks.playCount(j(postJson("1", extra = mapOf("carousel_media" to carousel)))))
        assertEquals(77L, read(extra = mapOf("play_count" to 77))!!.facts.playCount)
        assertNull(read(extra = mapOf("play_count" to "many"))!!.facts.playCount)
        assertNull(read()!!.facts.playCount)
    }

    @Test
    fun aProfilesTagsAreReadAsCiSawThemEachOpeningItsTopicOrCommunity() {
        val user = j(
            mapOf(
                "username" to "zuck",
                "profile_tags" to mapOf(
                    "edges" to listOf(
                        mapOf("node" to mapOf("display_name" to "Cosplayers of Threads", "community_favicon_url" to "https://cdn.example/c.png", "id" to "123456", "is_community" to false)),
                        mapOf("node" to mapOf("display_name" to "mma", "tag_cluster_name" to "MMA", "community_emoji" to "🥊")),
                        mapOf("node" to mapOf("display_name" to "photography", "community_favicon_url" to "http://cdn.example/p.png")),
                        mapOf("node" to mapOf("display_name" to "Cosplayers of Threads")),
                        mapOf("node" to mapOf("x" to 1)),
                    ),
                ),
            ),
        )
        val tags = ThreadsProfileExtras.tags(user)
        assertEquals(listOf("Cosplayers of Threads", "MMA", "photography"), tags.map { it.name }, "each once, a slug giving way to its cluster's name, a nameless one left out")
        assertTrue(tags[0].community && tags[0].iconUrl == "https://cdn.example/c.png", "a favicon tells a community")
        assertEquals("🥊", tags[1].emoji)
        assertFalse(tags[2].community, "a favicon over plain http is no mark")
        assertNull(tags[2].iconUrl)
        assertEquals(listOf("Running"), ThreadsProfileExtras.tags(j(mapOf("profile_tags" to listOf(mapOf("tag" to mapOf("name" to "#Running")))))).map { it.name }, "a plain list, a nested tag")
        assertTrue(ThreadsProfileExtras.tags(j(mapOf("profile_tags" to listOf(mapOf("x" to 1))))).isEmpty())
        assertTrue(ThreadsProfileExtras.tags(j(mapOf("profile_tags" to "none"))).isEmpty())
    }

    @Test
    fun aProfilesPodcastIsNamedAndOpensOnlyOverHttps() {
        val podcast = ThreadsProfileExtras.podcast(j(mapOf("platform_podcast_info" to mapOf("podcast_name" to "Made-up Show", "url" to "https://l.threads.com/?u=https%3A%2F%2Fpodcasts.example%2Fshow&e=x", "platform" to "APPLE_PODCASTS"))))
        assertEquals(ThreadsPodcast("Made-up Show", "https://podcasts.example/show", "Apple Podcasts"), podcast, "Meta's redirect unwrapped, the platform as people write it")
        val nested = ThreadsProfileExtras.podcast(j(mapOf("platform_podcast_info" to mapOf("podcast" to mapOf("show_url" to "https://open.example/show/1")))))
        assertEquals("open.example", nested?.label, "without a name or platform, the host")
        assertEquals("Spotify", ThreadsProfileExtras.podcast(j(mapOf("platform_podcast_info" to mapOf("title" to "x", "platform" to "spotify"))))?.platform)
        assertNull(ThreadsProfileExtras.podcast(j(mapOf("platform_podcast_info" to mapOf("url" to "http://plain.example/")))), "no https address and no name")
        assertNull(ThreadsProfileExtras.podcast(j(mapOf("platform_podcast_info" to emptyMap<String, Any>()))))
        assertNull(ThreadsProfileExtras.podcast(j(mapOf("username" to "zuck"))))
    }

    @Test
    fun fediverseSharingIsShownOnlyWhereAKeySaysSoWithTheAddressThreadsFederatesUnder() {
        val on = ThreadsProfileExtras.fediverse(j(mapOf("is_fediverse_sharing_enabled" to true)), "Zuck")
        assertEquals(ThreadsFediverse("@zuck@threads.net"), on)
        assertEquals("@zuck@threads.net", ThreadsProfileExtras.fediverse(j(mapOf("fediverse_info" to mapOf("is_enabled" to true))), "zuck")?.address)
        assertEquals("@mark@threads.net", ThreadsProfileExtras.fediverse(j(mapOf("fediverse_info" to mapOf("handle" to "mark@threads.net"))), "zuck")?.address, "an address Meta gives")
        assertNull(ThreadsProfileExtras.fediverse(j(mapOf("is_fediverse_sharing_enabled" to false)), "zuck"))
        assertNull(ThreadsProfileExtras.fediverse(j(mapOf("is_eligible_for_fediverse" to true, "show_fediverse_nux" to true)), "zuck"), "being eligible or prompted is not sharing")
        assertNull(ThreadsProfileExtras.fediverse(j(mapOf("fediverse_info" to mapOf("is_enabled" to false))), "zuck"))
        assertNull(ThreadsProfileExtras.fediverse(j(mapOf("username" to "zuck")), "zuck"))
    }

    @Test
    fun theOwnersObjectCarriesItsTagsPodcastAndFediverseIntoTheProfile() {
        val page = fixture("profile_owner_page.html")
        assertTrue(ThreadsProfileOwner.owner(page, "zuck")!!.facts.tags.isEmpty(), "the fixture's tag names nothing")
        val node = """{"node":{"display_name":"Made-up Club","community_emoji":"🎭","id":"98765"}}"""
        val richer = page.replace(
            "\"profile_tags\":[{\"x\":1}]",
            "\"profile_tags\":{\"edges\":[$node]},\"platform_podcast_info\":{\"title\":\"Made-up Cast\",\"url\":\"https://pod.example/1\"},\"fediverse_info\":{\"is_enabled\":true}",
        )
        val profile = ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(richer, "zuck"), richer, "zuck")!!
        assertEquals(listOf(ThreadsTopic("Made-up Club", "🎭", null, true, null)), profile.facts.tags)
        assertEquals(ThreadsPodcast("Made-up Cast", "https://pod.example/1"), profile.facts.podcast)
        assertEquals(ThreadsFediverse("@zuck@threads.net"), profile.facts.fediverse)
        assertEquals(5_812_345L, profile.followerCount, "the rest is read as before")
    }
}
