package com.aimdi.xtanative.ui.plugins

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class FetchTest {
    @Test
    fun aReadThatFailsKeepsWhatWasShownTheFailureBesideIt(): Unit = runBlocking {
        var fail = false
        var reads = 0
        val fetch = Fetch(this, initial = "seed") { force ->
            reads++
            if (fail) throw IOException("offline") else if (force) "forced" else "read"
        }
        assertEquals("seed", fetch.state.value.value)

        fetch.ensure()
        fetch.ensure()
        assertEquals("read", fetch.state.first { !it.loading && it.value != "seed" }.value)
        assertEquals(1, reads, "ensure reads once")

        fail = true
        fetch.load(force = true)
        val failed = fetch.state.first { it.problem != null }
        assertEquals("read", failed.value)
        assertIs<IOException>(failed.problem)
        fail = false
        fetch.load(force = true)
        assertEquals("forced", fetch.state.first { it.problem == null && !it.loading && it.value == "forced" }.value)
    }

    @Test
    fun aSoftRefreshKeepsTheItemsShownAndPaintsEachAnswerAsItComes(): Unit = runBlocking {
        val answer = CompletableDeferred<List<String>>()
        var fail = false
        val feed = MergedFeed(this) { _, partial ->
            if (fail) throw IOException("offline")
            partial(listOf("first"))
            answer.await()
        }
        feed.restore(listOf("kept"))
        assertEquals(listOf("kept"), feed.state.value.items)

        feed.refresh()
        feed.state.first { it.items == listOf("first") }
        answer.complete(listOf("first", "second"))
        assertEquals(listOf("first", "second"), feed.state.first { it.loaded && !it.loading }.items)

        fail = true
        feed.refresh(force = true)
        assertEquals(listOf("first", "second"), feed.state.first { it.problem != null }.items, "a failed refresh does not blank a healthy feed")
        feed.restore(listOf("other"))
        yield()
        assertEquals(listOf("first", "second"), feed.state.value.items, "what was kept never paints over what is shown")
        feed.forget()
        assertEquals(MergedFeedState(), feed.state.value)
    }
}
