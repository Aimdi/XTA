package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.ui.plugins.PluginProgress
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

/** A plugin's long piece of work at the foot of the window (PluginHost.withProgress). */
class PluginProgressingTest {
    private fun shown(): KeepNotice.Progress = assertIs<KeepNotice.Progress>(KeepNotices.shown.value?.notice)

    @Test
    fun eachLineTheWorkSaysIsShownAndItsAnswerComesBack(): Unit = runBlocking {
        val answer = PluginProgressing.run(PluginProgress("Fetching frames…")) { say ->
            assertEquals("Fetching frames…", shown().text)
            assertNull(shown().fraction, "a ring that turns while the work cannot say how far it is")
            say(PluginProgress("Encoding frame 2 of 4…", 0.25f, "Encoding can take a minute"))
            assertEquals(0.25f, shown().fraction)
            assertEquals("Encoding can take a minute", shown().note)
            assertNotNull(shown().onCancel)
            say(PluginProgress("Saving…", cancellable = false))
            assertNull(shown().onCancel, "saving runs to its end")
            42
        }
        assertEquals(42, answer)
        assertNull(KeepNotices.shown.value, "the notice goes once the work is done")
    }

    @Test
    fun cancelStopsTheWorkAndSaysSo(): Unit = runBlocking {
        val answer = PluginProgressing.run(PluginProgress("Fetching frames…")) { _ ->
            shown().onCancel!!.invoke()
            awaitCancellation()
        }
        assertNull(answer)
        assertEquals(KeepNotice.Cancelled, KeepNotices.shown.value?.notice)
        assertTrue(isActive, "the caller goes on")
    }

    @Test
    fun whatTheWorkThrowsComesOutAndTheCallerGoesOn(): Unit = runBlocking {
        val failure = runCatching { PluginProgressing.run<Int>(PluginProgress("Fetching frames…")) { throw IOException("gone") } }.exceptionOrNull()
        assertIs<IOException>(failure)
        assertNull(KeepNotices.shown.value, "the notice is taken away")
        delay(1)
        assertTrue(isActive, "the caller's own job was not cancelled by the failure")
    }

    @Test
    fun aFractionPastEitherEndIsKeptWithinTheRing() {
        assertEquals(1f, PluginProgressing.noticeOf(PluginProgress("x", 1.5f)) {}.fraction)
        assertEquals(0f, PluginProgressing.noticeOf(PluginProgress("x", -1f)) {}.fraction)
    }
}
