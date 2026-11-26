package com.aimdi.xtanative.plugins.rss

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RssOpmlDropTest {
    @Test
    fun opmlAndXmlFilesAreTaken() {
        assertTrue(isOpml("feeds.opml", "text/x-opml"))
        assertTrue(isOpml("Feeds.XML", "application/octet-stream"))
        assertTrue(isOpml("export", "application/xml"))
        assertTrue(isOpml("feeds.txt", "text/plain"))
    }

    @Test
    fun picturesAndOtherFilesAreNot() {
        assertFalse(isOpml("cat.png", "image/png"))
        assertFalse(isOpml("backup.json", "application/json"))
    }
}
