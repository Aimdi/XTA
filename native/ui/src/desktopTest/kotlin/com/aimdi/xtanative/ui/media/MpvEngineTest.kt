package com.aimdi.xtanative.ui.media

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MpvEngineTest {
    private val shim: File = Files.createTempFile("libxta_mpv", ".so").toFile().apply { deleteOnExit() }
    private val newest = (2L shl 16) or 2

    private fun choose(
        shim: File? = this.shim,
        loadShim: (File) -> String? = { null },
        open: (String) -> String? = { null },
        version: Long = newest,
    ) = MpvEngines.choose(shim, loadShim, open, { version })

    @Test
    fun withoutTheShimVideosGoToTheSystemsPlayer() {
        assertEquals(MpvEngineState.Missing("the app carries no video engine"), choose(shim = null))
        val absent = File(shim.parentFile, "no-such-dir/libxta_mpv.so")
        val missing = assertIs<MpvEngineState.Missing>(choose(shim = absent))
        assertTrue(absent.path in missing.reason)
    }

    @Test
    fun aShimThatDoesNotLoadIsNoEngine() {
        val missing = assertIs<MpvEngineState.Missing>(choose(loadShim = { "wrong ELF class" }))
        assertEquals("the video engine did not load: wrong ELF class", missing.reason)
    }

    @Test
    fun theNewestLibraryThatOpensIsTheOne() {
        val tried = ArrayList<String>()
        val ready = choose(open = { library -> tried += library; if (library == "libmpv.so.2") "libmpv.so.2: cannot open shared object file" else null }, version = (1L shl 16) or 109)
        assertEquals(MpvEngineState.Ready("libmpv.so.1", (1L shl 16) or 109), ready)
        assertEquals(listOf("libmpv.so.2", "libmpv.so.1"), tried)
        assertEquals(MpvEngineState.Ready("libmpv.so.2", newest), choose())
    }

    @Test
    fun withoutMpvsLibraryEveryRefusalIsSaid() {
        val missing = assertIs<MpvEngineState.Missing>(choose(open = { "$it: cannot open shared object file" }))
        assertEquals("no mpv library: libmpv.so.2: cannot open shared object file; libmpv.so.1: cannot open shared object file", missing.reason)
    }

    @Test
    fun anMpvOlderThanTheSoftwareRendererIsNoEngine() {
        val missing = assertIs<MpvEngineState.Missing>(choose(version = (1L shl 16) or 107))
        assertEquals("libmpv.so.2 has mpv's client API 1.107, older than 1.109 (mpv 0.33)", missing.reason)
    }

    @Test
    fun theChoiceIsLoggedInAWord() {
        assertEquals("2.2", MpvEngines.versionText(newest))
        assertEquals("XTA Native: video engine libmpv.so.2, mpv client API 2.2", MpvEngines.describe(MpvEngineState.Ready("libmpv.so.2", newest)))
        assertEquals(
            "XTA Native: video engine missing (no mpv library: x): videos open in the system's player",
            MpvEngines.describe(MpvEngineState.Missing("no mpv library: x")),
        )
    }
}
