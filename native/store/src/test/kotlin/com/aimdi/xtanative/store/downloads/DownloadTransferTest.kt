package com.aimdi.xtanative.store.downloads

import com.aimdi.xtanative.store.saved.answer
import com.aimdi.xtanative.store.saved.waitUntil
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.aimdi.xtanative.store.saved.timed

class DownloadTransferTest {
    private val staging = Files.createTempDirectory("staging").toFile()
    private val toFolder = DownloadEntry("photo", "https://media.example/photo.jpg", "photo.jpg", treeUri = "content://provider/tree/photos")
    private val video = DownloadEntry("video", "https://example.org/video.mp4", "video.mp4")

    /** Answers each request with the next step, which may check what was asked. */
    private class Scripted(vararg steps: (Map<String, String>) -> com.aimdi.xtanative.store.downloads.MediaResponse) : MediaHttp {
        private val left = steps.toMutableList()
        val requests = CopyOnWriteArrayList<Map<String, String>>()

        override suspend fun get(url: String, headers: Map<String, String>): MediaResponse {
            requests += headers
            return left.removeAt(0)(headers)
        }
    }

    private fun saving(expected: ByteArray? = null, saves: MutableList<File> = mutableListOf()) = SaveStaged { _, file, _ ->
        expected?.let { assertContentEquals(it, file.readBytes()) }
        saves += file
        "saved"
    }

    private suspend fun attempt(http: MediaHttp, entry: DownloadEntry = video, save: SaveStaged = saving()) =
        runCatching { DownloadTransfer(http, staging, save).run(entry, DownloadCancellation(), { _, _ -> }, { }) }

    private fun stagingIsEmpty() = staging.listFiles().orEmpty().isEmpty()

    @Test
    fun aDownloadIsStreamedToDiskWithItsProgressAndTheStagedFileGoes() = timed {
        val progress = CopyOnWriteArrayList<Long>()
        val saves = mutableListOf<File>()
        val http = Scripted({ answer(200, byteArrayOf(1, 2), byteArrayOf(3, 4, 5), length = 5) })
        val saved = DownloadTransfer(http, staging, saving(byteArrayOf(1, 2, 3, 4, 5), saves)).run(toFolder, DownloadCancellation(), { received, _ -> progress += received }, { })
        assertEquals("saved", saved)
        assertEquals(listOf(0L, 2L, 5L), progress)
        assertEquals("identity", http.requests.single()["accept-encoding"])
        assertFalse(saves.single().exists())
    }

    @Test
    fun aShortAnswerOrAServerErrorNeverReachesTheDestination() = timed {
        val saves = mutableListOf<File>()
        assertIs<DownloadHttpException>(attempt(Scripted({ answer(200, byteArrayOf(1, 2), length = 4) }), toFolder, saving(saves = saves)).exceptionOrNull())
        assertTrue(stagingIsEmpty())
        val error = attempt(Scripted({ answer(503) }), toFolder, saving(saves = saves)).exceptionOrNull()
        assertEquals(503, (error as DownloadHttpException).code)
        assertTrue(saves.isEmpty())
    }

    @Test
    fun aCancelStopsTheRequestAndALateAnswerReachesNothing() = timed {
        val asked = CompletableDeferred<Unit>()
        val late = CompletableDeferred<MediaResponse>()
        val saves = mutableListOf<File>()
        val cancellation = DownloadCancellation()
        val http = MediaHttp { _, _ ->
            asked.complete(Unit)
            late.await()
        }
        val result = async { runCatching { DownloadTransfer(http, staging, saving(saves = saves)).run(toFolder, cancellation, { _, _ -> }, { }) } }
        asked.await()
        cancellation.cancel()
        late.complete(answer(200, byteArrayOf(1, 2)))
        assertIs<DownloadCancelled>(result.await().exceptionOrNull())
        assertTrue(saves.isEmpty())
        assertTrue(stagingIsEmpty())
    }

    private suspend fun interrupt(tag: String = "\"v1\"") {
        val failed = attempt(Scripted({ answer(200, byteArrayOf(1, 2), length = 4, headers = mapOf("ETag" to tag)) }))
        assertIs<DownloadHttpException>(failed.exceptionOrNull())
    }

    @Test
    fun aNewAttemptResumesTheKeptBytesWithRangeAndIfRange() = timed {
        interrupt()
        val http = Scripted({ headers ->
            assertEquals("bytes=2-", headers["range"])
            assertEquals("\"v1\"", headers["if-range"])
            answer(206, byteArrayOf(3, 4), length = 2, headers = mapOf("etag" to "\"v1\"", "content-range" to "bytes 2-3/4"))
        })
        assertEquals("saved", attempt(http, save = saving(byteArrayOf(1, 2, 3, 4))).getOrThrow())
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun aChangedFileOrAnIgnoredRangeStartsOverInsteadOfJoiningBytes() = timed {
        interrupt()
        val http = Scripted({ answer(200, byteArrayOf(7, 8, 9), length = 3, headers = mapOf("etag" to "\"v2\"")) })
        assertEquals("saved", attempt(http, save = saving(byteArrayOf(7, 8, 9))).getOrThrow())
    }

    @Test
    fun anInvalidContentRangeIsRefusedAndTheNextAttemptStartsClean() = timed {
        interrupt()
        val wrong = Scripted({ answer(206, byteArrayOf(3, 4), length = 2, headers = mapOf("etag" to "\"v1\"", "content-range" to "bytes 1-2/4")) })
        assertIs<DownloadHttpException>(attempt(wrong).exceptionOrNull())
        val clean = Scripted({ headers ->
            assertNull(headers["range"])
            answer(200, byteArrayOf(5), length = 1)
        })
        assertEquals("saved", attempt(clean, save = saving(byteArrayOf(5))).getOrThrow())
    }

    @Test
    fun a416IsTriedOnceMoreWithoutARange() = timed {
        interrupt()
        val http = Scripted({ answer(416) }, { headers ->
            assertNull(headers["range"])
            answer(200, byteArrayOf(9), length = 1)
        })
        assertEquals("saved", attempt(http, save = saving(byteArrayOf(9))).getOrThrow())
        assertEquals(2, http.requests.size)
    }

    @Test
    fun aWeakValidatorCannotVouchForJoiningBytes() = timed {
        interrupt(tag = "W/\"v1\"")
        val http = Scripted({ headers ->
            assertNull(headers["range"])
            answer(200, byteArrayOf(4), length = 1)
        })
        assertEquals("saved", attempt(http, save = saving(byteArrayOf(4))).getOrThrow())
    }

    @Test
    fun aPartialAnswerWithAChangedEtagNeverReachesTheDestination() = timed {
        interrupt()
        val saves = mutableListOf<File>()
        val http = Scripted({ answer(206, byteArrayOf(3, 4), length = 2, headers = mapOf("etag" to "\"v2\"", "content-range" to "bytes 2-3/4")) })
        assertIs<DownloadHttpException>(attempt(http, save = saving(saves = saves)).exceptionOrNull())
        assertTrue(saves.isEmpty())
    }

    @Test
    fun downloadsWithoutAFolderOpenTheSaveDialogOneAtATime() = timed {
        val opened = CopyOnWriteArrayList<String>()
        val dialogs = ConcurrentHashMap<String, CompletableDeferred<String?>>()
        val phases = CopyOnWriteArrayList<DownloadStatus>()
        val save = SaveStaged { entry, _, _ ->
            opened += entry.fileName
            dialogs.computeIfAbsent(entry.fileName) { CompletableDeferred() }.await()
        }
        val http = MediaHttp { _, _ -> answer(200, byteArrayOf(1, 2, 3), length = 3) }
        fun start(name: String, cancellation: DownloadCancellation = DownloadCancellation()) = async {
            runCatching { DownloadTransfer(http, staging, save).run(DownloadEntry(name, "https://example.org/$name", name), cancellation, { _, _ -> }, { phases += it }) }
        }
        val a = start("a.jpg")
        waitUntil { opened.size == 1 }
        val waiting = DownloadCancellation()
        val b = start("b.jpg", waiting)
        val c = start("c.jpg")
        delay(100)
        assertEquals(1, opened.size, "the others wait for the dialog")
        waiting.cancel()
        assertIs<DownloadCancelled>(b.await().exceptionOrNull())
        dialogs.getValue(opened.first()).complete("content://provider/document/1")
        waitUntil { opened.size == 2 }
        dialogs.getValue(opened.last()).complete("content://provider/document/2")
        assertEquals("content://provider/document/1", a.await().getOrThrow())
        assertEquals("content://provider/document/2", c.await().getOrThrow())
        assertFalse("b.jpg" in opened, "a download cancelled while it waits never opens one")
        assertTrue(phases.all { it == DownloadStatus.CHOOSING_LOCATION })
    }

    @Test
    fun withoutSharedFoldersABackgroundDownloadAsksWhereToGo() = timed {
        var seen: DownloadEntry? = null
        val phases = CopyOnWriteArrayList<DownloadStatus>()
        val transfer = DownloadTransfer(MediaHttp { _, _ -> answer(200, byteArrayOf(1), length = 1) }, staging, SaveStaged { entry, _, _ -> seen = entry; "picked" }) { false }
        transfer.run(DownloadEntry("bg", "https://example.org/cat.jpg", "cat.jpg", background = true), DownloadCancellation(), { _, _ -> }, { phases += it })
        assertFalse(seen!!.background)
        assertEquals(listOf(DownloadStatus.CHOOSING_LOCATION), phases)
    }

    @Test
    fun whatInterruptedDownloadsLeftIsClearedAfterAWeek() = timed {
        val old = File(staging, "old.part").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000) }
        val fresh = File(staging, "fresh.part").apply { writeText("x") }
        DownloadTransfer.clearInterruptedFiles(staging)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        File(staging, "video.part.json").writeText("{}")
        DownloadTransfer.discardEntry(staging, DownloadEntry("video", "https://example.org/v.mp4", "v.mp4"))
        assertFalse(File(staging, "video.part.json").exists())
    }
}
