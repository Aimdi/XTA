package com.aimdi.xtanative.services.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeechTextTest {
    @Test
    fun shortTextIsOneUtteranceAndNothingIsNothingToSay() {
        assertEquals(listOf("A short article."), chunkForSpeech("A short article."))
        assertTrue(chunkForSpeech("").isEmpty())
        assertTrue(chunkForSpeech("   \n  ").isEmpty())
    }

    @Test
    fun longTextBreaksAtSentenceEndsNeverMidWord() {
        val text = List(60) { "This is a sentence of a reasonable length." }.joinToString(" ")
        val chunks = chunkForSpeech(text, maxChars = 200)
        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= 200)
            assertEquals(chunk.trim(), chunk, "a chunk neither starts nor ends on a space")
            assertTrue(chunk.endsWith("."), "the break lands after a sentence")
        }
    }

    @Test
    fun everyWordSurvivesTheSplit() {
        val text = List(200) { "Word$it is here." }.joinToString(" ")
        assertEquals(text, chunkForSpeech(text, maxChars = 300).joinToString(" "))
    }

    @Test
    fun oneSentenceLongerThanTheLimitIsCutWhereItHasTo() {
        val chunks = chunkForSpeech("a".repeat(500), maxChars = 200)
        assertEquals(3, chunks.size)
        assertEquals("a".repeat(500), chunks.joinToString(""))
    }

    @Test
    fun aSentenceExactlyAtTheLimitMakesNoEmptyChunk() {
        val sentence = "b".repeat(99) + "."
        val chunks = chunkForSpeech("$sentence $sentence", maxChars = 100)
        assertEquals(listOf(sentence, sentence), chunks, "XTA queued an empty utterance before it")
    }

    @Test
    fun ideographicFullStopsEndSentencesToo() {
        val chunks = chunkForSpeech("这是第一句。　这是第二句。", maxChars = 8)
        assertEquals(listOf("这是第一句。", "这是第二句。"), chunks)
    }

    private val article = "Title\n\nAuthor\n\nFirst paragraph is here.\n\nSecond paragraph follows."

    @Test
    fun readingStartsAtTheHeldParagraphAndKeepsTheRest() {
        assertEquals("Second paragraph follows.", textFromHere(article, "Second paragraph follows."))
        assertEquals("First paragraph is here.\n\nSecond paragraph follows.", textFromHere(article, "First paragraph is here."))
        assertEquals("A paragraph only in the page", textFromHere(article, "A paragraph only in the page"))
        assertEquals(article, textFromHere(article, "  "))
        assertEquals(article, textFromHere(article, "Title"))
    }

    @Test
    fun aHeldParagraphIsFoundByItsFirstLine() {
        assertEquals("First paragraph is here.\n\nSecond paragraph follows.", textFromHere(article, "First paragraph is here.\nwith a line the page added"))
    }

    @Test
    fun germanIsToldFromEnglish() {
        assertEquals(
            "de",
            detectTextLanguage("Die Regierung hat am Montag angekündigt, dass sie die Steuer nicht erhöhen wird. Das ist auch für uns eine gute Nachricht."),
        )
        assertEquals(
            "en",
            detectTextLanguage("The government said on Monday that it will not raise the tax. This is good news for all of us, and it was expected."),
        )
    }

    @Test
    fun frenchAndSpanishAreReadToo() {
        assertEquals("fr", detectTextLanguage("Le gouvernement a annoncé que les impôts ne vont pas augmenter et que la situation est stable pour les familles."))
        assertEquals("es", detectTextLanguage("El gobierno dijo que los impuestos no van a subir y que la situación es estable para las familias del país."))
    }

    @Test
    fun tooLittleTextSaysNothing() {
        assertNull(detectTextLanguage("Hello there"))
        assertNull(detectTextLanguage(""))
    }
}
