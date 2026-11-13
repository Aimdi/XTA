package com.aimdi.xtanative.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.services.ai.AiClient
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiPrompts
import com.aimdi.xtanative.store.saved.ArchiveAnnotation
import com.aimdi.xtanative.store.saved.archivePlainText
import com.aimdi.xtanative.ui.downloads.PickedFile
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An archived article as its document screen shows it. */
data class ArchiveUi(
    val title: String = "",
    /** The article's text and what was read off a picture: what is highlighted. */
    val text: String = "",
    val annotation: ArchiveAnnotation = ArchiveAnnotation(),
    val tags: List<String> = emptyList(),
    /** The reader's AI connection, when there is a whole one: transcription is offered. */
    val ai: AiConfig? = null,
    val extracting: Boolean = false,
)

/**
 * An article kept offline, to annotate (archive_document_screen.dart): its
 * plain text, the passages highlighted with a note each, tags (the reader's
 * and those the text suggests), and the text of a screenshot read by the
 * reader's own AI when one is set. The notes stay on the device.
 */
class ArchiveViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    private val article = MutableStateFlow<Pair<String, String>?>(null)
    private val extracting = MutableStateFlow(false)
    private val ai by lazy { AiClient(OkHttpXHttp()) }

    private val annotation = graph.archiveNotes.state.map { it?.get(id) ?: ArchiveAnnotation() }

    val ui: StateFlow<ArchiveUi> = combine(article, annotation, graph.settings.settings, extracting) { article, annotation, settings, busy ->
        val (title, body) = article ?: ("" to "")
        val text = ArchiveRules.text(body, annotation)
        ArchiveUi(
            title = title,
            text = text,
            annotation = annotation,
            tags = ArchiveRules.tags(title, text, annotation),
            ai = ArchiveRules.aiConfig(settings).takeIf { it.isConfigured },
            extracting = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUi())

    init {
        viewModelScope.launch {
            graph.archiveNotes.of(id)
            graph.offline.load()
            val kept = graph.offline.article(id)
            val title = graph.offline.state.value.entry(id)?.title ?: kept?.title.orEmpty()
            val text = kept?.let { withContext(Dispatchers.Default) { archivePlainText(it.bodyHtml) } }.orEmpty()
            article.value = title to text
        }
    }

    fun toggleTag(tag: String) = change { graph.archiveNotes.toggleTag(id, tag) }

    fun removeHighlight(quote: String) = change { graph.archiveNotes.removeHighlight(id, quote) }

    suspend fun highlight(quote: String, note: String?) {
        graph.archiveNotes.highlight(id, quote, note)
    }

    /** Sends the picked PNG or JPEG to the reader's AI for its text, which is kept beside the article's. */
    fun extract(pick: suspend () -> PickedFile?) {
        val config = ui.value.ai ?: return
        if (extracting.value) return
        extracting.value = true
        viewModelScope.launch {
            try {
                val picked = pick() ?: return@launch
                val image = AiPrompts.imageDataUrl(picked.bytes) ?: error("A PNG or JPEG of at most 8 MB")
                val text = ai.complete(config, AiPrompts.TRANSCRIBE, image)
                if (text.isEmpty()) error("No text came back")
                graph.archiveNotes.setExtracted(id, text)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                KeepNotices.show(KeepNotice.SomethingWentWrong)
            } finally {
                extracting.update { false }
            }
        }
    }

    private fun change(edit: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                edit()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                KeepNotices.show(KeepNotice.NoteSaveFailed)
            }
        }
    }
}
