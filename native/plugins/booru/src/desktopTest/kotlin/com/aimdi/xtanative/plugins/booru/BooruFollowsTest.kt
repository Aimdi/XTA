package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.NamedSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class BooruFollowsTest {
    private fun file(): File = File(Files.createTempDirectory("booru-follows").toFile(), "follows.json")

    private fun follows(): BooruFollows = BooruFollows(StoreFile(file()))

    private fun row(name: String) = NamedSubscriptionRow(id = name, name = name, createdAt = "2026-01-02 03:04:05")

    @Test
    fun aFollowIsKeptOnTheDeviceInItsNormalForm(): Unit = runBlocking {
        val path = file()
        BooruFollows(StoreFile(path)).add("  Blue_Eyes   Solo ")
        assertEquals(listOf("blue_eyes solo"), BooruFollows(StoreFile(path)).load())
    }

    @Test
    fun followsAreListedByNameOnceEach(): Unit = runBlocking {
        val follows = follows()
        listOf("zebra", "apple", "mango", "apple").forEach { follows.add(it) }
        assertEquals(listOf("apple", "mango", "zebra"), follows.load())
        assertEquals(listOf("apple", "mango", "zebra"), follows.state.value)
        follows.remove("MANGO")
        assertEquals(listOf("apple", "zebra"), follows.load())
    }

    @Test
    fun aFollowNotesWhenItWasMadeAsXtaWritesIt(): Unit = runBlocking {
        val at = Instant.parse("2026-03-04T05:06:07Z")
        val follows = BooruFollows(StoreFile(file())) { at }
        follows.add("solo")
        assertEquals(XtaTime.sqlite(at), J.parse(follows.export()).list.single()["created_at"].string)
    }

    @Test
    fun theBackupSectionIsXtasRowsBothWays(): Unit = runBlocking {
        val follows = follows()
        follows.import("""[{"id":"Solo","name":"Solo","in_feed":1,"created_at":"2026-01-02 03:04:05"},{"id":"comic","name":"comic","in_feed":0}]""")
        assertEquals(listOf("comic", "solo"), follows.load())
        val exported = J.parse(follows.export()).list.mapNotNull { NamedSubscriptionRow.from(it) }
        assertEquals(listOf(NamedSubscriptionRow("comic", "comic", inFeed = false), row("solo")), exported)
    }

    @Test
    fun theFileAnEarlierBuildWroteReads(): Unit = runBlocking {
        val path = file().apply { writeText("""{"v":1,"rows":[{"id":"solo","name":"solo","in_feed":1}],"adopted":"0f"}""") }
        assertEquals(listOf("solo"), BooruFollows(StoreFile(path)).load())
    }

    @Test
    fun aBareListOfXtaRowsReads(): Unit = runBlocking {
        val path = file().apply { writeText("""[{"id":"solo","name":"solo","in_feed":1,"created_at":"2026-01-02 03:04:05"},{"id":"x"}]""") }
        assertEquals(listOf("solo"), BooruFollows(StoreFile(path)).load())
    }

    @Test
    fun aDamagedFileReadsAsNoFollows(): Unit = runBlocking {
        val path = file().apply { writeText("{\"rows\": [3, {\"name\": 4}]") }
        assertEquals(emptyList(), BooruFollows(StoreFile(path)).load())
    }
}
