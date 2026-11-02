package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.core.x.model.playableUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XModelHelpersTest {
    @Test
    fun aPhotoIsAskedForAtTheSizeItIsShown() {
        assertEquals("https://pbs.twimg.com/media/GxYz?format=jpg&name=medium", photoUrl("https://pbs.twimg.com/media/GxYz.jpg", PhotoSize.MEDIUM))
        assertEquals("https://pbs.twimg.com/media/GxYz?format=png&name=small", photoUrl("https://pbs.twimg.com/media/GxYz.png?name=orig", PhotoSize.SMALL))
        assertEquals("https://example.com/a.jpg", photoUrl("https://example.com/a.jpg", PhotoSize.SMALL), "only X's image host takes the size")
    }

    @Test
    fun theVideoPlayedIsTheBestAPhoneStreamsWell() {
        val video = XMedia(
            XMedia.Kind.VIDEO, "https://pbs.twimg.com/poster.jpg", 1280, 720,
            variants = listOf(
                XVideoVariant("https://video.twimg.com/hls.m3u8", null, "application/x-mpegURL"),
                XVideoVariant("https://video.twimg.com/360.mp4", 832_000, "video/mp4"),
                XVideoVariant("https://video.twimg.com/1080.mp4", 10_368_000, "video/mp4"),
                XVideoVariant("https://video.twimg.com/720.mp4", 2_176_000, "video/mp4"),
            ),
        )
        assertEquals("https://video.twimg.com/720.mp4", video.playableUrl())
        assertEquals("https://video.twimg.com/360.mp4", video.playableUrl(maxBitrate = 100), "too light a cap still plays the lightest")
    }

    private fun post(id: String) = XPost(id, XUser("1", "a", "A"), null, "post $id")

    @Test
    fun anOpenedPostIsSplitIntoWhatItAnswersItselfAndItsReplies() {
        val page = XTimelinePage(
            listOf(XThread("1", listOf(post("1"))), XThread("2", listOf(post("2"))), XThread("m", listOf(post("3"), post("4")))),
            bottomCursor = "more",
            topCursor = null,
        )
        val conversation = XConversation.of(page, "2")
        assertEquals(listOf("1"), conversation.before.map { it.id })
        assertEquals("2", conversation.focal?.id)
        assertEquals(listOf(listOf("3", "4")), conversation.replies.map { thread -> thread.posts.map { it.id } })
        assertEquals("more", conversation.repliesCursor)

        val missing = XConversation.of(page, "9")
        assertNull(missing.focal)
        assertEquals(3, missing.replies.size)
    }

    /** What a row draws of its author: the disk keeps no profile header. */
    private fun XUser.slim() = XUser(id, screenName, name, avatarUrl, verified, protected)
    private fun XPost.slim(): XPost = copy(author = author.slim(), quoted = quoted?.slim(), reposted = reposted?.slim())

    @Test
    fun postsReadBackFromDiskAreThePostsWritten() {
        val threads = XParser.timeline(J.parse(javaClass.getResource("/x/user_originals_page.json")!!.readText())).threads
        assertTrue(threads.isNotEmpty())
        val expected = threads.map { thread -> thread.copy(posts = thread.posts.map { it.slim() }) }
        assertEquals(expected, XCodec.decode(XCodec.encode(threads)))
    }

    @Test
    fun aCacheOfAnotherShapeIsDropped() {
        assertTrue(XCodec.decode("""{"v": 99, "threads": []}""").isEmpty())
        assertTrue(XCodec.decode("garbage").isEmpty())
    }
}
