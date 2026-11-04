package com.aimdi.xtanative.ui.offline

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.offline.OfflineEntry
import com.aimdi.xtanative.store.offline.OfflineFile
import com.aimdi.xtanative.store.offline.OfflineKind
import com.aimdi.xtanative.store.offline.OfflineState
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.saved.KeepNotice
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineRulesTest {
    private val file = OfflineFile("media-0", "https://pbs.twimg.com/a.jpg", "image/jpeg", 2048)

    private fun entry(kind: OfflineKind, files: List<OfflineFile> = listOf(file), total: Int = 2, sensitive: Boolean = false, articleBytes: Long = 0) =
        OfflineEntry("saved:1", "d", "Title", "maya", kind, total, files, 0, articleBytes = articleBytes, sensitive = sensitive)

    @Test
    fun anEntryOpensItsWayAndASensitiveOneAsksFirst() {
        assertEquals(OfflineOpen.MEDIA, OfflineRules.open(entry(OfflineKind.MEDIA)))
        assertEquals(OfflineOpen.SENSITIVE_MEDIA, OfflineRules.open(entry(OfflineKind.MEDIA, sensitive = true)))
        assertEquals(OfflineOpen.ARTICLE, OfflineRules.open(entry(OfflineKind.ARTICLE, articleBytes = 10)))
        assertEquals(OfflineOpen.NOTHING, OfflineRules.open(entry(OfflineKind.MEDIA, files = emptyList())))
        assertEquals(OfflineIcon.SENSITIVE, OfflineRules.icon(entry(OfflineKind.MEDIA, sensitive = true)))
        assertEquals(OfflineIcon.ARTICLE, OfflineRules.icon(entry(OfflineKind.ARTICLE)))
        assertEquals(OfflineIcon.MEDIA, OfflineRules.icon(entry(OfflineKind.MEDIA)))
    }

    @Test
    fun theDetailCountsWhatWasKept() {
        assertEquals(OfflineDetail(article = false, saved = 1, total = 2), OfflineRules.detail(entry(OfflineKind.MEDIA)))
        assertEquals(OfflineDetail(article = true, saved = 1, total = 2), OfflineRules.detail(entry(OfflineKind.ARTICLE, articleBytes = 10)))
    }

    @Test
    fun sizesAreWrittenAsXtaWritesThem() {
        assertEquals("512 B", OfflineRules.bytes(512, Locale.ENGLISH))
        assertEquals("1.5 KiB", OfflineRules.bytes(1536, Locale.ENGLISH))
        assertEquals("2 MiB", OfflineRules.bytes(2L * 1024 * 1024, Locale.ENGLISH))
        assertEquals("1,5 KiB", OfflineRules.bytes(1536, Locale.FRENCH))
        assertEquals(0.5f, OfflineRules.fullness(50, 100))
        assertEquals(1f, OfflineRules.fullness(500, 100))
    }

    @Test
    fun keepingSaysHowItWent() {
        val kept = OfflineState(entries = listOf(entry(OfflineKind.MEDIA)))
        assertEquals(KeepNotice.OfflineKept(article = false, saved = 1, total = 2), OfflineRules.afterKeeping(kept, "saved:1"))
        assertEquals(KeepNotice.OfflineUnavailable, OfflineRules.afterKeeping(OfflineState(), "saved:1"))
        assertEquals(KeepNotice.OfflineFailed, OfflineRules.afterKeeping(kept.copy(failed = setOf("saved:1")), "saved:1"))
        assertTrue(OfflineRules.canClear(OfflineState(busy = setOf("x"))))
        assertFalse(OfflineRules.canClear(OfflineState()))
    }

    @Test
    fun aSavedPostsRowSaysWhatItKeeps() {
        val tweet = SavedContent.of(
            J.parse(
                """{"id_str":"1","full_text":"hi","user":{"screen_name":"maya"},"extended_entities":{"media":[
                {"type":"photo","media_url_https":"https://pbs.twimg.com/a.jpg"},{"type":"photo","media_url_https":"https://pbs.twimg.com/b.jpg"}]}}""",
            ).raw,
        )
        assertEquals(SavedOfflineLine.NotKept(2), SavedOfflineLine.of(tweet, null))
        assertEquals(SavedOfflineLine.Kept(OfflineDetail(false, 1, 2)), SavedOfflineLine.of(tweet, entry(OfflineKind.MEDIA)))
        val video = SavedContent.of(
            J.parse("""{"xtaPlugin":"reddit","post":{"id":"r","title":"t","subreddit":"s","permalink":"/r/s/r","videoFallbackUrl":"https://v.redd.it/x.mp4"}}""").raw,
        )
        assertEquals(SavedOfflineLine.VideoOnly, SavedOfflineLine.of(video, null))
    }
}
