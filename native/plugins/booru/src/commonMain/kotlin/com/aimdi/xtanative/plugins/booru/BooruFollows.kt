package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.NamedSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The tags followed and the searches saved here (XTA's BooruTagsStore, its
 * `booru_subscription` table): one row each, by the query's normal form, in
 * name order. Nothing is told to any booru. Kept in the plugin's own folder,
 * in the rows XTA's backup writes for them (`booruSubscriptions`).
 *
 * A restored XTA backup leaves its rows with the app ([adopt] takes them as
 * XTA's import does, once for each set of rows); [export] and [import] are the
 * plugin's own backup section, which also says which set was taken, so a
 * restore of it on another device does not take them again.
 */
internal class BooruFollows(private val file: StoreFile, private val clock: () -> Instant = Instant::now) {
    private class Kept(val rows: List<NamedSubscriptionRow>, val adopted: String?)

    private val mutable = MutableStateFlow<List<String>?>(null)

    /** The queries followed, in name order; null until the file has been read. */
    val state: StateFlow<List<String>?> = mutable.asStateFlow()

    private val mutex = Mutex()
    private var kept: Kept? = null

    suspend fun load(): List<String> = mutex.withLock { loaded().rows.map { it.name } }

    /** [query] followed, as its normal form; a query already followed is followed again from now, as XTA's insert replaces its row. */
    suspend fun add(query: String) {
        val id = BooruQuery.normalise(query) ?: return
        change { rows -> upsert(rows, listOf(NamedSubscriptionRow(id, id, inFeed = true, createdAt = XtaTime.sqlite(clock())))) }
    }

    suspend fun remove(query: String) {
        val id = BooruQuery.normalise(query) ?: return
        change { rows -> rows.filterNot { it.id == id } }
    }

    /** XTA's rows for these, as a restore left them with the app: taken as XTA's import takes them, unless this set was taken already. */
    suspend fun adopt(rows: List<NamedSubscriptionRow>) = mutex.withLock {
        val now = loaded()
        val print = fingerprint(rows)
        if (rows.isEmpty() || now.adopted == print) return@withLock
        save(Kept(upsert(now.rows, rows.mapNotNull(::normalised)), print))
    }

    /** What the backup keeps: the rows and which set of XTA's was taken. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    /** What a backup kept, in place of what is here. */
    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    private suspend fun change(edit: (List<NamedSubscriptionRow>) -> List<NamedSubscriptionRow>) = mutex.withLock {
        val now = loaded()
        save(Kept(edit(now.rows), now.adopted))
    }

    private suspend fun save(next: Kept) {
        val sorted = Kept(sorted(next.rows), next.adopted)
        file.write(encode(sorted))
        kept = sorted
        mutable.value = sorted.rows.map { it.name }
    }

    private suspend fun loaded(): Kept = kept ?: decode(file.read().orEmpty()).let { Kept(sorted(it.rows), it.adopted) }.also {
        kept = it
        mutable.value = it.rows.map { row -> row.name }
    }

    private companion object {
        /** [incoming]'s rows over [rows]', a row with a known id replacing it, as SQLite's INSERT OR REPLACE does. */
        fun upsert(rows: List<NamedSubscriptionRow>, incoming: List<NamedSubscriptionRow>): List<NamedSubscriptionRow> {
            val fresh = incoming.associateBy { it.id }
            return rows.filterNot { it.id in fresh } + fresh.values
        }

        /** As XTA lists them: by name, whatever its case (`COLLATE NOCASE`). */
        fun sorted(rows: List<NamedSubscriptionRow>): List<NamedSubscriptionRow> =
            rows.distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        fun normalised(row: NamedSubscriptionRow): NamedSubscriptionRow? =
            BooruQuery.normalise(row.name)?.let { query -> row.copy(id = query, name = query) }

        fun fingerprint(rows: List<NamedSubscriptionRow>): String {
            val text = JsonArray(rows.map { it.toJson() }).toString()
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        fun encode(kept: Kept): String = buildJsonObject {
            put("v", 1)
            put("rows", JsonArray(kept.rows.map { it.toJson() }))
            kept.adopted?.let { put("adopted", it) }
        }.toString()

        /** The file, or a bare list of XTA's rows as its backup writes them. */
        fun decode(text: String): Kept {
            val root = J.parse(text)
            val rows = if (root.raw is JsonArray) root.list else root["rows"].list
            return Kept(rows.mapNotNull { NamedSubscriptionRow.from(it) }.mapNotNull(::normalised), root["adopted"].string)
        }
    }
}
