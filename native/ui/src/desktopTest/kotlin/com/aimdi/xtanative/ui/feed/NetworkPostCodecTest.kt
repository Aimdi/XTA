package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.store.feed.FeedSnapshots
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.TextRun
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class NetworkPostCodecTest {
    private val full = NetworkPost(
        id = "p1",
        authorId = "did:1",
        authorName = "Amy",
        authorHandle = "amy.bsky.social",
        avatarUrl = "https://a/avatar.jpg",
        createdAt = Instant.ofEpochMilli(1_700_000_000_123),
        text = listOf(TextRun.Plain("hi "), TextRun.Link("site", "https://s"), TextRun.Mention("@bo", "did:2"), TextRun.Tag("#k", "#k")),
        media = listOf(MediaItem("https://a/p.jpg", MediaItem.Kind.VIDEO, videoUrl = "https://a/v.mp4", aspectRatio = 1.5f, alt = "alt", durationMs = 3_000, downloadUrl = "https://a/o.mp4")),
        card = LinkCard("https://s", "Title", "Words", "https://s/i.jpg"),
        quoted = NetworkPost("q", "did:3", "Cy", "cy", text = listOf(TextRun.Plain("quoted"))),
        replies = 1,
        reposts = 2,
        quotes = 3,
        likes = 4,
        views = 5,
        repostedBy = "Dee",
        pinned = true,
        replyingTo = "bo",
        warning = "spoiler",
        url = "https://bsky.app/p1",
        coversText = true,
        title = "A title",
    )

    @Test
    fun aPostComesBackAsItWasShown() {
        assertEquals(full, NetworkPostCodec.decode(NetworkPostCodec.encode(full)))
        val bare = NetworkPost("p", "a", "A", "a")
        assertEquals(bare, NetworkPostCodec.decode(NetworkPostCodec.encode(bare)))
    }

    @Test
    fun aRecordItCannotReadIsNoPost() {
        assertNull(NetworkPostCodec.decode(JsonPrimitive("nope")))
    }

    @Test
    fun theDevicesCopyKeepsEachQuestionApartAndReadsBack() = runBlocking {
        val snapshots = StoredSnapshots(FeedSnapshots(StoreDir(Files.createTempDirectory("snapshots").toFile())))
        val group = SourceAsk(listOf("b", "a"))
        snapshots.write("bluesky", group, listOf(full, NetworkPost("undated", "a", "A", "a")))
        assertEquals(listOf(full), snapshots.read("bluesky", SourceAsk(listOf("a", "b")))?.posts, "the same accounts in another order are the same question")
        assertNull(snapshots.read("bluesky", SourceAsk(followed = true)))
        assertNotEquals(StoredSnapshots.keyOf("bluesky", group), StoredSnapshots.keyOf("bluesky", group.copy(kinds = FeedKinds(replies = false))))
    }
}
