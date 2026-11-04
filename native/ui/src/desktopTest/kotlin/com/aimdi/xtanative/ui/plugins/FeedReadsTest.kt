package com.aimdi.xtanative.ui.plugins

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class FeedReadsTest {
    @Test
    fun aFeedThatFailsAddsNothing(): Unit = runBlocking {
        val read = FeedReads.each(listOf("a", "broken", "b")) { key -> if (key == "broken") throw IOException("down") else listOf("$key-1", "$key-2") }
        assertEquals(listOf("a-1", "a-2", "b-1", "b-2"), read, "in the keys' order")
    }

    @Test
    fun onlyWhenNoneAnswersDoesItFail() {
        val failure = assertFailsWith<IOException> {
            runBlocking { FeedReads.each<String, String>(listOf("a", "b")) { key -> throw IOException("no $key") } }
        }
        assertEquals("no a", failure.message)
    }

    @Test
    fun nothingToReadIsNothingAndAnEmptyAnswerIsAnAnswer(): Unit = runBlocking {
        assertEquals(emptyList<String>(), FeedReads.each(emptyList<String>()) { listOf("never") })
        assertEquals(emptyList<String>(), FeedReads.each(listOf("quiet")) { emptyList<String>() })
    }

    @Test
    fun theyAreReadAFewAtATimeWhenAsked(): Unit = runBlocking {
        var reading = 0
        var most = 0
        val read = FeedReads.each((1..9).map { "$it" }, atOnce = 3) { account ->
            reading++
            most = maxOf(most, reading)
            delay(20)
            reading--
            listOf(account)
        }
        assertEquals((1..9).map { "$it" }, read)
        assertTrue(most in 2..3, "at most three at once, was $most")
    }
}
