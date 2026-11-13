package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginDownload
import kotlin.test.Test
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadsMediaTest {
    private val cdn = "https://scontent.cdninstagram.com/v/t51.2885-15/471234_1838_n"

    private fun post(count: Int, url: String? = "https://www.threads.com/@zuck/post/DAbC-12_x") = ThreadsPost(
        id = "3412",
        handle = "zuck",
        authorName = "Mark",
        text = "",
        url = url,
        media = (1..count).map { ThreadsMedia("$cdn$it.jpg") },
    )

    @Test
    fun aSavedFileIsNamedAfterItsAuthorItsPostAndItsPlaceNotMetasNumbers() {
        val photo = MediaItem("$cdn.jpg?stp=dst-jpg&_nc_ht=x")
        assertEquals("threads-zuck-DAbC-12_x.jpg", ThreadsMediaFiles.name(post(1), 0, photo))
        assertEquals("threads-zuck-DAbC-12_x-3.jpg", ThreadsMediaFiles.name(post(4), 2, photo))
        val clip = MediaItem("$cdn.jpg", kind = MediaItem.Kind.VIDEO, videoUrl = "https://scontent.cdninstagram.com/o1/v/t16/f2/m69/AQN.mp4?efg=1")
        assertEquals("threads-zuck-DAbC-12_x-2.mp4", ThreadsMediaFiles.name(post(2), 1, clip))
        assertEquals("threads-zuck-3412.webp", ThreadsMediaFiles.name(post(1, url = null), 0, MediaItem("https://media.giphy.com/a/giphy.webp")), "no code: the post's id")
    }

    @Test
    fun aFileWhoseAddressNamesNoKindIsSavedAsWhatItIs() {
        assertEquals(".mp4", ThreadsMediaFiles.extension("https://video.example/stream/AQN", video = true))
        assertEquals(".jpg", ThreadsMediaFiles.extension("https://scontent.example/v/12_n", video = false))
        assertEquals(".heic", ThreadsMediaFiles.extension("https://scontent.example/v/12_n.HEIC?x=a.b", video = false))
    }

    @Test
    fun downloadAllSavesEveryFileOfThePostInItsOrderAndLeavesStreamsOut() {
        val items = listOf(
            MediaItem("$cdn.jpg"),
            MediaItem("$cdn.jpg", kind = MediaItem.Kind.VIDEO, videoUrl = "https://v.example/a.m3u8"),
            MediaItem("$cdn.jpg", kind = MediaItem.Kind.VIDEO, videoUrl = "https://v.example/b.mp4"),
        )
        assertEquals(
            listOf(PluginDownload("$cdn.jpg", "threads-zuck-DAbC-12_x-1.jpg"), PluginDownload("https://v.example/b.mp4", "threads-zuck-DAbC-12_x-3.mp4")),
            ThreadsMediaFiles.all(post(3), items),
        )
    }

    @Test
    fun aLongPressOffersOnlyWhatAppliesToThatPicture() {
        val described = MediaItem("$cdn.jpg", alt = " A dog ")
        val plain = MediaItem("$cdn.jpg")
        assertEquals(
            listOf(ThreadsMediaAction.DESCRIPTION, ThreadsMediaAction.DOWNLOAD, ThreadsMediaAction.DOWNLOAD_ALL, ThreadsMediaAction.OPEN_POST, ThreadsMediaAction.COPY_LINK, ThreadsMediaAction.SHARE_LINK),
            ThreadsMediaActions.of(post(2), 0, listOf(described, plain), opens = true),
        )
        assertEquals(
            listOf(ThreadsMediaAction.DOWNLOAD, ThreadsMediaAction.COPY_LINK, ThreadsMediaAction.SHARE_LINK),
            ThreadsMediaActions.of(post(1), 0, listOf(plain), opens = false),
            "one picture, on the post's own page",
        )
        val stream = MediaItem("$cdn.jpg", kind = MediaItem.Kind.VIDEO, videoUrl = "https://v.example/a.m3u8")
        assertEquals(emptyList(), ThreadsMediaActions.of(post(1, url = null), 0, listOf(stream), opens = true), "a stream with no link has nothing to offer")
        assertEquals(emptyList(), ThreadsMediaActions.of(post(1), 3, listOf(plain), opens = true))
    }

    @Test
    fun coveredMediaWaitsBehindATapOnlyWhereTheReaderHidesSensitiveMedia() {
        val covered = post(1).copy(mediaCovered = true)
        assertTrue(ThreadsMediaCover.covers(covered, Settings()), "the app hides sensitive media by default")
        assertFalse(ThreadsMediaCover.covers(post(1), Settings()))
        assertFalse(ThreadsMediaCover.covers(covered, Settings().with(PostsPrefs.hideSensitive, false)))
        assertFalse(ThreadsMediaCover.covers(covered, Settings().with(PostsPrefs.alwaysShowSensitiveMedia, true)))
    }

    @Test
    fun theSheetsGlyphsAreDrawable() {
        ThreadsMediaIcons.all.forEach { assertTrue(it.root.iterator().hasNext(), it.name) }
    }
}
