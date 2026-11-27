package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A file a device store cannot read (the reader's notes written by a newer
 * build, or one they cannot open) shows as empty but is never written over:
 * the next change is refused instead of making the empty list real.
 */
class StoredJsonTest {
    private val dir = tempStore()
    private val file = File(dir.root, "saved/words.json")

    private fun stored() = StoredJson(
        dir,
        "saved/words.json",
        emptyList(),
        { root -> root["words"].takeIf { root["v"].int == 1 }?.let(::strings) },
        { words -> buildJsonObject { put("v", 1); put("words", JsonArray(words.map(::JsonPrimitive))) } },
    )

    private fun written(vararg words: String) = file.apply {
        parentFile.mkdirs()
        writeText(buildJsonObject { put("v", 1); put("words", JsonArray(words.map(::JsonPrimitive))) }.toString())
    }

    private fun asides() = file.parentFile.listFiles { f -> f.name.startsWith("words.json.unreadable-") }.orEmpty().toList()

    @Test
    fun aFileANewerBuildWroteIsNeverWrittenOverAndIsCopiedAside() = runBlocking {
        file.apply { parentFile.mkdirs(); writeText("""{"v":2,"words":["kept"]}""") }
        val store = stored()
        assertEquals(emptyList(), store.get())
        assertTrue("saved/words.json" in dir.unreadable.value)
        assertFailsWith<IOException> { store.update { it + "new" } }
        assertEquals("""{"v":2,"words":["kept"]}""", file.readText(), "the empty list did not become real")
        assertEquals("""{"v":2,"words":["kept"]}""", asides().single().readText())
    }

    @Test
    fun eachRunsCopyAsideIsKeptBesideTheEarlierOnes() = runBlocking {
        file.apply { parentFile.mkdirs(); writeText("first damage") }
        stored().get()
        file.writeText("second damage")
        stored().get()
        stored().get()
        assertEquals(setOf("first damage", "second damage"), asides().map { it.readText() }.toSet())
    }

    @Test
    fun aFileThatCannotBeOpenedIsNotWrittenOver() = runBlocking {
        written("mine")
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("---------"))
        try {
            if (file.canRead()) return@runBlocking // run as root: nothing to test
            val store = stored()
            assertEquals(emptyList(), store.get())
            assertFailsWith<IOException> { store.update { it + "new" } }
        } finally {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        assertEquals(listOf("mine"), strings(J.parse(file.readText())["words"]))
    }

    @Test
    fun onceTheFileReadsAgainChangesAreWrittenAndTheNoticeGoes() = runBlocking {
        file.apply { parentFile.mkdirs(); writeText("{broken") }
        val store = stored()
        store.get()
        written("mended")
        store.update { it + "new" }
        assertEquals(listOf("mended", "new"), stored().get())
        assertTrue(dir.unreadable.value.isEmpty())
    }
}
