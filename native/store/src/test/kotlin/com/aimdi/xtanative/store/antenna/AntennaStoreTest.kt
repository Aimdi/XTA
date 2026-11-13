package com.aimdi.xtanative.store.antenna

import com.aimdi.xtanative.store.StoreDir
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AntennaStoreTest {
    private val root: File = Files.createTempDirectory("antennas").toFile()
    private var now = 1_000L
    private var ids = 0

    private fun store() = AntennaStore(StoreDir(root), clock = { now }, newId = { "id${++ids}" })

    @Test
    fun aSavedAntennaIsListedNewestFirstAndKeptForTheNextLaunch() = runBlocking {
        val store = store()
        val tech = store.save("  Tech ", listOf("flutter", " dart lang "), listOf("job"), AntennaScope.FOLLOWING)!!
        now = 2_000
        store.save("News", listOf("news"))
        assertEquals(Antenna("id1", "Tech", listOf("flutter", "dart lang"), listOf("job"), AntennaScope.FOLLOWING, 1_000), tech)
        assertEquals(listOf("News", "Tech"), store().load().map { it.name })
        assertEquals(listOf("News", "Tech"), store.antennas.first().map { it.name })
    }

    @Test
    fun theEditorsRuleHoldsNoNameOrNoTermIsNoAntenna() = runBlocking {
        val store = store()
        assertNull(store.save(" ", listOf("flutter")))
        assertNull(store.save("Tech", listOf(" ", "")))
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun anEditKeepsWhenTheAntennaWasMade() = runBlocking {
        val store = store()
        val tech = store.save("Tech", listOf("flutter"))!!
        now = 5_000
        val edited = store.save("Tech news", listOf("flutter", "kotlin"), scope = AntennaScope.SEARCH, id = tech.id)!!
        assertEquals(1_000, edited.createdAt)
        assertEquals(listOf(edited), store().load())
    }

    @Test
    fun deletingAndRestoringReplaceByIdAsXtasImportDoes() = runBlocking {
        val store = store()
        val tech = store.save("Tech", listOf("flutter"))!!
        store.save("News", listOf("news"))
        store.delete(tech.id)
        assertEquals(listOf("News"), store.load().map { it.name })
        store.merge(listOf(Antenna("id2", "News again", listOf("press"), createdAt = 3_000), Antenna("old", "Old", listOf("x"), createdAt = 10)))
        assertEquals(listOf("News again", "Old"), store().load().map { it.name })
    }

    @Test
    fun aFileThatCannotBeReadIsKeptAsideBeforeAnythingReplacesIt() = runBlocking {
        File(root, "antennas.json").writeText("""{"v": 9}""")
        val store = store()
        assertEquals(emptyList(), store.load())
        store.save("Tech", listOf("flutter"))
        assertEquals("""{"v": 9}""", File(root, "antennas.json.unreadable").readText())
        assertEquals(listOf("Tech"), store().load().map { it.name })
    }
}
