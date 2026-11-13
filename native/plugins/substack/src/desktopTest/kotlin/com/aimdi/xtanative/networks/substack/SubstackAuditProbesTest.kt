package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's substack_audit_probes_test, the probes no other test here pins. */
class SubstackAuditProbesTest {
    @Test
    fun theArticleCacheRemembersThatAPostIsAVideo(): Unit = runBlocking {
        val cache = SubstackArticleCache(MemoryFiles())
        val video = SubstackPost("7", "Clip", "clip", "https://fieldnotes.substack.com", "Field Notes", bodyHtml = "<p>Clip</p>", hasVideoUpload = true)
        cache.put(video)
        val cached = cache.get(video.publication, "clip")
        assertTrue(video.isVideo)
        assertEquals(true, cached?.isVideo, "BUG-09: false before XTA's fix")
    }
}
