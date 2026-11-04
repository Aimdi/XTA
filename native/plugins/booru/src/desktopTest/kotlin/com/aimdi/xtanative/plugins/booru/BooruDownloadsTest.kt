package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.post
import com.aimdi.xtanative.ui.plugins.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals

class BooruDownloadsTest {
    private val picture = post(
        id = "123",
        host = "https://danbooru.donmai.us",
        tags = listOf("hatsune_miku", "solo", "kei_(keigarou)"),
        categories = mapOf("hatsune_miku" to BooruTagCategory.CHARACTER, "kei_(keigarou)" to BooruTagCategory.ARTIST),
        fileUrl = "https://cdn.donmai.us/original/ab/cd/abcd1234.PNG",
        fileExt = "png",
        md5 = "abcd1234",
    )

    @Test
    fun aTemplateNamesTheFileKeepingItsExtension() {
        assertEquals("danbooru.donmai.us_123_kei_(keigarou)_hatsune_miku.png", BooruDownloads.fileName(picture, "{site}_{id}_{artist}_{character}"))
        assertEquals("abcd1234-g.png", BooruDownloads.fileName(picture, "{md5}-{rating}"))
    }

    @Test
    fun withoutATemplateTheFileKeepsItsOwnName() {
        assertEquals("booru-abcd1234.PNG", BooruDownloads.fileName(picture, ""))
        assertEquals("booru-abcd1234.PNG", BooruDownloads.fileName(picture, "{copyright}"))
    }

    @Test
    fun aTemplateWithUnknownWordsAndUnsafeCharactersStillNamesAFile() {
        assertEquals("{nope}_a_b.png", BooruDownloads.fileName(picture, "{nope}/a:b"))
    }

    @Test
    fun aBatchNamesEachPostsFileAsItsDownloadDoes() {
        val posts = listOf(post(id = "1", fileUrl = "https://cdn.example/1.png"), post(id = "2", fileUrl = "https://cdn.example/clip.webm", fileExt = "webm"))
        val files = BooruDownloads.files(posts, "{id}")
        assertEquals(listOf("https://cdn.example/1.png", "https://cdn.example/clip.webm"), files.map { it.url })
        assertEquals(listOf("1.png", "2.webm"), files.map { it.fileName })
    }

    @Test
    fun theViewerSavesAPicturesOriginalAndAVideosFile() {
        assertEquals(MediaItem("https://cdn.donmai.us/original/ab/cd/abcd1234.PNG"), BooruDownloads.media(picture))
        val clip = post(id = "7", previewUrl = "https://cdn.example/7.jpg", fileUrl = "https://cdn.example/7.mp4", fileExt = "mp4")
        assertEquals(MediaItem("https://cdn.example/7.jpg", MediaItem.Kind.VIDEO, videoUrl = "https://cdn.example/7.mp4"), BooruDownloads.media(clip))
    }

    @Test
    fun aSavedPostIsKeptUnderItsPageOnTheHost() {
        val archive = BooruArchive.of(picture)
        assertEquals("booru:https://danbooru.donmai.us/posts/123", archive.id)
        assertEquals("https://danbooru.donmai.us", archive.authorId)
        val content = J(archive.content)
        assertEquals("https://danbooru.donmai.us/posts/123", content["url"].string)
        assertEquals("link", content["xtaPlugin"].string)
        assertEquals("booru", content["source"].string)
        assertEquals("hatsune_miku solo kei_(keigarou)", content["text"].string)
        assertEquals(listOf("https://cdn.donmai.us/original/ab/cd/abcd1234.PNG"), content["images"].list.map { it.string })
    }
}
