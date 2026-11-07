package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** XTA's substack_opml_test: an import whose settings screen closed stops asking. */
class SubstackOpmlStopTest {
    @Test
    fun anImportWhoseScreenClosedStopsAsking(): Unit = runBlocking {
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resolved = ArrayList<String>()
        val first = CompletableDeferred<Unit>()
        val job = screen.launch {
            SubstackOpml.import(
                listOf("https://new.substack.com", "https://other.substack.com"),
                resolve = { site ->
                    resolved += site
                    first.complete(Unit)
                    CompletableDeferred<SubstackPublication>().await()
                },
                followOf = { null },
                follow = {},
            )
        }
        first.await()
        screen.cancel()
        job.join()
        assertEquals(listOf("https://new.substack.com"), resolved, "nothing more is asked once the screen is gone")
        assertTrue(job.isCancelled)
    }
}
