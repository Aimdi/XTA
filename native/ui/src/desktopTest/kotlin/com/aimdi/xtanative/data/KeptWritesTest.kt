package com.aimdi.xtanative.data

import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** A kept write (a save, a like) that fails is told, not dropped. */
class KeptWritesTest {
    @Test
    fun aFailedWriteSaysSomethingWentWrongAndLogsALine() = runBlocking {
        val logged = mutableListOf<String>()
        val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO + keptWriteFailures(logged::add))
        writes.launch { throw IOException("No space left on device") }.join()
        assertSame(KeepNotice.SomethingWentWrong, KeepNotices.shown.value?.notice)
        assertEquals(1, logged.size)
        assertEquals(true, "No space left on device" in logged.single())
        writes.launch { }.join()
        assertEquals(true, writes.coroutineContext[kotlinx.coroutines.Job]!!.isActive, "later writes still run")
    }

    @Test
    fun aStoppedWriteIsNoFailure() = runBlocking {
        val logged = mutableListOf<String>()
        KeepNotices.show(KeepNotice.MediaSaved)
        val before = KeepNotices.shown.value
        val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO + keptWriteFailures(logged::add))
        writes.launch { throw CancellationException("app closing") }.join()
        assertEquals(emptyList(), logged)
        assertSame(before, KeepNotices.shown.value)
    }
}
