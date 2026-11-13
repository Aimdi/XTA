package com.aimdi.xtanative.ui.archive

import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.store.saved.ArchiveAnnotation
import com.aimdi.xtanative.store.saved.suggestedArchiveTags
import com.aimdi.xtanative.store.settings.AiPrefs
import com.aimdi.xtanative.store.settings.Settings

/** The archive document's rules (archive_document_screen.dart). */
object ArchiveRules {
    /** The text annotated: the article's, then what was read off a picture. */
    fun text(articleText: String, annotation: ArchiveAnnotation): String = "$articleText\n${annotation.extracted}".trim()

    /** The tags offered: the reader's first, then up to eight the text suggests, each once. */
    fun tags(title: String, text: String, annotation: ArchiveAnnotation): List<String> =
        LinkedHashSet(annotation.tags + suggestedArchiveTags("$title $text")).toList()

    /** The reader's AI connection, as Settings › AI holds it; transcription is offered only once it is whole. */
    fun aiConfig(settings: Settings): AiConfig = AiConfig(settings[AiPrefs.baseUrl], settings[AiPrefs.apiKey], settings[AiPrefs.model])

    /** The passage selected in [text], trimmed; null when nothing but spaces is. */
    fun passage(text: String, start: Int, end: Int): String? {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        return text.substring(from, to).trim().takeIf { it.isNotEmpty() }
    }
}
