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
}
