package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.backup.FeedFilterRow
import com.aimdi.xtanative.store.backup.ProfileNoteRow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The reader's private note on a profile, as XTA's profile_note table keeps it. */
data class ProfileNote(val profileId: String, val note: String, val updatedAt: Long)

/**
 * Private notes on profiles, by account id, in `saved/profile-notes.json`
 * (XTA's profile_note.dart). A note never leaves the device but in the
 * reader's own backup.
 */
class ProfileNotesStore(dir: StoreDir, private val clock: () -> Long = System::currentTimeMillis) {
    private val stored = StoredJson(dir, "saved/profile-notes.json", emptyMap(), ::decode, ::encode)

    /** The notes once read, by profile id; null before the first call reads them. */
    val state: StateFlow<Map<String, ProfileNote>?> = stored.state

    suspend fun of(profileId: String): ProfileNote? = stored.get()[profileId]

    /** [note] on [profileId], trimmed, as XTA saves it; a blank note removes it. */
    suspend fun set(profileId: String, note: String) {
        val kept = note.trim()
        stored.update { notes ->
            when {
                kept.isEmpty() -> if (profileId in notes) notes - profileId else notes
                notes[profileId]?.note == kept -> notes
                else -> notes + (profileId to ProfileNote(profileId, kept, clock()))
            }
        }
    }

    /** Every note, for the backup, as XTA's rows. */
    suspend fun content(): List<ProfileNoteRow> = stored.get().values.map { ProfileNoteRow(it.profileId, it.note, isoTime(it.updatedAt)) }

    /** Every note replaced with [rows], as a restored backup does; a blank one is dropped. */
    suspend fun replace(rows: List<ProfileNoteRow>) {
        val notes = rows.filter { it.note.isNotBlank() }
            .associate { it.profileId to ProfileNote(it.profileId, it.note.trim(), parseTime(it.updatedAt) ?: clock()) }
        stored.update { notes }
    }

    private companion object {
        const val VERSION = 1

        fun encode(notes: Map<String, ProfileNote>): JsonElement = buildJsonObject {
            put("v", VERSION)
            put(
                "notes",
                JsonObject(notes.mapValues { (_, note) -> buildJsonObject { put("note", note.note); put("updatedAt", note.updatedAt) } }),
            )
        }

        fun decode(root: J): Map<String, ProfileNote>? {
            if (root["v"].int != VERSION) return null
            val notes = root["notes"]
            return notes.keys.mapNotNull { id ->
                val note = notes[id]["note"].string?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to ProfileNote(id, note, notes[id]["updatedAt"].long ?: 0)
            }.toMap()
        }
    }
}

/** What the reader hides of one account in the feeds (XTA's profile feed settings), by account id. */
data class ProfileFilters(
    /** Lower-case handles by account id: the account's reposts are not shown. */
    val hiddenReposts: Map<String, String> = emptyMap(),
    /** Lower-case handles by account id: the account's replies to others are not shown. */
    val hiddenReplies: Map<String, String> = emptyMap(),
) {
    fun repostsHidden(userId: String): Boolean = userId in hiddenReposts

    fun repliesHidden(userId: String): Boolean = userId in hiddenReplies

    /** The handles a feed's shaping reads ([com.aimdi.xtanative.core.feed.FeedShaping.hiddenReposts]). */
    val hiddenRepostHandles: Set<String> get() = hiddenReposts.values.toSet()

    val hiddenReplyHandles: Set<String> get() = hiddenReplies.values.toSet()
}

/**
 * The accounts whose reposts or replies to others the reader hid, in
 * `saved/profile-filters.json`: XTA's retweet_filter and reply_filter
 * tables. Feeds match them by handle, as XTA does.
 */
class ProfileFiltersStore(dir: StoreDir) {
    private val stored = StoredJson(dir, "saved/profile-filters.json", ProfileFilters(), ::decode, ::encode)

    /** The filters once read; null before the first call reads them. */
    val state: StateFlow<ProfileFilters?> = stored.state

    suspend fun filters(): ProfileFilters = stored.get()

    suspend fun setRepostsHidden(userId: String, screenName: String, hidden: Boolean) {
        stored.update { it.copy(hiddenReposts = toggled(it.hiddenReposts, userId, screenName, hidden)).orSame(it) }
    }

    suspend fun setRepliesHidden(userId: String, screenName: String, hidden: Boolean) {
        stored.update { it.copy(hiddenReplies = toggled(it.hiddenReplies, userId, screenName, hidden)).orSame(it) }
    }

    /** Both filters for the backup, as XTA's rows. */
    suspend fun content(): Pair<List<FeedFilterRow>, List<FeedFilterRow>> =
        stored.get().let { filters -> rows(filters.hiddenReposts) to rows(filters.hiddenReplies) }

    /** Both filters replaced with a backup's rows. */
    suspend fun replace(reposts: List<FeedFilterRow>, replies: List<FeedFilterRow>) {
        stored.update { ProfileFilters(byId(reposts), byId(replies)) }
    }

    private companion object {
        const val VERSION = 1

        fun toggled(hidden: Map<String, String>, userId: String, screenName: String, hide: Boolean): Map<String, String> = when {
            hide && hidden[userId] == screenName.lowercase() -> hidden
            hide -> hidden + (userId to screenName.lowercase())
            userId in hidden -> hidden - userId
            else -> hidden
        }

        /** The same instance when nothing changed, so nothing is written. */
        fun ProfileFilters.orSame(old: ProfileFilters): ProfileFilters = if (this == old) old else this

        fun rows(hidden: Map<String, String>): List<FeedFilterRow> = hidden.map { (id, name) -> FeedFilterRow(id, name) }

        fun byId(rows: List<FeedFilterRow>): Map<String, String> =
            rows.filter { it.userId.isNotBlank() }.associate { it.userId to it.screenName.lowercase() }

        fun encode(filters: ProfileFilters): JsonElement = buildJsonObject {
            put("v", VERSION)
            put("reposts", JsonObject(filters.hiddenReposts.mapValues { JsonPrimitive(it.value) }))
            put("replies", JsonObject(filters.hiddenReplies.mapValues { JsonPrimitive(it.value) }))
        }

        fun decode(root: J): ProfileFilters? {
            if (root["v"].int != VERSION) return null
            fun handles(section: J) = section.keys.mapNotNull { id -> section[id].string?.let { id to it.lowercase() } }.toMap()
            return ProfileFilters(handles(root["reposts"]), handles(root["replies"]))
        }
    }
}
