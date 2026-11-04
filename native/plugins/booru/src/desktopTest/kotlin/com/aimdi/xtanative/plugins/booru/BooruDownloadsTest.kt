package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.post
import com.aimdi.xtanative.store.downloads.DownloadDestination
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
    fun aBatchQueuesOnlyFilesNotDownloadedYet() {
        val posts = listOf(
            post(id = "1", fileUrl = "https://cdn.example/1.png"),
            post(id = "2", fileUrl = "https://cdn.example/2.png"),
            post(id = "3", fileUrl = "https://cdn.example/1.png"),
            post(id = "4", fileUrl = "https://cdn.example/4.png"),
            post(id = "5"),
        )
        val folder = DownloadDestination("content://tree", background = true)
        val plan = BooruDownloads.plan(posts, known = setOf("https://cdn.example/2.png"), room = 10, template = "{id}", destination = folder)
        assertEquals(listOf("1.png", "4.png"), plan.requests.map { it.fileName })
        assertEquals(listOf("https://cdn.example/1.png", "https://cdn.example/4.png"), plan.requests.map { it.url })
        assertEquals(3, plan.skipped)
        assertEquals("content://tree", plan.requests.first().treeUri)

        assertEquals(1, BooruDownloads.plan(posts, emptySet(), room = 1, template = "", destination = folder).requests.size)
    }

    @Test
    fun aSavedPostIsKeptUnderItsPageOnTheHost() {
        assertEquals("booru:https://danbooru.donmai.us/posts/123", BooruArchive.id(picture))
        val content = J(BooruArchive.content(picture))
        assertEquals("https://danbooru.donmai.us/posts/123", content["url"].string)
        assertEquals("link", content["xtaPlugin"].string)
        assertEquals("booru", content["source"].string)
        assertEquals("hatsune_miku solo kei_(keigarou)", content["text"].string)
    }
}
