package com.aimdi.xtanative.store.history

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.PrefSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What a history entry opens again. */
enum class HistoryKind(val stored: String) {
    POST("post"),
    PROFILE("profile"),
    ;

    companion object {
        fun of(raw: String?): HistoryKind? = entries.firstOrNull { it.stored == raw }
    }
}

/**
 * A post or a profile the reader opened, at [at] (epoch milliseconds): a
 * post by its id with its author's name and handle and the start of its
 * text, a profile by its handle ([target]) with its name.
 */
data class HistoryEntry(
    val kind: HistoryKind,
    val target: String,
    val name: String,
    val handle: String,
    val text: String = "",
    val at: Long,
) {
    /** One entry a post or profile: opening it again moves it to the top. */
    val key: String get() = kind.stored + ":" + target.lowercase()

    /** Whether the search [query] finds it, in its name, handle or text, whatever the case. */
    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || listOf(name, handle, text).any { it.contains(q, ignoreCase = true) }
    }
}

/** The history's rules, apart from its file. */
object HistoryRules {
    /** How many entries the history keeps: the oldest go past it. */
    const val CAP = 500

    /** How much of a post's text an entry keeps: enough to find it again by. */
    const val TEXT_CHARS = 280

    /** [entries] with [entry] first, any earlier entry of the same post or profile gone, at most [CAP]. */
    fun recorded(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        (listOf(entry.copy(text = entry.text.take(TEXT_CHARS))) + entries.filterNot { it.key == entry.key }).take(CAP)
}

/**
 * The posts and profiles opened, newest first, on this device only: one
 * file of the store (written owner-only, as every store file is), never
 * read by a backup or its WebDAV sync, and never sent anywhere. A file
 * that cannot be read is kept as `history.json.unreadable`, the history
 * starting afresh.
 */
class ViewHistory(private val dir: StoreDir) {
    private val file = dir.file(FILE)
    private val mutex = Mutex()
    private val current = MutableStateFlow<List<HistoryEntry>?>(null)

    /** The history, read on first collection, then each change. */
    val entries: Flow<List<HistoryEntry>> = flow {
        load()
        emitAll(current.filterNotNull())
    }

    suspend fun load(): List<HistoryEntry> = mutex.withLock { loaded() }

    suspend fun record(entry: HistoryEntry) = mutex.withLock { save(HistoryRules.recorded(loaded(), entry)) }

    suspend fun remove(key: String) = mutex.withLock {
        val all = loaded()
        if (all.any { it.key == key }) save(all.filterNot { it.key == key })
    }

    /** Everything forgotten: the file goes too. */
    suspend fun clear() = mutex.withLock {
        file.delete()
        current.value = emptyList()
    }

    private suspend fun loaded(): List<HistoryEntry> {
        current.value?.let { return it }
        val text = file.read()
        val stored = text?.let(::decode)
        if (text != null && stored == null) dir.file("$FILE.unreadable").write(text)
        return (stored ?: emptyList()).also { current.value = it }
    }

    private suspend fun save(entries: List<HistoryEntry>) {
        file.write(encode(entries))
        current.value = entries
    }

    internal companion object {
        const val FILE = "history.json"
        private const val VERSION = 1

        fun encode(entries: List<HistoryEntry>): String = buildJsonObject {
            put("v", VERSION)
            put(
                "entries",
                JsonArray(
                    entries.map { entry ->
                        buildJsonObject {
                            put("kind", entry.kind.stored)
                            put("target", entry.target)
                            put("name", entry.name)
                            put("handle", entry.handle)
                            if (entry.text.isNotEmpty()) put("text", entry.text)
                            put("at", entry.at)
                        }
                    },
                ),
            )
        }.toString()

        fun decode(text: String): List<HistoryEntry>? {
            val root = J.parse(text)
            if (root["v"].int != VERSION) return null
            return root["entries"].list.mapNotNull { row ->
                HistoryEntry(
                    kind = HistoryKind.of(row["kind"].string) ?: return@mapNotNull null,
                    target = row["target"].string?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    name = row["name"].string.orEmpty(),
                    handle = row["handle"].string.orEmpty(),
                    text = row["text"].string.orEmpty(),
                    at = row["at"].long ?: 0,
                )
            }.take(HistoryRules.CAP)
        }
    }
}

/** History's own switch, which XTA never had: outside XtaPrefs, as DesktopPrefs are. */
object HistoryPrefs : PrefSection() {
    /** History keeps the posts and profiles opened, on this device only ([ViewHistory]); on until the reader turns it off. */
    val keep = flag("history.keep", true, seeded = false)
}
