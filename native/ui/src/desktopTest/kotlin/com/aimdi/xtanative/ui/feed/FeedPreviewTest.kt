package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.feed.FeedRules
import com.aimdi.xtanative.core.feed.FeedShaping
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A feed's cache shown again under its rules as they are now, and the Media choice (lib/group/group_screen.dart, feed_session_cache.dart). */
class FeedPreviewTest {
    private fun thread(id: String, text: String, by: String = "amy", likes: Long = 0) =
        XThread(id, listOf(XPost(id, XUser("id-$by", by, by), Instant.ofEpochSecond(id.toLong()), text, likeCount = likes)))

    @Test
    fun theCacheIsShownAsTheRulesSeeItNowInTheOrderItWasKept() {
        val rules = FeedRules(mutedKeywords = listOf(MutedKeyword("spoiler"), MutedKeyword("crypto", action = KeywordAction.FOLD)))
        val shaping = FeedShaping(sort = FeedSort.POPULAR, rules = rules, postCaps = mapOf("id-amy" to 1))
        val cached = listOf(thread("1", "hello"), thread("3", "a spoiler"), thread("2", "crypto news", likes = 99), thread("4", "more"))
        val shown = FeedPreview.of(cached, shaping)
        assertEquals(listOf("1", "2", "4"), shown.threads.map { it.id }, "muted hidden; order and caps of the cache kept")
        assertEquals(setOf("2"), shown.folds.keys)
        assertTrue(shown.folds.getValue("2").contains("crypto"))
    }

    @Test
    fun nothingToHideShowsTheCacheAsItIs() {
        val cached = listOf(thread("2", "b"), thread("1", "a"))
        assertEquals(Shown(cached, emptyMap()), FeedPreview.of(cached, FeedShaping()))
    }

    @Test
    fun aFeedsMediaChoiceIsKeptByItsKeyForThisRun() {
        assertFalse(MediaModes.isOn("following"))
        MediaModes.set("following", true)
        MediaModes.set("g1", true)
        assertTrue(MediaModes.isOn("following"))
        MediaModes.set("following", false)
        assertFalse(MediaModes.isOn("following"))
        assertEquals(setOf("g1"), MediaModes.keys.value)
        MediaModes.set("g1", false)
    }
}
