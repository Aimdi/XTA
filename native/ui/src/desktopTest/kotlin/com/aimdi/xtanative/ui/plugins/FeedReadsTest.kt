package com.aimdi.xtanative.ui.plugins

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class FeedReadsTest {
    @Test
    fun aFeedThatFailsAddsNothing() = runBlocking {
        val read = FeedReads.each(listOf("a", "broken", "b")) { key -> if (key == "broken") throw IOException("down") else listOf("$key-1", "$key-2") }
        assertEquals(listOf("a-1", "a-2", "b-1", "b-2"), read)
    }

    @Test
    fun onlyWhenNoneAnswersDoesItFail() {
        val failure = assertFailsWith<IOException> {
            runBlocking { FeedReads.each<String, String>(listOf("a", "b")) { key -> throw IOException("no $key") } }
        }
        assertEquals("no a", failure.message)
    }

    @Test
    fun nothingToReadIsNothing() = runBlocking {
        assertEquals(emptyList<String>(), FeedReads.each(emptyList<String>()) { listOf("never") })
    }
}
