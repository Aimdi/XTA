package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.StoreFile
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A work or a novel as the device keeps it, in its history or its bookmarks:
 * enough to draw its tile or card and to open it again; opening fetches the
 * rest. Its tags stay so a muted tag still hides it, and its age rating and AI
 * mark so its badges do.
 */
internal data class PixivKept(
    val id: Long,
    val title: String,
    val userId: Long,
    val userName: String,
    val thumbUrl: String,
    val tags: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val bookmarks: Long = 0,
    val bookmarked: Boolean = false,
    /** A novel's length in characters. */
    val textLength: Long = 0,
    /** 1 for any R-18 work; a novel keeps 2 for R-18G. */
    val xRestrict: Int = 0,
    val isAi: Boolean = false,
    val pageCount: Int = 1,
    val type: String = "illust",
) {
    /** Whether the title or the creator's name holds [query], whatever its case. */
    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        return needle.isEmpty() || needle in title.lowercase() || needle in userName.lowercase()
    }

    fun toIllust(): PixivIllust = PixivIllust(
        id = id,
        title = title,
        caption = "",
        type = type,
        thumbnailUrl = thumbUrl,
        pageCount = pageCount,
        userId = userId,
        userName = userName,
        userAccount = "",
        tags = tags.map { PixivTag(it) },
        width = width,
        height = height,
        totalBookmarks = bookmarks,
        isBookmarked = bookmarked,
        isR18 = xRestrict > 0,
        isAi = isAi,
    )

    fun toNovel(): PixivNovel = PixivNovel(
        id = id,
        title = title,
        user = PixivUser(userId, userName, ""),
        coverUrl = thumbUrl.ifEmpty { null },
        tags = tags.map { PixivTag(it) },
        textLength = textLength,
        totalBookmarks = bookmarks,
        isBookmarked = bookmarked,
        xRestrict = xRestrict,
        isAi = isAi,
    )

    /** The fields as XTA's history writes them, into [json]. */
    fun writeTo(json: JsonObjectBuilder) {
        json.put("id", id)
        json.put("title", title)
        json.put("userId", userId)
        json.put("userName", userName)
        json.put("thumbUrl", thumbUrl)
        json.put("tags", JsonArray(tags.map(::JsonPrimitive)))
        json.put("width", width)
        json.put("height", height)
        json.put("bookmarks", bookmarks)
        json.put("bookmarked", bookmarked)
        if (textLength > 0) json.put("textLength", textLength)
        if (xRestrict > 0) json.put("xRestrict", xRestrict)
        if (isAi) json.put("isAi", true)
        if (pageCount > 1) json.put("pageCount", pageCount)
        if (type != "illust") json.put("type", type)
    }

    companion object {
        fun of(illust: PixivIllust) = PixivKept(
            id = illust.id,
            title = illust.title,
            userId = illust.userId,
            userName = illust.userName,
            thumbUrl = illust.thumbnailUrl,
            tags = illust.tags.map { it.name },
            width = illust.width,
            height = illust.height,
            bookmarks = illust.totalBookmarks,
            bookmarked = illust.isBookmarked,
            xRestrict = if (illust.isR18) 1 else 0,
            isAi = illust.isAi,
            pageCount = illust.pageCount,
            type = illust.type,
        )

        fun of(novel: PixivNovel) = PixivKept(
            id = novel.id,
            title = novel.title,
            userId = novel.user.id,
            userName = novel.user.name,
            thumbUrl = novel.coverUrl.orEmpty(),
            tags = novel.tags.map { it.name },
            bookmarks = novel.totalBookmarks,
            bookmarked = novel.isBookmarked,
            textLength = novel.textLength,
            xRestrict = novel.xRestrict,
            isAi = novel.isAi,
        )

        /** One kept entry, or null without an id or, where [needsThumb] says a work cannot show without one, its picture. */
        fun parse(json: J, needsThumb: Boolean = true): PixivKept? {
            val id = json["id"].long?.takeIf { it > 0 } ?: return null
            val thumb = json["thumbUrl"].string.orEmpty()
            if (needsThumb && thumb.isEmpty()) return null
            return PixivKept(
                id = id,
                title = json["title"].string.orEmpty(),
                userId = json["userId"].long ?: 0,
                userName = json["userName"].string.orEmpty(),
                thumbUrl = thumb,
                tags = json["tags"].list.mapNotNull { it.string },
                width = json["width"].int ?: 0,
                height = json["height"].int ?: 0,
                bookmarks = json["bookmarks"].long ?: 0,
                bookmarked = json["bookmarked"].boolean == true,
                textLength = json["textLength"].long ?: 0,
                xRestrict = json["xRestrict"].int ?: 0,
                isAi = json["isAi"].boolean == true,
                pageCount = json["pageCount"].int ?: 1,
                type = json["type"].string ?: "illust",
            )
        }
    }
}

/** Works or novels: the two kinds every list of the plugin keeps apart. */
internal enum class PixivKind(val stored: String) {
    WORK("work"),
    NOVEL("novel"),
    ;

    companion object {
        fun of(stored: String?): PixivKind = entries.firstOrNull { it.stored == stored } ?: WORK
    }
}

/** One opened work or novel, and when. */
internal data class PixivVisit(val kept: PixivKept, val viewedAt: Instant)

/**
 * The works or the novels opened on this device, newest first, at most
 * [LIMIT], each once. Viewing history, so it lives in a file of the plugin's
 * own and no backup carries it, as in XTA.
 */
internal class PixivHistory(private val file: StoreFile, private val kind: PixivKind) {
    private val mutable = MutableStateFlow<List<PixivVisit>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<PixivVisit>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<PixivVisit> = mutex.withLock { loaded() }

    suspend fun record(visit: PixivVisit) = change { visits -> listOf(visit) + visits.filterNot { it.kept.id == visit.kept.id } }

    suspend fun remove(id: Long) = change { visits -> visits.filterNot { it.kept.id == id } }

    suspend fun clear() = change { emptyList() }

    private suspend fun change(edit: (List<PixivVisit>) -> List<PixivVisit>) = mutex.withLock {
        val next = edit(loaded()).take(LIMIT)
        mutable.value = next
        file.write(encode(next))
    }

    private suspend fun loaded(): List<PixivVisit> = mutable.value ?: decode(file.read().orEmpty()).also { mutable.value = it }

    private fun decode(text: String): List<PixivVisit> = J.parse(text).list.mapNotNull { row ->
        PixivKept.parse(row, needsThumb = kind == PixivKind.WORK)?.let { kept ->
            PixivVisit(kept, row["viewedAt"].string?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH)
        }
    }.distinctBy { it.kept.id }.take(LIMIT)

    private fun encode(visits: List<PixivVisit>): String = JsonArray(
        visits.map { visit -> buildJsonObject { visit.kept.writeTo(this); put("viewedAt", visit.viewedAt.toString()) } },
    ).toString()

    companion object {
        const val LIMIT = 500

        /** [visits] whose title or creator holds [query]. */
        fun filtered(visits: List<PixivVisit>, query: String): List<PixivVisit> =
            if (query.isBlank()) visits else visits.filter { it.kept.matches(query) }
    }
}

/** A work or novel bookmarked on this device, under the reader's own [tags]. Nothing is bookmarked on Pixiv. */
internal data class PixivDeviceBookmark(val kind: PixivKind, val kept: PixivKept, val tags: List<String>, val at: Instant) {
    val key: String get() = key(kind, kept.id)

    companion object {
        fun key(kind: PixivKind, id: Long): String = kind.stored + ":" + id
    }
}

/**
 * What the reader bookmarked on this device, newest first: the heart on a
 * work or a novel files it here, never on Pixiv. The backup keeps them.
 */
internal class PixivDeviceBookmarks(private val file: StoreFile) {
    private val mutable = MutableStateFlow<List<PixivDeviceBookmark>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<PixivDeviceBookmark>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<PixivDeviceBookmark> = mutex.withLock { loaded() }

    fun find(kind: PixivKind, id: Long): PixivDeviceBookmark? = mutable.value?.firstOrNull { it.kind == kind && it.kept.id == id }

    /** Bookmarks [kept] under [tags], or files the bookmark it has again: it keeps its place and its time. */
    suspend fun put(kind: PixivKind, kept: PixivKept, tags: List<String>, at: Instant) = change { marks ->
        val old = marks.firstOrNull { it.kind == kind && it.kept.id == kept.id }
        val mark = PixivDeviceBookmark(kind, kept, tags.distinct(), old?.at ?: at)
        if (old == null) listOf(mark) + marks else marks.map { if (it === old) mark else it }
    }

    suspend fun remove(kind: PixivKind, id: Long) = change { marks -> marks.filterNot { it.kind == kind && it.kept.id == id } }

    /** The bookmarks as the backup keeps them. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    /** The bookmarks a backup kept, in place of these. */
    suspend fun import(text: String) = mutex.withLock {
        val marks = decode(text)
        file.write(encode(marks))
        mutable.value = marks
    }

    private suspend fun change(edit: (List<PixivDeviceBookmark>) -> List<PixivDeviceBookmark>) = mutex.withLock {
        val next = edit(loaded())
        mutable.value = next
        file.write(encode(next))
    }

    private suspend fun loaded(): List<PixivDeviceBookmark> = mutable.value ?: decode(file.read().orEmpty()).also { mutable.value = it }

    companion object {
        /** Each tag the [kind]'s bookmarks carry with how many carry it, most used first. */
        fun tagCounts(marks: List<PixivDeviceBookmark>, kind: PixivKind): List<Pair<String, Int>> =
            marks.filter { it.kind == kind }.flatMap { it.tags }.groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }

        /** The [kind]'s bookmarks under [tag]: every one for null, the untagged ones for [UNTAGGED]. */
        fun under(marks: List<PixivDeviceBookmark>, kind: PixivKind, tag: String?): List<PixivDeviceBookmark> = marks.filter { mark ->
            mark.kind == kind && when (tag) {
                null -> true
                UNTAGGED -> mark.tags.isEmpty()
                else -> tag in mark.tags
            }
        }

        /** Pixiv's own name for untagged bookmarks, which the tag filter offers here too. */
        const val UNTAGGED = "未分類"

        fun encode(marks: List<PixivDeviceBookmark>): String = buildJsonObject {
            put("v", 1)
            put(
                "bookmarks",
                JsonArray(
                    marks.map { mark ->
                        buildJsonObject {
                            put("kind", mark.kind.stored)
                            mark.kept.writeTo(this)
                            put("bookmarkTags", JsonArray(mark.tags.map(::JsonPrimitive)))
                            put("at", mark.at.toString())
                        }
                    },
                ),
            )
        }.toString()

        fun decode(text: String): List<PixivDeviceBookmark> = J.parse(text)["bookmarks"].list.mapNotNull { row ->
            val kind = PixivKind.of(row["kind"].string)
            PixivKept.parse(row, needsThumb = kind == PixivKind.WORK)?.let { kept ->
                PixivDeviceBookmark(
                    kind = kind,
                    kept = kept,
                    tags = row["bookmarkTags"].list.mapNotNull { it.string?.trim()?.ifEmpty { null } },
                    at = row["at"].string?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
                )
            }
        }.distinctBy { it.key }
    }
}

/** A JSON object's fields kept as they came, so a row this build does not fully read goes back out whole. */
