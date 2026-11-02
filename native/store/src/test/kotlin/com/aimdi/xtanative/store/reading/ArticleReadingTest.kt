package com.aimdi.xtanative.store.reading

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.tempStore
import com.aimdi.xtanative.store.saved.waitUntil
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import com.aimdi.xtanative.store.saved.timed

class ArticleReadingTest {
    /** A reader's time in view, moved by the test. */
    private class ReaderClock(var time: Long = 0) : ActiveClock {
        override var isRunning = false
        override val elapsedMs: Long get() = time

        override fun start() {
            isRunning = true
        }

        override fun stop() {
            isRunning = false
        }
    }

    private fun progress(fraction: Double = 0.42, scroll: Boolean = true, end: Boolean = false) =
        """{"fraction":$fraction,"paragraph":8,"leading":-32.5,"userScrolled":$scroll,"interacted":$scroll,"atEnd":$end}"""

    private val dir: StoreDir = tempStore()
    private val journal = ReadingJournal(dir)
    private val appearance = ReadingAppearanceStore(dir)
    private var now = 1_000L

    private suspend fun CoroutineScope.open(
        id: String,
        onCompleted: suspend () -> Unit = {},
        clock: ActiveClock = ActiveClock(),
        journal: ReadingJournal = this@ArticleReadingTest.journal,
        remembers: () -> Boolean = { true },
        journalOnOpen: Boolean = true,
        automatic: Boolean = true,
    ) = ArticleReading.open(journal, appearance, id, this, onCompleted, remembers, journalOnOpen, allowAutomaticCompletion = automatic, clock = clock, now = { now++ })

    @Test
    fun openingIsNotFinishingAndThePlaceAndTypeSurviveARestart() = timed {
        var completed = 0
        val first = open("rss:feed:item", onCompleted = { completed++ })
        assertFalse(first.state.value.point.completed)
        first.appearance(fontSize = 24.0, lineHeight = 2.0)
        first.receiveProgress(progress())
        first.close()
        assertEquals(0, completed)

        val reopened = open("rss:feed:item")
        assertEquals(24.0, reopened.state.value.fontSize)
        assertEquals(2.0, reopened.state.value.lineHeight)
        assertEquals(0.42, reopened.state.value.point.fraction)
        assertEquals(8, reopened.state.value.point.paragraph)
        assertEquals(-32.5, reopened.state.value.point.leading)
        assertTrue(reopened.state.value.resumed)
        reopened.close()

        val otherNetwork = open("substack:pub:item")
        assertEquals(24.0, otherNetwork.state.value.fontSize)
        assertEquals(0.0, otherNetwork.state.value.point.fraction)
        otherNetwork.close()
    }

    @Test
    fun onlyAnActiveReaderScrollingToTheEndAfterReadingAWhileFinishes() = timed {
        val completed = AtomicInteger()
        val clock = ReaderClock()
        val reading = open("a", onCompleted = { completed.incrementAndGet() }, clock = clock)
        reading.setActive(true)
        reading.receiveProgress(progress(fraction = 1.0, end = true))
        assertEquals(0, completed.get())
        clock.time = 14_000
        reading.receiveProgress(progress(fraction = 1.0, scroll = false, end = true))
        delay(50)
        assertEquals(0, completed.get(), "arriving at the end while the place is put back is not reading")
        reading.setActive(false)
        reading.receiveProgress(progress(fraction = 1.0, end = true))
        delay(50)
        assertEquals(0, completed.get())
        reading.setActive(true)
        reading.receiveProgress(progress(fraction = 1.0, end = true))
        waitUntil { reading.state.value.point.completed }
        assertEquals(1, completed.get())
        reading.complete()
        assertEquals(1, completed.get())
        reading.close()
        assertTrue(journal.point("a")!!.completed)
    }

    @Test
    fun aPreviewFinishesOnlyWhenAskedAndAFailedFinishCanBeTriedAgain() = timed {
        var calls = 0
        val reading = open("preview", onCompleted = { if (++calls == 1) error("disk unavailable") }, clock = ReaderClock(60_000), automatic = false)
        reading.setActive(true)
        reading.receiveProgress(progress(fraction = 1.0, end = true))
        delay(50)
        assertEquals(0, calls)
        reading.complete()
        assertFalse(reading.state.value.point.completed)
        reading.complete()
        assertEquals(2, calls)
        assertTrue(reading.state.value.point.completed)
        reading.close()
    }

    @Test
    fun readersOpenTogetherBothKeepTheirPlaceAndOnlyTheNewest300Stay() = timed {
        journal.replace((0 until 305).associate { "old-$it" to ArticleReadPoint(updatedAt = it.toLong()) })
        now = 10_000
        val first = open("first")
        val second = open("second")
        first.receiveProgress(progress(fraction = 0.3))
        second.receiveProgress(progress(fraction = 0.8))
        first.close()
        second.close()
        val places = journal.content()
        assertEquals(ReadingJournal.LIMIT, places.size)
        assertEquals(0.3, places["first"]?.fraction)
        assertEquals(0.8, places["second"]?.fraction)
        assertFalse("old-0" in places)
    }

    @Test
    fun damagedFilesAndReportsAreBoundedAndSafe() = timed {
        File(dir.root, "reading").mkdirs()
        File(dir.root, "reading/articles.json").writeText("{broken")
        File(dir.root, "reading/appearance.json").writeText("""{"fontSize":9999,"lineHeight":-10}""")
        val reading = open("a")
        assertEquals(28.0, reading.state.value.fontSize)
        assertEquals(1.4, reading.state.value.lineHeight)
        reading.receiveProgress("{broken")
        reading.receiveProgress("""{"fraction":9000,"paragraph":-8,"leading":"bad"}""")
        reading.receiveProgress("""{"fraction":0.5,"padding":"${"x".repeat(1600)}"}""")
        assertEquals(1.0, reading.state.value.point.fraction)
        assertEquals(0, reading.state.value.point.paragraph)
        assertEquals(0.0, reading.state.value.point.leading)
        assertFalse(reading.state.value.point.completed)
        reading.close()
    }

    @Test
    fun withPlacesNotKeptTheTypeIsSharedButNoPlaceIsReadOrWritten() = timed {
        journal.put("a", ArticleReadPoint(fraction = 0.5, paragraph = 12))
        appearance.set(ReadingAppearance(fontSize = 23.0))
        val reading = open("a", remembers = { false })
        assertEquals(0.0, reading.state.value.point.fraction)
        assertEquals(23.0, reading.state.value.fontSize)
        reading.receiveProgress(progress())
        reading.close()
        assertEquals(0.5, journal.point("a")?.fraction)
    }

    @Test
    fun aJournalOfItsOwnIsJoinedOnlyOnceTheReaderMovesAndEmptiedOnItsOwn() = timed {
        journal.put("rss:item", ArticleReadPoint(fraction = 0.5, updatedAt = 1))
        val own = ReadingJournal(dir, "own")
        suspend fun openOwn(id: String, remembers: () -> Boolean = { true }) = open(id, journal = own, remembers = remembers, journalOnOpen = false)

        val glanced = openOwn("glanced")
        glanced.receiveProgress(progress(scroll = false))
        glanced.close()
        assertTrue(own.content().isEmpty(), "opening and putting the place back leave no trace")

        val read = openOwn("read")
        read.receiveProgress(progress(fraction = 0.3))
        read.close()
        assertEquals(0.3, own.point("read")?.fraction)

        val reopened = openOwn("read")
        assertEquals(0.3, reopened.state.value.point.fraction)
        reopened.receiveProgress(progress(fraction = 0.6, scroll = false))
        reopened.close()
        assertEquals(0.6, own.point("read")?.fraction, "a journaled article keeps its place")

        val paused = openOwn("paused", remembers = { false })
        paused.receiveProgress(progress())
        paused.close()
        assertNull(own.point("paused"))
        assertEquals(setOf("rss:item"), journal.content().keys, "the shared journal is left alone")

        own.forget()
        assertTrue(own.content().isEmpty())
        assertEquals(setOf("rss:item"), journal.content().keys)
    }

    @Test
    fun aClosedReaderIgnoresLateReports() = timed {
        val reading = open("a")
        reading.close()
        reading.receiveProgress(progress())
        assertEquals(0.0, reading.state.value.point.fraction)
    }

    @Test
    fun startingOverKeepsAFinishedArticleFinished() = timed {
        val reading = open("a")
        reading.complete()
        reading.receiveProgress(progress())
        reading.startOver()
        assertEquals(0.0, reading.state.value.point.fraction)
        assertTrue(reading.state.value.point.completed)
        reading.close()
    }

    @Test
    fun xtasJournalReadsAsPlacesAndBack() {
        val xta = J.parse("""{"a":{"fraction":0.5,"paragraph":12,"leading":-3.5,"completed":true,"updatedAt":7},"b":{}}""")
        val places = ReadingJournal.fromXta(xta)
        assertEquals(ArticleReadPoint(0.5, 12, -3.5, true, 7), places["a"])
        assertEquals(ArticleReadPoint(), places["b"])
        assertEquals(places, ReadingJournal.fromXta(J(ReadingJournal.toXta(places))))
    }

    @Test
    fun theFirstBlockStillShowingIsWhereANativeReaderIs() {
        val blocks = listOf(BlockExtent(2, 40.0, 100.0), BlockExtent(0, -300.0, 100.0), BlockExtent(1, -60.0, 100.0))
        assertEquals(VisibleBlock(1, -60.0), firstVisibleBlock(blocks))
        assertNull(firstVisibleBlock(listOf(BlockExtent(0, -100.0, 100.0))))
    }
}
