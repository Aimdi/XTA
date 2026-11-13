package com.aimdi.xtanative.pluginhost

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstalledPluginsTest {
    private val reddit = InstalledPlugin(
        id = "reddit",
        version = "1.0.3",
        apiVersion = 1,
        entryClass = "com.aimdi.xtanative.plugins.reddit.RedditPlugin",
        fileName = "xta-plugin-reddit-1.0.3.jar",
        sha256 = "ab".repeat(32),
        size = 398765,
        installedAt = 1_700_000_000_000,
        category = PluginCategory.COMMUNITIES,
        name = LocalizedText(mapOf("en" to "Reddit", "de" to "Reddit")),
        description = LocalizedText(mapOf("" to "Read Reddit")),
    )

    @Test
    fun aRecordReadsBackAsItWasWritten() {
        assertEquals(mapOf("reddit" to reddit), InstalledRecords.decode(InstalledRecords.encode(listOf(reddit))))
        assertEquals(emptyMap(), InstalledRecords.decode(""))
        assertEquals(emptyMap(), InstalledRecords.decode("""{"v": 9, "plugins": []}"""))
    }

    @Test
    fun aRecordThatIsNotWholeOrNamesAFileOutsideItsFolderIsLeftOut() {
        val bad = listOf(
            reddit.copy(id = "../reddit"),
            reddit.copy(version = ".."),
            reddit.copy(fileName = "../../evil.jar"),
            reddit.copy(sha256 = "abc"),
            reddit.copy(size = 0),
        )
        bad.forEach { assertEquals(emptyMap(), InstalledRecords.decode(InstalledRecords.encode(listOf(it))), it.toString()) }
    }

    @Test
    fun aKeptFileIsReadOnlyInItsVersionsFolderAndAnUpdateReplacesTheVersionBefore() {
        val files = PluginFiles(tempDir("plugins"))
        val old = files.downloadOf("reddit", "1.0.3", PluginPlatform.DESKTOP).apply { parentFile.mkdirs(); writeText("old") }
        val kept = files.keep(old, "reddit", "1.0.3", reddit.fileName)
        assertEquals(File(files.root, "reddit/1.0.3/xta-plugin-reddit-1.0.3.jar"), kept)
        assertEquals(kept, files.fileOf(reddit))
        assertTrue(isReadOnly(kept))
        assertFalse(old.exists())

        val update = files.downloadOf("reddit", "1.1.0", PluginPlatform.DESKTOP).apply { writeText("new") }
        files.keep(update, "reddit", "1.1.0", "xta-plugin-reddit-1.1.0.jar")
        files.keepOnly("reddit", "1.1.0")
        assertEquals(listOf("1.1.0"), File(files.root, "reddit").list()?.toList())
        assertEquals(3, files.bytesOf("reddit"))
    }

    @Test
    fun aDownloadOfAnotherVersionMakesWayAndRemovingAPluginLeavesNothingOfIt() {
        val files = PluginFiles(tempDir("plugins"))
        val stale = files.downloadOf("reddit", "1.0.0", PluginPlatform.DESKTOP).apply { parentFile.mkdirs(); writeText("stale") }
        val current = files.downloadOf("reddit", "1.0.3", PluginPlatform.DESKTOP).apply { writeText("partial") }
        assertFalse(stale.exists())
        files.keep(files.downloadOf("redditx", "1.0.0", PluginPlatform.DESKTOP).apply { parentFile.mkdirs(); writeText("x") }, "redditx", "1.0.0", "x.jar")

        files.remove("reddit")
        assertFalse(current.exists())
        assertFalse(File(files.root, "reddit").exists())
        assertTrue(File(files.root, "redditx/1.0.0/x.jar").exists(), "another plugin whose id starts the same stays")
        assertEquals(0, files.bytesOf("reddit"))
    }
}
