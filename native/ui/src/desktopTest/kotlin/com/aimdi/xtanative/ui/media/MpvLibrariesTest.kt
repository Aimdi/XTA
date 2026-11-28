package com.aimdi.xtanative.ui.media

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvLibrariesTest {
    private val dir: File = Files.createTempDirectory("mpv-libraries").toFile().apply { deleteOnExit() }
    private val noCache = File(dir, "no-cache")

    private fun cache(vararg strings: String) = File(dir, "ld.so.cache").apply {
        writeBytes(("glibc-ld.so.cache1.1\u0000" + strings.joinToString("") { "$it\u0000" }).toByteArray(Charsets.ISO_8859_1))
    }

    @Test
    fun theLinkersCacheNamesTheLibraryOrItsPath() {
        assertTrue(MpvLibraries.findable("libmpv.so.2", null, cache("libc.so.6", "/usr/lib/libmpv.so.2"), emptyList()))
        assertTrue(MpvLibraries.findable("libmpv.so.2", null, cache("libmpv.so.2", "/usr/lib/libc.so.6"), emptyList()))
        assertFalse(MpvLibraries.findable("libmpv.so.2", null, cache("/usr/lib/libmpv.so.2.5", "/usr/lib/xlibmpv.so.2"), emptyList()))
    }

    @Test
    fun aFolderOfLdLibraryPathOrTheSystemsHasIt() {
        val own = File(dir, "own").apply { mkdirs() }
        File(own, "libmpv.so.1").writeText("")
        assertTrue(MpvLibraries.findable("libmpv.so.1", "/nowhere:${own.path}", noCache, emptyList()))
        assertTrue(MpvLibraries.findable("libmpv.so.1", null, noCache, listOf(own.path)))
        assertFalse(MpvLibraries.findable("libmpv.so.2", "/nowhere:${own.path}", noCache, listOf(own.path)))
    }
}
