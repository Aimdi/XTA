package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.ui.plugins.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginDownloadsTest {
    @Test
    fun aPluginsMediaIsNamedAfterThePluginAndTheFile() {
        assertEquals("reddit-abc123.jpg", PluginDownloads.fileName("reddit", "https://i.redd.it/abc123.jpg?width=640"))
        assertEquals("bluesky-my picture.png", PluginDownloads.fileName("bluesky", "https://cdn.example/img/my%20picture.png"))
        assertEquals("booru-media", PluginDownloads.fileName("booru", "https://example.com/"))
        assertEquals("xta-a.webp", PluginDownloads.fileName(" ", "https://example.com/a.webp"))
    }

    @Test
    fun aLongNameKeepsItsStartAndItsExtension() {
        val name = PluginDownloads.fileName("pixiv", "https://i.pximg.net/" + "x".repeat(400) + ".png")
        assertEquals(true, name.startsWith("pixiv-xxx"))
        assertEquals(true, name.endsWith(".png"))
        assertEquals(true, name.toByteArray().size <= 190)
    }

    @Test
    fun aMadeFileWithoutAnExtensionGetsTheOneItsTypeStandsFor() {
        assertEquals("ugoira_1.gif", PluginDownloads.named("ugoira_1", "image/gif"))
        assertEquals("novel.txt", PluginDownloads.named("novel", "text/plain; charset=utf-8"))
        assertEquals("work.zip", PluginDownloads.named("work.zip", "image/gif"), "its own extension stays")
        assertEquals("odd", PluginDownloads.named("odd", "application/x-unknown"))
        assertEquals("xta-file.png", PluginDownloads.named("  ", "image/png"))
    }

    @Test
    fun aPhotoSavesItselfAVideoItsFileAndAStreamNothing() {
        assertEquals("https://x.test/a.jpg", PluginDownloads.url(MediaItem("https://x.test/a.jpg")))
        assertEquals("https://x.test/v.mp4", PluginDownloads.url(MediaItem("https://x.test/p.jpg", MediaItem.Kind.VIDEO, videoUrl = "https://x.test/v.mp4")))
        assertNull(PluginDownloads.url(MediaItem("https://x.test/p.jpg", MediaItem.Kind.VIDEO, videoUrl = "https://x.test/hls/playlist.m3u8")))
        assertNull(PluginDownloads.url(MediaItem("https://x.test/p.jpg", MediaItem.Kind.GIF)))
        assertNull(PluginDownloads.url(MediaItem("data:image/png;base64,AAAA")))
    }
}
