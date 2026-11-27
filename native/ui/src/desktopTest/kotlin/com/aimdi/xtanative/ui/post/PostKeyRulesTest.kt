package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.ai.TranslationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the desktop's m, v and t do to the post the reading keys are on. */
class PostKeyRulesTest {
    private val picture = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/a.jpg", 10, 10)
    private val post = XPost("1", XUser("u", "u", "U"), null, "hallo")

    @Test
    fun theFirstPictureOpensUnlessTheGateHidesIt() {
        assertNull(PostKeyRules.mediaIndex(post, sensitiveHidden = false), "nothing to open")
        assertEquals(0, PostKeyRules.mediaIndex(post.copy(media = listOf(picture, picture)), sensitiveHidden = true))
        val sensitive = post.copy(media = listOf(picture), sensitive = true)
        assertNull(PostKeyRules.mediaIndex(sensitive, sensitiveHidden = true), "behind the sensitive gate")
        assertEquals(0, PostKeyRules.mediaIndex(sensitive, sensitiveHidden = false), "the gate opened")
    }

    @Test
    fun tTranslatesAndGoesBack() {
        val runs = TranslationRuns(CoroutineScope(Job() + Dispatchers.Unconfined))
        val translation = PostTranslation(post, runs.of("k"), runs) { TranslatedText { it.copy(text = "hello") } }
        PostKeyRules.toggle(translation)
        assertEquals(TranslationStatus.TRANSLATED, translation.status)
        assertEquals("hello", translation.shown.text)
        PostKeyRules.toggle(translation)
        assertEquals(TranslationStatus.ORIGINAL, translation.status)
        assertEquals("hallo", translation.shown.text)
    }
}
