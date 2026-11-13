package com.aimdi.xtanative.ui.archive

import com.aimdi.xtanative.store.saved.ArchiveAnnotation
import com.aimdi.xtanative.store.settings.AiPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveRulesTest {
    @Test
    fun theTextIsTheArticlesThenWhatAPictureSaid() {
        assertEquals("Body\nRead off a picture", ArchiveRules.text("Body", ArchiveAnnotation(extracted = "Read off a picture")))
        assertEquals("Body", ArchiveRules.text("Body", ArchiveAnnotation()))
    }

    @Test
    fun theReadersTagsLeadThenTheSuggestionsEachOnce() {
        val annotation = ArchiveAnnotation(tags = setOf("kotlin"))
        val tags = ArchiveRules.tags("Compose", "compose compose kotlin native native native", annotation)
        assertEquals("kotlin", tags.first())
        assertEquals(listOf("kotlin", "compose", "native"), tags, "equal counts keep the order they first came in")
    }

    @Test
    fun transcriptionWaitsForAWholeConnection() {
        assertFalse(ArchiveRules.aiConfig(Settings()).isConfigured)
        val set = Settings().with(AiPrefs.baseUrl, "https://api.x.ai/v1").with(AiPrefs.apiKey, "k").with(AiPrefs.model, "grok-4")
        assertTrue(ArchiveRules.aiConfig(set).isConfigured)
        assertEquals("api.x.ai", ArchiveRules.aiConfig(set).host)
    }

    @Test
    fun aSelectionIsThePassageItCovers() {
        val text = "One two three"
        assertEquals("two", ArchiveRules.passage(text, 3, 8))
        assertEquals("two", ArchiveRules.passage(text, 8, 3), "a selection made backwards")
        assertNull(ArchiveRules.passage(text, 3, 4))
        assertEquals("One two three", ArchiveRules.passage(text, 0, 99))
    }
}
