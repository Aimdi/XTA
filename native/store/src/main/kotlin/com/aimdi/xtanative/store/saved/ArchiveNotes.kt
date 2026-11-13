package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup

/**
 * What the reader added to an article kept offline: passages highlighted
 * with a note each (blank for none), tags, and the text read off a picture.
 */
data class ArchiveAnnotation(
    val highlights: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val extracted: String = "",
) {
    val isEmpty: Boolean get() = highlights.isEmpty() && tags.isEmpty() && extracted.isEmpty()

    /** What a search of the archive matches besides the article. */
    val searchable: String get() = "${highlights.entries.joinToString(" ") { "${it.key} ${it.value}" }} ${tags.joinToString(" ")} $extracted"

    /** XTA's JSON for it, as its backup's `archiveAnnotations` carries it. */
    fun toJson(): JsonObject = buildJsonObject {
        put("highlights", JsonObject(highlights.mapValues { JsonPrimitive(it.value) }))
        put("tags", JsonArray(tags.map(::JsonPrimitive)))
        put("extracted", extracted)
    }

    /**
     * This annotation with [incoming] restored into it: highlights and tags it
     * lacks are added, and where both have a note or text, what is here now wins.
     */
    fun merging(incoming: ArchiveAnnotation): ArchiveAnnotation = ArchiveAnnotation(
        highlights = incoming.highlights + highlights,
        tags = incoming.tags + tags,
        extracted = extracted.ifEmpty { incoming.extracted },
    )

    companion object {
        /** XTA's JSON for an annotation; anything not shaped like one is an empty one. */
        fun parse(raw: J): ArchiveAnnotation {
            val highlights = raw["highlights"]
            return ArchiveAnnotation(
                highlights = highlights.keys.mapNotNull { quote -> highlights[quote].string?.let { quote to it } }.toMap(),
                tags = strings(raw["tags"]).toSet(),
                extracted = raw["extracted"].string.orEmpty(),
            )
        }
    }
}

/**
 * The annotations of archived articles, by article id, in
 * `saved/archive-notes.json`. XTA's backup keys each as `archive-note:<id>`
 * ([BACKUP_PREFIX]).
 */
class ArchiveNotesStore(dir: StoreDir) {
    private val stored = StoredJson(dir, "saved/archive-notes.json", emptyMap(), ::decode, ::encode)

    /** Every annotation once read; null before the first call reads them. */
    val state: StateFlow<Map<String, ArchiveAnnotation>?> = stored.state

    suspend fun of(articleId: String): ArchiveAnnotation = stored.get()[articleId] ?: ArchiveAnnotation()

    /** Highlights [quote] with [note], or with none for null; highlighting it again replaces its note. */
    suspend fun highlight(articleId: String, quote: String, note: String?) =
        change(articleId) { it.copy(highlights = it.highlights + (quote to note.orEmpty())) }

    suspend fun removeHighlight(articleId: String, quote: String) = change(articleId) { it.copy(highlights = it.highlights - quote) }

    suspend fun toggleTag(articleId: String, tag: String) =
        change(articleId) { it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag) }

    suspend fun setExtracted(articleId: String, text: String) = change(articleId) { it.copy(extracted = text) }

    /** Every annotation, for the backup. */
    suspend fun content(): Map<String, ArchiveAnnotation> = stored.get()

    suspend fun replace(annotations: Map<String, ArchiveAnnotation>) {
        stored.update { annotations.filterValues { !it.isEmpty } }
    }

    /** Restores [incoming] as XTA's import does: it adds what is missing, and the edits made here win. */
    suspend fun merge(incoming: Map<String, ArchiveAnnotation>) {
        stored.update { current ->
            current + incoming.mapValues { (id, annotation) -> (current[id] ?: ArchiveAnnotation()).merging(annotation) }
        }
    }

    private suspend fun change(articleId: String, edit: (ArchiveAnnotation) -> ArchiveAnnotation): ArchiveAnnotation =
        stored.updateWith { notes ->
            val next = edit(notes[articleId] ?: ArchiveAnnotation())
            (if (next.isEmpty) notes - articleId else notes + (articleId to next)) to next
        }

    private fun encode(notes: Map<String, ArchiveAnnotation>): JsonElement = buildJsonObject {
        put("v", 1)
        put("notes", JsonObject(notes.mapValues { it.value.toJson() }))
    }

    private fun decode(root: J): Map<String, ArchiveAnnotation>? {
        if (root["v"].int != 1) return null
        val notes = root["notes"]
        return notes.keys.associateWith { ArchiveAnnotation.parse(notes[it]) }
    }

    companion object {
        /** How XTA's backup keys an article's annotation. */
        const val BACKUP_PREFIX = "archive-note:"

        /** The key a highlight's note draft is kept under: one per article and passage. */
        fun draftKey(articleId: String, quote: String): String = "highlight:$articleId:${sha256(quote)}"
    }
}

/** The plain text of an article's HTML, scripts and styles left out, its spaces folded: what is annotated and searched. */
fun archivePlainText(html: String): String {
    val document = Jsoup.parse(html)
    document.select("script,style,noscript").remove()
    return document.body().text().replace(Regex("\\s+"), " ").trim()
}

private val stopWords = setOf(
    "this", "that", "with", "from", "have", "your", "about", "https", "there", "their", "would", "which", "these", "them",
    "then", "than", "were", "been", "also", "into", "what", "when", "will", "und", "oder", "eine", "einer", "einen", "nicht",
    "auch", "sich", "para", "pour", "avec", "dans",
)

/** Up to eight tags for [text]: its most frequent words of four letters or digits or more, common words left out. */
fun suggestedArchiveTags(text: String): List<String> =
    Regex("[\\p{L}\\p{N}]{4,}").findAll(text.lowercase())
        .map { it.value }
        .filterNot { it in stopWords }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(8)
        .map { it.key }
