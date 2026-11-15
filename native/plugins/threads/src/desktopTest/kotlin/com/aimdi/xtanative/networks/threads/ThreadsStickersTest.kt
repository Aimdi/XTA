package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stickers among a caption's or bio's words (round 24): the shape CI's probe
 * saw on bios (`inline_sticker_fragment` with `sticker_id` and `sticker_url`)
 * and the other names Meta may give them, all made up here.
 */
class ThreadsStickersTest {
    private fun words(text: String) = mapOf("fragment_type" to "plaintext", "plaintext" to text, "inline_sticker_fragment" to null)

    private fun seen(url: String?, plaintext: String = "") = mapOf(
        "fragment_type" to "inline_sticker",
        "plaintext" to plaintext,
        "inline_sticker_fragment" to mapOf("community_inline_sticker_id" to null, "sticker_id" to "123", "sticker_url" to url),
    )

    private fun post(caption: String, fragments: List<Map<String, Any?>>, info: Map<String, Any?> = emptyMap()) =
        ThreadsParser.post(j(postJson("7", text = caption, info = mapOf("text_fragments" to mapOf("fragments" to fragments)) + info)))

    @Test
    fun theShapeSeenOnBiosKeepsItsPlaceAmongTheWords() {
        val read = post("Hello world", listOf(words("Hello "), seen("https://cdn/mario.png"), words("world")))!!
        assertEquals(listOf(Kind.TEXT, Kind.STICKER, Kind.TEXT), read.fragments.map { it.kind })
        assertEquals("https://cdn/mario.png", read.fragments[1].sticker?.url)
        assertEquals("Hello world", read.text)
        assertTrue(read.hasStickers)
    }

    @Test
    fun aPostOfStickersAloneIsKeptWithNoWords() {
        val read = post("", listOf(seen("https://cdn/a.png"), seen("https://cdn/b.png")))
        assertNotNull(read, "a post of stickers alone is not an empty post")
        assertEquals("", read.text)
        assertEquals(listOf("https://cdn/a.png", "https://cdn/b.png"), read.fragments.mapNotNull { it.sticker?.url })
    }

    @Test
    fun repeatedStickersAreEachDrawnInOrder() {
        val runs = post("", List(5) { seen(if (it % 2 == 0) "https://cdn/a.png" else "https://cdn/b.png") })!!.fragments
        assertEquals(listOf("a", "b", "a", "b", "a"), runs.map { it.sticker!!.url.substringAfterLast('/').removeSuffix(".png") })
    }

    @Test
    fun aStickerWithoutAPictureIsLeftOutAndTheWordsStay() {
        val read = post("Hi there", listOf(words("Hi "), seen(null), words("there"), seen("https://cdn/ok.png")))!!
        assertEquals(listOf(Kind.TEXT, Kind.TEXT, Kind.STICKER), read.fragments.map { it.kind })
        assertEquals("Hi there", read.text)
    }

    @Test
    fun theCaptionsStandInCharactersAndTheStickersOwnWordsAreNotTheCaption() {
        val read = post("Go ￼ team", listOf(words("Go "), seen("https://cdn/x.png", plaintext = "￼"), words(" team")))!!
        assertEquals("Go  team", read.text, "the words around the sticker, without the character holding its place")
        assertEquals(Kind.STICKER, read.fragments[1].kind)
        assertEquals("", read.fragments[1].text, "a sticker's run holds no words of its own")
    }

    @Test
    fun otherPlausibleNamesAreReadToo() {
        val sized = mapOf(
            "fragment_type" to "sticker",
            "plaintext" to "",
            "sticker_fragment" to mapOf("url" to "https://cdn/s.webp", "width" to 200, "height" to 100, "accessibility_label" to "Mario jumping"),
        )
        val nested = mapOf("fragment_type" to "inline_sticker", "inline_sticker_fragment" to mapOf("sticker" to mapOf("image_versions2" to mapOf("candidates" to listOf(mapOf("url" to "https://cdn/n.png", "width" to 64, "height" to 64))))))
        val runs = post("a", listOf(words("a"), sized, nested))!!.fragments
        val (first, second) = runs.mapNotNull { it.sticker }
        assertEquals(ThreadsSticker("https://cdn/s.webp", 200, 100, "Mario jumping"), first)
        assertEquals(2f, first.aspect)
        assertEquals("https://cdn/n.png", second.url)
        assertEquals(1f, second.aspect)
    }

    @Test
    fun aStickersOwnWordsNameItWhereItHasNoLabel() {
        val read = post("Z", listOf(seen("https://cdn/z.png", plaintext = "Z")))!!
        assertEquals("Z", read.fragments.single().sticker?.label)
        assertEquals("", read.text, "its letter is a picture, not a word to copy twice")
    }

    @Test
    fun stickersListedBesideTheWordsFollowThem() {
        val read = ThreadsParser.post(j(postJson("8", text = "Look", info = mapOf("inline_stickers" to listOf(mapOf("sticker_url" to "https://cdn/l.png"))))))!!
        assertEquals(listOf(Kind.TEXT, Kind.STICKER), read.fragments.map { it.kind })
        assertEquals("Look", read.text)
    }

    @Test
    fun whenTheWordsNoLongerSpellTheCaptionTheCaptionIsReadAndItsStickersFollow() {
        val read = post("Totally different @meta", listOf(words("Hello "), seen("https://cdn/a.png")))!!
        assertEquals(listOf(Kind.TEXT, Kind.MENTION, Kind.STICKER), read.fragments.map { it.kind })
        assertEquals("Totally different @meta", read.text)
    }

    @Test
    fun aSpoilerStickerStaysASpoiler() {
        val hidden = seen("https://cdn/a.png") + mapOf("styling_info" to mapOf("is_spoiler" to true))
        val read = post("", listOf(hidden))!!
        assertTrue(read.fragments.single().spoiler)
        assertTrue(read.hasSpoiler)
    }

    @Test
    fun quotedPostsCarryTheirStickers() {
        val quoted = postJson("9", text = "", info = mapOf("text_fragments" to mapOf("fragments" to listOf(seen("https://cdn/q.png")))))
        val outer = ThreadsParser.post(j(postJson("10", text = "see", info = mapOf("share_info" to mapOf("quoted_post" to quoted)))))!!
        assertEquals("https://cdn/q.png", outer.quoted?.fragments?.single()?.sticker?.url)
    }

    @Test
    fun aMissingOrOddShapeIsNoSticker() {
        assertNull(ThreadsStickers.sticker(j(null)))
        assertNull(ThreadsStickers.sticker(j(mapOf("sticker_url" to "not a url"))))
        assertEquals(emptyList(), post("plain", listOf(words("plain")))!!.fragments)
        val oddBody = post("x", listOf(mapOf("fragment_type" to "inline_sticker", "inline_sticker_fragment" to listOf(1, 2))))
        assertEquals("x", oddBody?.text)
    }

    @Test
    fun aBiosStickersAreReadAsAPostsAre() {
        val user = j(mapOf("text_app_biography" to mapOf("text_fragments" to mapOf("fragments" to listOf(words("Hi "), seen("https://cdn/bio.png"), words(" @dana"))))))
        val runs = ThreadsProfileOwner.bioFragments(user, "Hi  @dana")
        assertEquals(listOf(Kind.TEXT, Kind.STICKER, Kind.TEXT), runs.map { it.kind })
        assertEquals("https://cdn/bio.png", runs[1].sticker?.url)
    }
}
