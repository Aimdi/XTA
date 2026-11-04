package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.ui.plugins.PluginProgress
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * A plugin's long piece of work at the foot of the window
 * (PluginHost.withProgress), as XTA's working snack bar with its ring: each
 * line the work says shown as it comes, and Cancel, while a line offers it,
 * stopping the work and saying so.
 */
internal object PluginProgressing {
    suspend fun <T : Any> run(first: PluginProgress, work: suspend (say: (PluginProgress) -> Unit) -> T): T? {
        // A supervisor of its own, so Cancel stops the work alone and a failure comes out of here, not through the caller's job.
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val stopped = AtomicBoolean(false)
        val cancel = {
            if (stopped.compareAndSet(false, true)) {
                job.cancel()
                KeepNotices.show(KeepNotice.Cancelled)
            }
        }
        val say = { line: PluginProgress -> if (!stopped.get()) KeepNotices.show(noticeOf(line, cancel)) }
        say(first)
        return try {
            withContext(job) { work(say) }
        } catch (e: CancellationException) {
            if (stopped.get() && currentCoroutineContext().isActive) null else throw e
        } finally {
            job.complete()
            if (!stopped.get()) KeepNotices.endWorking()
        }
    }

    /** [line] as the notice at the foot of the window, with [cancel] behind its Cancel while it offers one. */
    fun noticeOf(line: PluginProgress, cancel: () -> Unit): KeepNotice.Progress =
        KeepNotice.Progress(line.text, line.fraction?.coerceIn(0f, 1f), line.note, cancel.takeIf { line.cancellable })
}
