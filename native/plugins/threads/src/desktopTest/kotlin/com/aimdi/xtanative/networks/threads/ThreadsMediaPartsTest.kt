package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadsMediaPartsTest {
    private fun post(extra: String) = J.parse(
        """{"pk":"1","code":"AbC","taken_at":1700000000,"user":{"username":"zuck"},"caption":{"text":"hi"},$extra"text_post_app_info":{}}""",
    )

    private val picture = """{"image_versions2":{"candidates":[{"url":"https://cdn.example/a.jpg","width":1080,"height":1350}]}}"""

    @Test
    fun mediaMetaLinksIntoAPostIsShownWhenThePostHasNoneOfItsOwn() {
        val linked = J.parse("""{"pk":"1","user":{"username":"zuck"},"text_post_app_info":{"linked_inline_media":$picture}}""")
        assertEquals(listOf("https://cdn.example/a.jpg"), ThreadsMediaParts.of(linked).map { it.url })
        val both = J.parse("""{"image_versions2":{"candidates":[{"url":"https://cdn.example/own.jpg"}]},"text_post_app_info":{"linked_inline_media":$picture}}""")
        assertEquals(listOf("https://cdn.example/own.jpg"), ThreadsMediaParts.of(both).map { it.url }, "its own come first and alone")
        val empty = J.parse("""{"text_post_app_info":{"linked_inline_media":{"has_audio":true}}}""")
        assertTrue(ThreadsMediaParts.of(empty).isEmpty(), "a link without a picture adds nothing")
        assertEquals("https://cdn.example/a.jpg", ThreadsParser.post(J.parse("""{"pk":"1","code":"x","user":{"username":"zuck"},"text_post_app_info":{"linked_inline_media":$picture}}"""))?.media?.single()?.url)
    }

    @Test
    fun aPostIsCoveredOnlyWhereMetaLaysSomethingOverItsMedia() {
        assertTrue(ThreadsParser.post(post(""""media_overlay_info":{"anything":1},"""))!!.mediaCovered)
        assertFalse(ThreadsParser.post(post(""""media_overlay_info":{},"""))!!.mediaCovered)
        assertFalse(ThreadsParser.post(post(""""media_overlay_info":null,"""))!!.mediaCovered)
        assertFalse(ThreadsParser.post(post(""))!!.mediaCovered)
    }

    @Test
    fun theCoverSaysMetasOwnWordsWhenItGivesSomeInTheShapeSeenLive() {
        val buttons = """"media_overlay_info":{"overlay_type":"x","sub_category":"y","buttons":[{"action":"a","button_type":"b"},
            {"action":"reveal","headline_text":"Sensitive content","secondary_text":"This photo may show violence.","text":"See photo"}]},"""
        assertEquals("Sensitive content\nThis photo may show violence.", ThreadsParser.post(post(buttons))!!.mediaCoverWords)
        val described = """"media_overlay_info":{"title":"Graphic","description":"Viewer discretion","buttons":[{"headline_text":"Other"}]},"""
        assertEquals("Graphic\nViewer discretion", ThreadsMediaParts.coverWords(post(described)))
        assertEquals(null, ThreadsMediaParts.coverWords(post(""""media_overlay_info":{"description":{"text":"x"},"buttons":[{"headline_text":3}]},""")))
        assertEquals(null, ThreadsMediaParts.coverWords(post("")))
        assertEquals(200, ThreadsMediaParts.coverWords(post(""""media_overlay_info":{"description":"${"w".repeat(500)}"},"""))!!.length)
    }
}

