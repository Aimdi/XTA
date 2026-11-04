package com.aimdi.xtanative.plugins.immich

import com.aimdi.xtanative.store.StoreDir
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** What was sent to Immich from this device, as XTA's immich_upload table keeps it. */
class ImmichDeviceLogTest {
    private val store = StoreDir(Files.createTempDirectory("immich-log-test").toFile())

    @Test
    fun whatWasRecordedIsNoLongerPendingAndOutlivesARestart(): Unit = runBlocking {
        val log = ImmichDeviceLog(store.file("uploads.json"))
        log.record("https://pbs.twimg.com/media/a.jpg", "asset-a")
        log.record("https://pbs.twimg.com/media/b.jpg", null)
        val asked = setOf("https://pbs.twimg.com/media/a.jpg", "https://pbs.twimg.com/media/b.jpg", "https://pbs.twimg.com/media/c.jpg")
        assertEquals(setOf("https://pbs.twimg.com/media/c.jpg"), log.pending(asked))
        assertEquals(2, log.count())

        val restarted = ImmichDeviceLog(StoreDir(store.root).file("uploads.json"))
        assertEquals(setOf("https://pbs.twimg.com/media/c.jpg"), restarted.pending(asked))
    }

    @Test
    fun theRowsAreXtasColumnsAndALogThatDoesNotReadIsEmpty() {
        val rows = mapOf("m1" to "asset-1", "m2" to null)
        assertEquals("""[{"media_id":"m1","asset_id":"asset-1"},{"media_id":"m2","asset_id":null}]""", ImmichDeviceLog.encode(rows))
        assertEquals(rows, ImmichDeviceLog.decode(ImmichDeviceLog.encode(rows)))
        assertTrue(ImmichDeviceLog.decode(null).isEmpty())
        assertTrue(ImmichDeviceLog.decode("not json").isEmpty())
    }
}
