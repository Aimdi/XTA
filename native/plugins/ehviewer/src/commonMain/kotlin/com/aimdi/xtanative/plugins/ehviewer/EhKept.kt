package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.ehviewer.EhCategory
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.EhFavoriteRow
import com.aimdi.xtanative.store.backup.EhGalleryRow
import com.aimdi.xtanative.store.backup.EhHistoryRow
import com.aimdi.xtanative.store.backup.XtaTime
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** A gallery as XTA's `eh_favorite` and `eh_history` rows keep it (eh_store.dart's EhFavorite), and back: the rows keep no tags. */
internal object EhRows {
    fun of(gallery: EhGallery): EhGalleryRow = EhGalleryRow(
        gid = gallery.gid,
        token = gallery.token,
        title = gallery.title,
        titleJpn = gallery.titleJpn,
        category = gallery.category?.label,
        thumbUrl = gallery.thumbUrl,
        uploader = gallery.uploader,
        pageCount = gallery.pageCount,
        rating = gallery.rating,
        postedAt = gallery.postedAt?.let(XtaTime::iso),
    )

    fun gallery(row: EhGalleryRow): EhGallery = EhGallery(
        gid = row.gid,
        token = row.token,
        title = row.title,
        titleJpn = row.titleJpn,
        category = EhCategory.tryParse(row.category),
        thumbUrl = row.thumbUrl,
        uploader = row.uploader,
        postedAt = XtaTime.parse(row.postedAt),
        pageCount = row.pageCount,
        rating = row.rating,
    )
}

/**
 * Rows kept whole in one file of the plugin's folder, as XTA's backup writes
 * them: one per gallery ([gid]), newest first by [time], as XTA lists them
 * (`ORDER BY … DESC`).
 */
internal class EhRowsFile<T>(
    private val file: StoreFile,
    private val decode: (J) -> T?,
    private val encode: (T) -> JsonObject,
    private val gid: (T) -> Long,
    private val time: (T) -> String?,
) {
    private val mutable = MutableStateFlow<List<T>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<T>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<T> = mutex.withLock { loaded() }

    /** [row] in place of the gallery's row, as SQLite's INSERT OR REPLACE. */
    suspend fun put(row: T) = change { rows -> listOf(row) + rows.filterNot { gid(it) == gid(row) } }

    suspend fun remove(id: Long) = change { rows -> rows.filterNot { gid(it) == id } }

    suspend fun clear() = change { emptyList() }

    /** The rows as XTA's backup writes them. */
    suspend fun export(): String = mutex.withLock { encoded(loaded()) }

    /** A backup's rows in place of these. */
    suspend fun import(text: String) = mutex.withLock { save(decoded(text)) }

    private suspend fun change(edit: (List<T>) -> List<T>) = mutex.withLock { save(edit(loaded())) }

    private suspend fun loaded(): List<T> = mutable.value ?: decoded(file.read().orEmpty()).also { mutable.value = it }

    private suspend fun save(rows: List<T>) {
        val next = ordered(rows)
        file.write(encoded(next))
        mutable.value = next
    }

    private fun ordered(rows: List<T>): List<T> =
        rows.distinctBy(gid).sortedByDescending { XtaTime.parse(time(it)) ?: Instant.EPOCH }

    private fun encoded(rows: List<T>): String = JsonArray(rows.map(encode)).toString()

    private fun decoded(text: String): List<T> = ordered(J.parse(text).list.mapNotNull(decode))
}

/** The galleries favourited on this device (XTA's EhFavoritesStore): the site is never told. */
internal class EhFavorites(file: StoreFile, private val clock: () -> Instant = Instant::now) {
    private val rows = EhRowsFile<EhFavoriteRow>(file, { EhFavoriteRow.from(it) }, EhFavoriteRow::toJson, { it.gallery.gid }, { it.favoritedAt })

    val state: StateFlow<List<EhFavoriteRow>?> get() = rows.state

    suspend fun load(): List<EhFavoriteRow> = rows.load()

    fun contains(gid: Long): Boolean = rows.state.value.orEmpty().any { it.gallery.gid == gid }

    /** Favourited now, or no longer. */
    suspend fun toggle(gallery: EhGallery) {
        load()
        if (contains(gallery.gid)) rows.remove(gallery.gid) else rows.put(EhFavoriteRow(EhRows.of(gallery), XtaTime.iso(clock())))
    }

    suspend fun export(): String = rows.export()

    suspend fun import(text: String) = rows.import(text)
}

/** The galleries read on this device and the page each was left on (XTA's EhHistoryStore). */
internal class EhHistory(file: StoreFile, private val clock: () -> Instant = Instant::now) {
    private val rows = EhRowsFile<EhHistoryRow>(file, { EhHistoryRow.from(it) }, EhHistoryRow::toJson, { it.gallery.gid }, { it.viewedAt })

    val state: StateFlow<List<EhHistoryRow>?> get() = rows.state

    suspend fun load(): List<EhHistoryRow> = rows.load()

    /** The page [gid] was left on; null when it was never read here. */
    fun lastPage(gid: Long): Int? = rows.state.value.orEmpty().firstOrNull { it.gallery.gid == gid }?.lastPage

    /** [gallery] read up to [page] now: it goes first, never before page 1. */
    suspend fun remember(gallery: EhGallery, page: Int) =
        rows.put(EhHistoryRow(EhRows.of(gallery), lastPage = page.coerceAtLeast(1), viewedAt = XtaTime.iso(clock())))

    suspend fun clear() = rows.clear()

    suspend fun export(): String = rows.export()

    suspend fun import(text: String) = rows.import(text)
}
