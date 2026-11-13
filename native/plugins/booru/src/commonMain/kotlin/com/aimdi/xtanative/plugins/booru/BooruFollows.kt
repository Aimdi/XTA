package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.NamedSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

/**
 * The tags followed and the searches saved here (XTA's BooruTagsStore, its
 * `booru_subscription` table): one row each, by the query's normal form, in
 * name order. Nothing is told to any booru. Kept in the plugin's own folder
 * as the rows XTA's backup writes for them (`booruSubscriptions`), which
 * [export] and [import] carry as they are.
 */
internal class BooruFollows(private val file: StoreFile, private val clock: () -> Instant = Instant::now) {
    private val mutable = MutableStateFlow<List<String>?>(null)

    /** The queries followed, in name order; null until the file has been read. */
    val state: StateFlow<List<String>?> = mutable.asStateFlow()

    private val mutableRows = MutableStateFlow<List<NamedSubscriptionRow>?>(null)

    /** Their rows, with when each was followed; null until the file has been read. */
    val rows: StateFlow<List<NamedSubscriptionRow>?> = mutableRows.asStateFlow()

    private val mutex = Mutex()
    private var current: List<NamedSubscriptionRow>? = null

    suspend fun load(): List<String> = mutex.withLock { loaded().map { it.name } }

    /** [query] followed, as its normal form; a query already followed is followed again from now, as XTA's insert replaces its row. */
    suspend fun add(query: String) {
        val id = BooruQuery.normalise(query) ?: return
        change { rows -> rows.filterNot { it.id == id } + NamedSubscriptionRow(id, id, inFeed = true, createdAt = XtaTime.sqlite(clock())) }
    }

    suspend fun remove(query: String) {
        val id = BooruQuery.normalise(query) ?: return
        change { rows -> rows.filterNot { it.id == id } }
    }

    /** The rows as XTA's backup writes `booruSubscriptions`. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    /** A backup's rows in place of these. */
    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    private suspend fun change(edit: (List<NamedSubscriptionRow>) -> List<NamedSubscriptionRow>) = mutex.withLock { save(edit(loaded())) }

    private suspend fun save(next: List<NamedSubscriptionRow>) {
        val sorted = sorted(next)
        file.write(encode(sorted))
        current = sorted
        mutableRows.value = sorted
        mutable.value = sorted.map { it.name }
    }

    private suspend fun loaded(): List<NamedSubscriptionRow> = current ?: sorted(decode(file.read().orEmpty())).also {
        current = it
        mutableRows.value = it
        mutable.value = it.map { row -> row.name }
    }

    private companion object {
        /** As XTA lists them: by name, whatever its case (`COLLATE NOCASE`). */
        fun sorted(rows: List<NamedSubscriptionRow>): List<NamedSubscriptionRow> =
            rows.distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        fun normalised(row: NamedSubscriptionRow): NamedSubscriptionRow? =
            BooruQuery.normalise(row.name)?.let { query -> row.copy(id = query, name = query) }

        fun encode(rows: List<NamedSubscriptionRow>): String = JsonArray(rows.map { it.toJson() }).toString()

        /** XTA's rows, or the file an earlier build wrote, which held them under `rows`. */
        fun decode(text: String): List<NamedSubscriptionRow> {
            val root = J.parse(text)
            val rows = if (root.raw is JsonArray) root.list else root["rows"].list
            return rows.mapNotNull { NamedSubscriptionRow.from(it) }.mapNotNull(::normalised)
        }
    }
}
