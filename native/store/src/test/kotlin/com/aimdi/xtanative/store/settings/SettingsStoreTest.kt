package com.aimdi.xtanative.store.settings

import com.aimdi.xtanative.store.StoreDir
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive

class SettingsStoreTest {
    private val root: File = Files.createTempDirectory("settings").toFile()

    @Test
    fun aFirstLoadIsXtasFreshInstallAndIsWrittenDown() = runBlocking {
        val store = SettingsStore(StoreDir(root))
        assertNull(store.state.value)
        val settings = store.load()
        assertEquals(ThemeBackground.SYSTEM, settings[ThemePrefs.background])
        assertTrue(settings[MediaPrefs.qualitySplitMigrated], "XTA's launch steps ran")
        assertTrue(File(root, "settings.json").isFile)
        assertEquals(settings, store.state.value)
    }

    @Test
    fun aChangeIsKeptAndReadBackByTheNextLaunch() = runBlocking {
        SettingsStore(StoreDir(root)).apply {
            set(ThemePrefs.accent, ThemeAccent.PINK)
            set(MediaPrefs.gridColumns, 5)
            update { it.with("plugin.someday.option", PrefValue.Texts(listOf("a"))) }
        }
        val again = SettingsStore(StoreDir(root))
        assertEquals(ThemeAccent.PINK, again.get(ThemePrefs.accent))
        assertEquals(5, again.get(MediaPrefs.gridColumns))
        assertEquals(PrefValue.Texts(listOf("a")), again.load().values["plugin.someday.option"], "keys this build does not know are kept")
        again.reset(MediaPrefs.gridColumns)
        assertEquals(3, SettingsStore(StoreDir(root)).get(MediaPrefs.gridColumns))
    }

    @Test
    fun anObserverSeesTheValueAndEachChangeOfIt() = runBlocking {
        val store = SettingsStore(StoreDir(root))
        val seen = mutableListOf<Boolean>()
        val watching = launch(start = CoroutineStart.UNDISPATCHED) { store.observe(PostsPrefs.zenMode).collect { seen += it } }
        withTimeout(5_000) {
            while (seen.isEmpty()) delay(5)
            store.set(ThemePrefs.accent, ThemeAccent.GREEN)
            store.set(PostsPrefs.zenMode, true)
            while (seen.size < 2) delay(5)
        }
        watching.cancel()
        assertEquals(listOf(false, true), seen, "a change to another preference is not one of zen mode's")
    }

    @Test
    fun aBackupsSettingsAreRestoredWithoutItsCredentials() = runBlocking {
        val store = SettingsStore(StoreDir(root))
        store.set(AiPrefs.apiKey, "sk-mine")
        store.restore(
            mapOf(
                "theme.preset" to JsonPrimitive("x_look_lights_out"),
                "ai.api_key" to JsonPrimitive("sk-theirs"),
                "media.grid_columns" to JsonPrimitive(2),
            ),
        )
        val restored = SettingsStore(StoreDir(root)).load()
        assertEquals(ThemeBackground.LIGHTS_OUT, restored[ThemePrefs.background], "XTA's launch steps run on what came in")
        assertEquals("sk-mine", restored[AiPrefs.apiKey])
        assertEquals(2, restored[MediaPrefs.gridColumns])
        val exported = store.exported()!!
        assertEquals(JsonPrimitive(2), exported["media.grid_columns"])
        assertFalse("ai.api_key" in exported)
    }

    @Test
    fun aFileThatCannotBeReadIsKeptAsideBeforeAnythingReplacesIt() = runBlocking {
        File(root, "settings.json").writeText("{not json")
        val store = SettingsStore(StoreDir(root))
        assertEquals(MediaQuality.MEDIUM, store.get(MediaPrefs.imageQuality))
        assertEquals("{not json", File(root, "settings.json.unreadable").readText())
        assertTrue(store.get(GeneralPrefs.introSeen), "it was no first launch")
        store.set(PostsPrefs.calmMode, true)
        assertTrue(SettingsStore(StoreDir(root)).get(PostsPrefs.calmMode))
    }
}
