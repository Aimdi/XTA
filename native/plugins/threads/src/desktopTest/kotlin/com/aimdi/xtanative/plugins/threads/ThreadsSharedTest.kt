package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsLinkCard
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsVideoRendition
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SecretPrefs
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.TextRun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Threads in the app's shared timelines, its Saved and its settings, as XTA keeps them. */
class ThreadsSharedTest {
    private fun post(id: String, handle: String = "zuck", at: Long? = 1_000, reply: Boolean = false) =
        ThreadsPost(id = id, handle = handle, authorName = handle, text = "post $id", publishedAt = at?.let(Instant::ofEpochSecond), isReply = reply)

    @Test
    fun aMixedFeedTakesTenOfEachAccountARepostCountingForItsReposter() {
        val zuck = (1..12).map { post("z$it") }
        val reposts = (1..11).map { post("r$it", handle = "meta").repostedBy("rp$it", "mosseri", "Adam") }
        val mixed = ThreadsShared.mixed(zuck + reposts + post("m1", handle = "mosseri"), FeedKinds.ALL)
        assertEquals((1..10).map { "z$it" }, mixed.filter { it.handle == "zuck" }.map { it.id })
        assertEquals((1..10).map { "rp$it" }, mixed.filter { it.sourceHandle == "mosseri" }.map { it.id }, "mosseri's own post is past the ten")
    }

    @Test
    fun hiddenRepliesUseNoneOfTheTenAndAPostWithoutADateIsLeftOut() {
        val posts = (1..10).map { post("reply$it", reply = true) } + post("own") + post("undated", at = null)
        val mixed = ThreadsShared.mixed(posts, FeedKinds(replies = false))
        assertEquals(listOf("own"), mixed.map { it.id })
        assertEquals(10, ThreadsShared.mixed((1..15).map { post("$it") }, FeedKinds.ALL).size)
    }

    @Test
    fun theSharedRowShowsWhoRepostedWhomItAnswersAndOnlyTheCountsMetaGave() {
        val video = ThreadsMedia("https://cdn/poster.jpg", 1.5, "a clip", listOf(ThreadsVideoRendition("https://cdn/720.mp4", 720, 1280), ThreadsVideoRendition("https://cdn/360.mp4", 360, 640)))
        val quoted = post("q", handle = "meta")
        val shown = ThreadsShared.post(
            post("1").copy(
                text = "hi @meta",
                fragments = listOf(ThreadsTextFragment(ThreadsTextFragment.Kind.TEXT, "hi "), ThreadsTextFragment(ThreadsTextFragment.Kind.MENTION, "@meta", "meta")),
                media = listOf(video),
                linkCard = ThreadsLinkCard("https://example.com", "Example"),
                quoted = quoted,
                replyToHandle = "mosseri",
                likeCount = 3,
                url = "https://www.threads.com/@zuck/post/A",
            ).repostedBy("1", "adam", "Adam M"),
            MediaQuality.MEDIUM,
        )
        assertEquals("Adam M", shown.repostedBy)
        assertEquals("mosseri", shown.replyingTo)
        assertEquals(listOf(TextRun.Plain("hi "), TextRun.Mention("@meta", "meta")), shown.text)
        assertEquals(MediaItem.Kind.VIDEO, shown.media.single().kind)
        assertEquals("https://example.com", shown.card?.url)
        assertEquals("q", shown.quoted?.id)
        assertEquals(3L, shown.likes)
        assertEquals(0L, shown.replies, "a count Meta did not give is none")
        assertEquals(Instant.ofEpochSecond(1_000), shown.createdAt)
    }

    @Test
    fun aSavedPostIsXtasThreadsRecordUnderItsLinkAndReadsBack() {
        val post = post("7").copy(url = "https://www.threads.com/@zuck/post/C", media = listOf(ThreadsMedia("https://cdn/a.jpg", 1.0)))
        val archive = ThreadsArchive.of(post)
        assertEquals("threads:https://www.threads.com/@zuck/post/C", archive.id, "the id XTA's link save gave it, so it still shows saved")
        assertEquals("zuck", archive.authorId)
        assertEquals("threads:8", ThreadsArchive.id(post("8")), "a post without a link is kept by its id")

        val kept = SavedContent.of(archive.content)
        assertEquals(SavedContent.Kind.THREADS, kept.kind)
        assertEquals("threads", kept.network)
        assertEquals(listOf("https://cdn/a.jpg"), kept.photos.map { it.url })
        assertEquals(post, ThreadsArchive.post(archive.content))
        assertNull(ThreadsArchive.post(PluginPostArchive.link("threads", "https://www.threads.com/@zuck/post/D", "zuck", "old").content), "a link record is Saved's own card")
    }

    @Test
    fun theSessionAndTokenStayOutOfEveryExportAndUninstallingErasesAllThreadsKeeps() {
        val secrets = listOf(ThreadsPrefs.directCookies, ThreadsPrefs.directBearer, ThreadsPrefs.apiToken)
        secrets.forEach { assertTrue(SecretPrefs.isSecret(it.key), it.key) }
        val settings = secrets.fold(Settings()) { kept, pref -> kept.with(pref, "kept") }.with(ThreadsPrefs.instance, "https://rsshub.example")
        val exported = settings.exported().orEmpty()
        secrets.forEach { assertFalse(it.key in exported, it.key) }
        assertTrue(ThreadsPrefs.instance.key in exported)

        // The store erases what is kept under the plugin's own keys when it is uninstalled.
        val own = PluginSettings.prefixOf("threads")
        ThreadsPrefs.prefs.forEach { assertTrue(it.key.startsWith(own), it.key) }
    }
}
