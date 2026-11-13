package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsCommunityBadge
import com.aimdi.xtanative.networks.threads.ThreadsFediverse
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPodcast
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfileFacts
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.networks.threads.ThreadsTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Round 20 as the screens take it: what a kept post and a kept profile keep, the badges' words, and the Media tab's grid. */
class ThreadsGuestViewTest {
    private fun post(id: String, media: Int) = ThreadsPost(
        id = id,
        handle = "zuck",
        authorName = "Mark",
        text = "post $id",
        media = (1..media).map { ThreadsMedia("https://cdn.example/$id-$it.jpg") },
    )

    @Test
    fun aKeptPostKeepsItsBadgeItsVanishedQuoteAndItsPlays() {
        val facts = ThreadsPostFacts(communityBadge = ThreadsCommunityBadge.MEMBER, quoteUnavailable = true, playCount = 1_234)
        assertEquals(facts, ThreadsFactsJson.facts(J(ThreadsFactsJson.json(facts))))
        assertEquals(ThreadsPostFacts.NONE, ThreadsFactsJson.facts(J(ThreadsFactsJson.json(ThreadsPostFacts(communityBadge = null)))))
        val odd = J.parse("""{"communityBadge":"EMPEROR","playCount":-3}""")
        assertEquals(ThreadsPostFacts.NONE, ThreadsFactsJson.facts(odd), "a badge not known and a count below nothing are not kept")
    }

    @Test
    fun aKeptProfileKeepsItsTagsPodcastAndFediverseAddress() {
        val facts = ThreadsProfileFacts(
            tags = listOf(ThreadsTopic("Made-up Club", "🎭", null, true, null), ThreadsTopic("running", iconUrl = "https://cdn.example/r.png")),
            podcast = ThreadsPodcast("Made-up Cast", "https://pod.example/1", "Spotify"),
            fediverse = ThreadsFediverse("@zuck@threads.net"),
        )
        val page = ThreadsProfilePage(ThreadsProfile(pk = "1", id = "1", username = "zuck", fullName = "Mark", facts = facts))
        val kept = ThreadsProfilesKept.pageOf(J(ThreadsProfilesKept.json(page)))
        assertEquals(facts, kept?.profile?.facts)
        val bare = ThreadsProfilesKept.pageOf(J(ThreadsProfilesKept.json(ThreadsProfilePage(ThreadsProfile(pk = "1", id = "1", username = "zuck", fullName = "Mark")))))
        assertEquals(ThreadsProfileFacts(), bare?.profile?.facts, "a profile without them keeps none")
    }

    @Test
    fun everyBadgeHasItsOwnWords() {
        val words = ThreadsCommunityBadge.entries.map(::badgeLabel)
        assertEquals(words.size, words.toSet().size)
    }

    @Test
    fun theMediaGridIsOneSquarePerPostWithMediaInRowsOfThree() {
        val posts = listOf(post("a", 1), post("b", 0), post("c", 3), post("d", 1), post("e", 2))
        val tiles = ThreadsMediaGrid.tiles(posts)
        assertEquals(listOf("a", "c", "d", "e"), tiles.map { it.key }, "a post without media has no square")
        assertEquals("https://cdn.example/c-1.jpg", tiles[1].media.url, "a post's first picture")
        assertEquals(listOf(false, true, false, true), tiles.map { it.more })
        val rows = ThreadsMediaGrid.rows(tiles)
        assertEquals(listOf(3, 1), rows.map { it.size })
        assertTrue(ThreadsMediaGrid.rows(emptyList()).isEmpty())
        assertEquals(4, ThreadsMediaGrid.rows(tiles, columns = 0).size, "a column count below one is one")
    }
}
