package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlinx.coroutines.delay

/**
 * What the app does when one of its windows breaks while it draws or runs
 * its screens. Compose Desktop catches that itself, so it never reaches the
 * crash reports' uncaught handler: by default it shows an untranslated Swing
 * error dialog and asks the window to close, which with Close to tray on only
 * hid the broken window while the process lived on.
 *
 * Instead the crash is written down as an uncaught one is ([record]; the next
 * start shows the note of a screen that broke) and the whole app quits
 * ([quit]), from the main window and a post window alike: every window shares
 * one graph and its state, which the crash may have left half changed, so a
 * post window closing alone could leave the main one running on it.
 */
@OptIn(ExperimentalComposeUiApi::class)
object WindowCrash {
    /** The handlers every window of the app gets, sharing [handler]. */
    fun handlers(record: (Throwable) -> Unit, quit: () -> Unit): WindowExceptionHandlerFactory {
        val shared = handler(record, quit)
        return WindowExceptionHandlerFactory { shared }
    }

    /**
     * The first crash is recorded and quits, and [halt]s the process should
     * the quit not have ended it in time; later ones (the same broken frame
     * drawn again) are only printed.
     */
    fun handler(record: (Throwable) -> Unit, quit: () -> Unit, halt: () -> Unit = { exitProcess(1) }): WindowExceptionHandler {
        val quitting = AtomicBoolean(false)
        return WindowExceptionHandler { error ->
            error.printStackTrace()
            if (!quitting.compareAndSet(false, true)) return@WindowExceptionHandler
            runCatching { record(error) }
            // Should the closing windows hang on the broken state, the process still ends.
            Thread({
                Thread.sleep(QUIT_WAIT_MS)
                halt()
            }, "crash-quit").apply { isDaemon = true }.start()
            runCatching(quit).onFailure { halt() }
        }
    }

    /**
     * The window XTA_NATIVE_TEST_CRASH asks to break ("main" or "post"), so a
     * check sees what a crash does; honoured only against XTA's stand-in for X
     * (XTA_NATIVE_MOCK_X), never in the reader's own app.
     */
    fun testCrash(env: (String) -> String?): String? {
        if (env("XTA_NATIVE_MOCK_X").isNullOrBlank()) return null
        return env("XTA_NATIVE_TEST_CRASH")?.trim()?.takeIf { it in KINDS }
    }

    /** Breaks the composition it sits in a moment after it is first drawn, when [testCrash] names [window]. */
    @Composable
    fun TestCrash(window: String) {
        val asked = remember { testCrash(System::getenv) == window }
        if (!asked) return
        var broken by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(TEST_CRASH_DELAY_MS)
            broken = true
        }
        check(!broken) { "XTA_NATIVE_TEST_CRASH=$window" }
    }

    private val KINDS = setOf("main", "post")
    private const val QUIT_WAIT_MS = 10_000L
    private const val TEST_CRASH_DELAY_MS = 3_000L
}
