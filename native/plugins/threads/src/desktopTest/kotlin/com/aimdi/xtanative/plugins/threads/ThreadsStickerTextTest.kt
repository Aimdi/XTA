package com.aimdi.xtanative.plugins.threads

import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSticker
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import com.aimdi.xtanative.ui.plugins.TextRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How a caption's stickers are drawn, heard, copied, folded and kept (round 24). */
class ThreadsStickerTextTest {
    private val mario = ThreadsSticker("https://cdn/mario.png")
    private val star = ThreadsSticker("https://cdn/star.png", 64, 32, "Star")
    private fun sticker(s: ThreadsSticker) = ThreadsTextFragment.sticker(s)
    private fun words(text: String) = ThreadsTextFragment(Kind.TEXT, text)
    private val runs = listOf(words("Hi "), sticker(mario), sticker(mario), words(" "), sticker(mario), sticker(star), words(" and "), ThreadsTextFragment(Kind.MENTION, "@meta", "meta"))

    @Test
    fun aScreenReaderHearsEachNameOnceInARow() {
        assertEquals("Hi Sticker Star and @meta", ThreadsStickerRules.spoken(runs, "Sticker"))
        assertEquals("Sticker", ThreadsStickerRules.spoken(List(300) { sticker(mario) }, "Sticker"), "three hundred unnamed stickers are one word")
    }

    @Test
    fun copyingGivesEachStickersNameInBrackets() {
        assertEquals("[Sticker]", ThreadsStickerRules.alternate(mario, "Sticker"))
        assertEquals("[Star]", ThreadsStickerRules.alternate(star, "Sticker"))
        val text = ThreadsStickerRules.annotated(runs, Color.Blue, "Sticker", {}, {})
        assertEquals("Hi [Sticker][Sticker] [Sticker][Star] and @meta", text.text)
        assertEquals(4, text.getStringAnnotations(0, text.length).count { it.tag == INLINE_TAG }, "each sticker is a placeholder")
        assertEquals(1, text.getLinkAnnotations(0, text.length).size, "the mention still opens its profile")
    }

    @Test
    fun eachPictureIsOnePlaceholderHoweverOftenItRepeats() {
        assertEquals(listOf(mario, star), ThreadsStickerRules.distinct(runs))
        assertEquals(ThreadsStickerRules.id(mario), ThreadsStickerRules.id(mario.copy(label = "other")))
    }

    @Test
    fun stickersAloneAreDrawnLargerThanAmongWords() {
        assertEquals(ThreadsStickerRules.INLINE_EM, ThreadsStickerRules.em(runs))
        assertEquals(ThreadsStickerRules.ALONE_EM, ThreadsStickerRules.em(listOf(sticker(mario), words(" "), sticker(star))))
    }

    @Test
    fun aStickerIsLoadedAtThePixelsItIsDrawnAt() {
        assertEquals(45 to 45, ThreadsStickerRules.pixels(mario, 30f, 1.5f))
        assertEquals(90 to 45, ThreadsStickerRules.pixels(star, 30f, 1.5f))
    }

    @Test
    fun aPostOfManyStickersFoldsAsALongCaptionDoes() {
        val many = ThreadsPost("1", "a", "A", "", fragments = List(200) { sticker(mario) })
        assertTrue(ThreadsCardRules.isLong(many))
        assertFalse(ThreadsCardRules.isLong(many.copy(fragments = List(10) { sticker(mario) })))
    }

    @Test
    fun aCoveredCaptionCoversASpoilerStickerAndDrawsTheRest() {
        val covered = ThreadsCardRules.covered(listOf(sticker(mario), ThreadsTextFragment.sticker(star, spoiler = true)), Color.Gray, Color.Blue, "Sticker") {}
        assertEquals("[Sticker][Star]", covered.text)
        assertEquals(1, covered.getStringAnnotations(0, covered.length).count { it.tag == INLINE_TAG })
    }

    @Test
    fun theCardsSummaryNamesItsStickers() {
        val post = ThreadsPost("1", "a", "Ann", "Hi", fragments = listOf(words("Hi "), sticker(star)))
        assertEquals("Ann, @a, now. Hi Star", ThreadsCardRules.summary(post, "now", null, "Spoiler", emptyList(), "Sticker"))
    }

    @Test
    fun theSharedRowAndReadingAloudKeepTheWordsAlone() {
        assertEquals(listOf("Hi ", " ", " and ", "@meta"), threadsRuns("Hi and @meta", runs).map(TextRun::text))
    }

    @Test
    fun aTranslationKeepsTheStickersAfterIt() {
        val shown = ThreadsStickerRules.translated("Hallo", runs)
        assertEquals(listOf(Kind.TEXT, Kind.TEXT, Kind.STICKER, Kind.STICKER, Kind.STICKER, Kind.STICKER), shown.map { it.kind })
        assertEquals(emptyList(), ThreadsStickerRules.translated("Hallo", listOf(words("Hi"))))
    }

    @Test
    fun aKeptPostKeepsItsStickers() {
        val post = ThreadsPost("1", "a", "A", "Hi and @meta", fragments = runs)
        val back = ThreadsPostJson.post(J(ThreadsPostJson.json(post)))!!
        assertEquals(runs, back.fragments)
        assertEquals(star, back.fragments[5].sticker)
        val older = J(kotlinx.serialization.json.Json.parseToJsonElement("""{"kind":"sticker","text":""}"""))
        assertEquals(null, ThreadsPostJson.fragment(older), "a kept sticker without its picture is left out")
    }

    private companion object {
        /** The tag Compose files an inline placeholder under (androidx.compose.foundation.text.INLINE_CONTENT_TAG). */
        const val INLINE_TAG = "androidx.compose.foundation.text.inlineContent"
    }
}
