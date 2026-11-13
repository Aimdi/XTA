package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.x.model.XUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's group_add_member_store_test.dart: the add sheet's search and follows. */
class AddMemberModelTest {
    private fun user(id: String) = XUser(id, id, id)

    @Test
    fun aFailedAdditionReleasesTheBusyStateAndCanBeTriedAgain() = runBlocking {
        val model = AddMemberModel(this)
        assertFalse(model.add("x:1") { throw IllegalStateException("write failed") })
        assertFalse(model.state.value.adding)
        assertTrue(model.state.value.added.isEmpty())
        assertTrue(model.add("x:1") { "1" })
        assertEquals(setOf("1"), model.state.value.added)
        assertFalse(model.state.value.adding)
    }

    @Test
    fun aDoubleTapDoesNotRepeatAFollowWhilePendingOrAfterItWorked() = runBlocking {
        val model = AddMemberModel(this)
        val result = CompletableDeferred<String>()
        var calls = 0
        val follow: suspend () -> String = { calls++; result.await() }
        val adding = async { model.add("bluesky:alice", follow) }
        model.state.first { it.adding }
        assertFalse(model.add("bluesky:alice", follow))
        result.complete("alice.bsky.social")
        assertTrue(adding.await())
        assertFalse(model.add("bluesky:alice", follow))
        assertEquals(1, calls)
        assertEquals(setOf("alice.bsky.social"), model.state.value.added)
    }

    @Test
    fun aFollowStartedRunsToItsEndWhenTheSheetGoesAway() = runBlocking {
        val sheet = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val model = AddMemberModel(sheet)
        val result = CompletableDeferred<String>()
        var written = false
        val adding = sheet.async { model.add("x:1") { result.await().also { written = true } } }
        model.state.first { it.adding }
        sheet.cancel()
        result.complete("1")
        runCatching { adding.await() }
        withTimeout(1_000) { while (!written) delay(5) }
        assertTrue(written)
    }

    @Test
    fun anEarlierSearchCannotReplaceTheLatestResultForTheSameQuery() = runBlocking {
        val model = AddMemberModel(this)
        model.type("alice")
        val old = CompletableDeferred<List<XUser>>()
        model.find { old.await() }
        model.find { listOf(user("new")) }
        old.complete(listOf(user("old")))
        val done = model.state.first { !it.searching }
        assertEquals(listOf("new"), done.users?.map { it.id })
    }

    @Test
    fun aHangingSearchTimesOutAndANewSearchCanBeAsked() = runBlocking {
        val model = AddMemberModel(this, timeoutMillis = 50)
        model.type("alice")
        model.find { awaitCancellation() }
        val timedOut = model.state.first { !it.searching }
        assertTrue(timedOut.failed)
        model.type("bob")
        model.find { emptyList() }
        val next = model.state.first { !it.searching && it.users != null }
        assertFalse(next.failed)
        assertEquals(emptyList(), next.users)
    }
}
