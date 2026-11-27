package com.aimdi.xtanative.data

import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * What becomes of an [AppGraph.keep] write that fails (a save or like on a
 * full disk): the reader is told it went wrong instead of thinking it kept,
 * and the log gets a line. A write stopped as the app closes is no failure.
 */
internal fun keptWriteFailures(log: (String) -> Unit = System.err::println): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, failure ->
        if (failure is CancellationException) return@CoroutineExceptionHandler
        log("XTA Native: a write did not finish: $failure")
        KeepNotices.show(KeepNotice.SomethingWentWrong)
    }
