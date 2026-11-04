package com.aimdi.xtanative.ui.plugins

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** A settings screen's connection test, as XTA's PluginConnectionStore keeps it. */
class PluginConnectionTest {
    private val ok = ConnectionState(ConnectionStatus.OK, "Connected")

    @Test
    fun theFieldsAreKeptBeforeTheyAreTestedAndTheAnswerIsShown(): Unit = runBlocking {
        val connection = PluginConnection()
        val steps = mutableListOf<String>()
        connection.test(save = { steps += "kept" }) {
            steps += "tested"
            assertTrue(connection.state.value.running, "a spinner while it runs")
            ok
        }
        assertEquals(listOf("kept", "tested"), steps)
        assertEquals(ok, connection.state.value)
    }

    @Test
    fun anAnswerAboutFieldsEditedSinceIsNeverShown(): Unit = runBlocking {
        val connection = PluginConnection()
        val answer = CompletableDeferred<ConnectionState>()
        val test = launch(start = CoroutineStart.UNDISPATCHED) { connection.test(save = {}) { answer.await() } }
        connection.invalidate()
        answer.complete(ok)
        test.join()
        assertEquals(ConnectionState(), connection.state.value)
    }

    @Test
    fun aNewerTestHasTheLastWord(): Unit = runBlocking {
        val connection = PluginConnection()
        val first = CompletableDeferred<ConnectionState>()
        val older = launch(start = CoroutineStart.UNDISPATCHED) { connection.test(save = {}) { first.await() } }
        connection.test(save = {}) { ConnectionState(ConnectionStatus.FAILED, "Refused") }
        first.complete(ok)
        older.join()
        assertEquals(ConnectionState(ConnectionStatus.FAILED, "Refused"), connection.state.value)
    }

    @Test
    fun anEditTakesTheAnswerAway(): Unit = runBlocking {
        val connection = PluginConnection()
        connection.test(save = {}) { ok }
        connection.invalidate()
        assertEquals(ConnectionState(), connection.state.value)
    }

    @Test
    fun fieldsThatCouldNotBeKeptOrTestedLeaveNothingSaidRatherThanASpinner(): Unit = runBlocking {
        val connection = PluginConnection()
        connection.test(save = { throw IOException("disk full") }) { ok }
        assertEquals(ConnectionState(), connection.state.value)
        connection.test(save = {}) { error("a bug in the test") }
        assertEquals(ConnectionState(), connection.state.value)
    }

    @Test
    fun aRequestThatFailedIsAnsweredInThePluginsWords(): Unit = runBlocking {
        assertEquals("refused: IOException", answeredOr({ "refused: " + it::class.simpleName }) { throw IOException("no route") })
        assertEquals("fine", answeredOr({ "refused" }) { "fine" })
    }
}
