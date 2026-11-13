package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonFollow
import com.aimdi.xtanative.networks.mastodon.MastodonMedia
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonSnapshot
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.plugins.FeedKinds
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Mastodon in the app's shared timelines and its Saved, as XTA keeps them. */
class MastodonSharedTest {
    private fun post(id: String, reply: Boolean = false, boosted: Boolean = false, at: Long? = 1_000) = MastodonPost(
        id = id,
        acct = "alice@example.social",
        authorName = "Alice",
        text = "post $id",
        url = "https://example.social/@alice/$id",
        publishedAt = at?.let(Instant::ofEpochSecond),
        replyToId = if (reply) "0" else null,
        boosted = boosted,
        boostedBy = if (boosted) "Bob" else null,
    )

    @Test
    fun aMixedFeedTakesTenInAllOfTheKindsItTakesWithADate() {
        val posts = (1..12).map { post("$it") }
        assertEquals((1..10).map { "$it" }, MastodonShared.mixed(posts, FeedKinds.ALL).map { it.id })
        val kinds = FeedKinds(replies = false, reposts = false)
        val mixed = MastodonShared.mixed(listOf(post("r", reply = true), post("b", boosted = true), post("own"), post("undated", at = null)), kinds)
        assertEquals(listOf("own"), mixed.map { it.id }, "a boost is a repost, and hidden kinds use none of the ten")
    }

    @Test
    fun theSharedRowHoldsAContentWarningsPostBackBehindItsWordsAndSaysWhoBoostedIt() {
        val warned = MastodonShared.post(post("1").copy(spoilerText = "spoilers", media = listOf(MastodonMedia(MastodonMedia.Kind.IMAGE, "https://example.social/a.jpg"))), "en")
        assertEquals("spoilers", warned.warning)
        assertTrue(warned.coversText)
        assertEquals("post 1", warned.text.joinToString("") { it.text }, "the text is there for the reader to open")
        assertEquals(1, warned.media.size)

        val sensitive = MastodonShared.post(post("2", boosted = true).copy(sensitive = true), "en")
        assertEquals(Strings.sensitive.get("en"), sensitive.warning)
        assertEquals(false, sensitive.coversText, "only the media waits")
        assertEquals("Bob", sensitive.repostedBy)
        assertNull(MastodonShared.post(post("3"), "en").warning)
    }

    @Test
    fun theSharedRowSaysWhoBoostedAndThatItWasEditedAsXtasCardDoes() {
        assertEquals("Bob boosted", SharedLines.boosted(post("1", boosted = true), "en"))
        assertNull(SharedLines.boosted(post("2"), "en"))
        assertEquals("Edited", SharedLines.edited(post("3").copy(editedAt = Instant.ofEpochSecond(2_000)), "en"))
        assertNull(SharedLines.edited(post("4"), "en"))
    }

    @Test
    fun aSavedPostIsXtasMastodonRecordUnderItsAddress() {
        val post = post("9").copy(media = listOf(MastodonMedia(MastodonMedia.Kind.IMAGE, "https://example.social/a.jpg", "https://example.social/a-full.jpg")))
        val archive = MastodonShared.archive(post)
        assertEquals("mastodon:https://example.social/@alice/9", archive.id)
        assertEquals("alice@example.social", archive.authorId)
        val kept = SavedContent.of(archive.content)
        assertEquals(SavedContent.Kind.MASTODON, kept.kind)
        assertEquals("mastodon", kept.network)
        assertEquals(post, MastodonSnapshot.fromArchive(archive.content), "as XTA reads it back")
    }

    @Test
    fun anAccountPutInAGroupIsFollowedOnceUnderItsAddress(): Unit = runBlocking {
        val folder = Files.createTempDirectory("mastodon-follows").toFile().apply { deleteOnExit() }
        val follows = MastodonFollowsStore(StoreFile(folder.resolve("follows.json")))
        follows.follow(MastodonFollow("Alice@Example.social", "Alice", createdAt = "2026-11-04 08:00:00"))
        follows.follow(MastodonFollow("alice@example.social", "Alice B", "https://example.social/a.png", createdAt = "2026-11-04 09:00:00"))
        assertEquals(listOf("Alice B"), follows.load().map { it.name }, "the same account, whatever the case of its address")
        assertTrue(follows.follows("ALICE@example.social"))
    }
}
