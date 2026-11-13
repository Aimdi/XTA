package com.aimdi.xtanative.store

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

class StoreFileTest {
    @Test
    fun aFileReadsBackWhatWasWrittenAndNothingBefore() = runBlocking {
        val dir = StoreDir(Files.createTempDirectory("store").toFile())
        val file = dir.file("saved/posts.json")
        assertNull(file.read())
        file.write("[1]")
        file.write("[1,2]")
        assertEquals("[1,2]", dir.file("saved/posts.json").read())
        assertSame(file, dir.file("saved/posts.json"))
        file.delete()
        assertNull(file.read())
    }
}
