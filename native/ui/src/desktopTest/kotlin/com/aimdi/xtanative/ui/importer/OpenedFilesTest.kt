package com.aimdi.xtanative.ui.importer

import com.aimdi.xtanative.ui.plugins.OpenedPluginFile
import com.aimdi.xtanative.ui.settings.BackupRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What a file opened with the app is taken for, and how an OPML list reaches the plugin the reader picks. */
class OpenedFilesTest {
    private val opml = """<?xml version="1.0"?>
        <!-- exported -->
        <opml version="2.0"><body><outline xmlUrl="https://example.org/feed"/></body></opml>"""

    @Test
    fun anOpmlListIsKnownByItsNameOrItsRoot() {
        assertTrue(OpenedFile.isOpml("feeds.OPML", "anything"))
        assertTrue(OpenedFile.isOpml("subscriptions.xml", opml))
        assertTrue(OpenedFile.isOpml("export", "﻿<opml version=\"1.0\"/>"))
        assertFalse(OpenedFile.isOpml("feed.xml", "<?xml version=\"1.0\"?><rss><channel/></rss>"))
        assertFalse(OpenedFile.isOpml("backup.json", "{\"formatVersion\": 2}"))
        assertIs<OpenedFile.Opml>(OpenedFile.of("subscriptions.xml", opml.toByteArray()))
    }

    @Test
    fun aBackupIsPreviewedAndAnythingElseRefused() {
        val backup = """{"formatVersion": 2, "subscriptions": [{"id": "1", "screen_name": "nasa", "name": "NASA", "verified": 0, "in_feed": 1}]}"""
        assertIs<OpenedFile.Backup>(OpenedFile.of("xta-2026-11-27.json", backup.toByteArray()))
        assertEquals(OpenedFile.Refused(BackupRead.NotABackup), OpenedFile.of("notes.txt", "hello".toByteArray()))
        assertEquals(OpenedFile.Refused(BackupRead.NotABackup), OpenedFile.of("gone.json", null), "gone or too large")
    }

    @Test
    fun theOpenedListGoesOnceToAPluginThatTakesIt() {
        val file = OpenedPluginFile.of("feeds.opml", "text/x-opml", opml.toByteArray())
        OpenedPluginFile.offer(file, now = 1_000)
        assertNull(OpenedPluginFile.take({ _, mime -> mime == "application/json" }, now = 1_001), "a screen that does not take it leaves it")
        assertSame(file, OpenedPluginFile.take({ name, _ -> name.endsWith(".opml") }, now = 1_002))
        assertNull(OpenedPluginFile.take({ _, _ -> true }, now = 1_003), "once")
        OpenedPluginFile.offer(file, now = 1_000)
        assertNull(OpenedPluginFile.take({ _, _ -> true }, now = 100_000), "not long after")
    }
}
