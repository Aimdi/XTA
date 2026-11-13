package com.aimdi.xtanative.networks.ehviewer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * The work a holder starts on its own (pages read ahead, tag suggestions, a
 * search run again) on [owner]'s dispatcher. It is cancelled with [owner] or
 * by [close], and one failure never cancels the rest. [owner] never waits for
 * it, so a `runBlocking` or `coroutineScope` that made a holder still ends.
 */
internal class EhWork(owner: CoroutineScope) {
    private val jobs = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(owner.coroutineContext + jobs)
    private val ownerEnds: DisposableHandle? = owner.coroutineContext[Job]?.invokeOnCompletion { jobs.cancel() }

    /** Neither [owner] ended nor [close] was called: new work can still start. */
    val active: Boolean get() = jobs.isActive

    fun close() {
        ownerEnds?.dispose()
        jobs.cancel()
    }
}
