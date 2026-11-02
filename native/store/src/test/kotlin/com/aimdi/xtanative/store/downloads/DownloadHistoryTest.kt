package com.aimdi.xtanative.store.downloads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.saved.json
import com.aimdi.xtanative.store.saved.tempStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class DownloadHistoryTest {
    private fun entry(id: String, status: DownloadStatus = DownloadStatus.QUEUED) =
        DownloadEntry(id, "https://media.example/$id.jpg", "$id.jpg", "content://provider/tree/photos", createdAt = 1_767_225_600_000, status = status)

    @Test
    fun aDownloadRunningWhenTheAppStoppedCanBeTriedAgain() = runBlocking {
        val dir = tempStore()
        FileDownloadHistory(dir).write(listOf(entry("pending"), entry("complete", DownloadStatus.COMPLETED)))
        val loaded = FileDownloadHistory(dir).read()
        assertEquals(DownloadStatus.INTERRUPTED, loaded.first().status)
        assertTrue(loaded.first().canRetry)
        assertEquals(DownloadStatus.COMPLETED, loaded.last().status)
        assertFalse(File(dir.root, "downloads/history.json.part").exists())
    }

    @Test
    fun theHistoryIsBoundedAndSkipsWhatDoesNotRead() = runBlocking {
        val dir = tempStore()
        val history = FileDownloadHistory(dir)
        history.write((0 until 250).map { entry("$it", DownloadStatus.COMPLETED) })
        assertEquals(200, history.read().size)
        val file = File(dir.root, "downloads/history.json")
        file.writeText("""{"version":1,"entries":[null,{},${entry("valid").toJson()},${entry("valid").toJson()}]}""")
        assertEquals(listOf("valid"), history.read().map { it.id })
        file.writeText("x".repeat(2_000_001))
        assertTrue(history.read().isEmpty())
        file.writeText("""{"version":2,"entries":[]}""")
        assertTrue(history.read().isEmpty())
    }

    @Test
    fun anEntryIsCheckedAndCleanedAsItIsRead() {
        val good = entry("a", DownloadStatus.COMPLETED).copy(subfolder = "R-18/Mika_42")
        assertEquals("R-18/Mika_42", good.toJson()["folder"].let { (it as JsonPrimitive).content })
        assertEquals(good, DownloadEntry.fromJson(J(good.toJson())))
        fun with(key: String, value: String) = J(JsonObject(good.toJson() + (key to JsonPrimitive(value))))
        assertEquals("etc", DownloadEntry.fromJson(with("folder", "../../etc"))?.subfolder)
        assertEquals("photo.jpg", DownloadEntry.fromJson(with("name", "../../photo.jpg"))?.fileName)
        assertNull(DownloadEntry.fromJson(with("id", "../escape")))
        assertNull(DownloadEntry.fromJson(with("url", "file:///etc/passwd")))
        assertNull(DownloadEntry.fromJson(with("status", "unknown")))
        assertNull(DownloadEntry.fromJson(J(json("""{"id":"a"}"""))))
        assertFalse(DownloadEntry("b", good.url, "b.png").toJson().containsKey("folder"))
        assertTrue(DownloadEntry.fromJson(J(good.copy(background = true).toJson()))!!.background)
    }

    @Test
    fun theScreenListsWhatRunsOldestFirstThenWhatEndedNewestFirst() {
        fun at(id: String, time: Long, status: DownloadStatus) = DownloadEntry(id, "https://media.example/$id", id, createdAt = time, status = status)
        val state = DownloadCenterState(
            listOf(at("done-old", 1, DownloadStatus.COMPLETED), at("run-new", 4, DownloadStatus.DOWNLOADING), at("done-new", 3, DownloadStatus.FAILED), at("run-old", 2, DownloadStatus.QUEUED)),
        )
        assertEquals(listOf("run-old", "run-new", "done-new", "done-old"), state.shown.map { it.id })
        assertEquals(0.5, entry("a").copy(received = 5, total = 10).fraction)
        assertNull(entry("a").copy(received = 5).fraction)
        assertEquals(1.0, entry("a").copy(received = 50, total = 10).fraction)
    }
}
