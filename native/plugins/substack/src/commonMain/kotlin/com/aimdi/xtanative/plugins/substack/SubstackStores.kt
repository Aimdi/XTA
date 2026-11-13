package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.substack.SubstackFiles
import com.aimdi.xtanative.networks.substack.SubstackFollow
import com.aimdi.xtanative.networks.substack.SubstackFollowing
import com.aimdi.xtanative.networks.substack.SubstackKept
import com.aimdi.xtanative.networks.substack.SubstackPostJson
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.SubstackSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

/** A text kept in one of the app's settings, as XTA keeps it in a preference. */
internal class PrefKept(private val settings: PluginSettings, private val pref: Pref<String>) : SubstackKept {
    override suspend fun read(): String? = settings.get(pref)

    override suspend fun write(text: String) = settings.set(pref, text)
}

/** A text kept in a file of the plugin's own folder. */
internal class FileKept(private val file: StoreFile) : SubstackKept {
    override suspend fun read(): String? = file.read()

    override suspend fun write(text: String) = file.write(text)
}

/** The plugin's own folder, by file name. */
internal class PluginFiles(private val store: StoreDir) : SubstackFiles {
    override suspend fun read(name: String): String? = store.file(name).read()

    override suspend fun write(name: String, text: String) = store.file(name).write(text)

    override suspend fun delete(name: String) {
        store.file(name).delete()
    }
}

/**
 * The publications followed on this device, in the plugin's own folder as
 * the rows XTA's `substack_subscription` table and backup hold
 * (`substackSubscriptions`). What XTA kept in `plugin.substack.publications`
 * before follows had a table is taken over once and that preference
 * cleared, as XTA's store does. Nothing is told to Substack.
 */
internal class SubstackFollowStore(private val file: StoreFile, private val settings: PluginSettings) {
    private val mutable = MutableStateFlow<List<SubstackFollow>?>(null)
    private val mutex = Mutex()

    /** Null until the file has been read. */
    val state: StateFlow<List<SubstackFollow>?> = mutable.asStateFlow()

    suspend fun load(): List<SubstackFollow> = mutex.withLock { loaded() }

    suspend fun follow(publication: SubstackPublication) = change { SubstackFollowing.followed(it, publication, now()) }

    suspend fun unfollow(id: String) = change { SubstackFollowing.unfollowed(it, id) }

    /** A fresher copy of a followed publication written over its follow; the id it left, when placing it moved it. */
    suspend fun refresh(fresh: SubstackPublication): String? = mutex.withLock {
        val (next, left) = SubstackFollowing.refreshed(loaded(), fresh, now())
        save(next)
        left
    }

    /** The follows as XTA's backup holds them: its rows. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    /** A backup's rows in place of these. */
    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    private suspend fun change(edit: (List<SubstackFollow>) -> List<SubstackFollow>) = mutex.withLock { save(edit(loaded())) }

    private suspend fun save(follows: List<SubstackFollow>) {
        file.write(encode(follows))
        mutable.value = follows
    }

    private suspend fun loaded(): List<SubstackFollow> {
        mutable.value?.let { return it }
        val stored = file.read()?.let(::decode)
        val follows = stored ?: legacy().also { if (it.isNotEmpty()) file.write(encode(it)) }
        mutable.value = follows
        return follows
    }

    /** The follows XTA kept in a preference before they had a table, taken over and the preference cleared. */
    private suspend fun legacy(): List<SubstackFollow> {
        val publications = SubstackPostJson.publications(settings.get(SubstackPrefs.publications))
        if (publications.isEmpty()) return emptyList()
        settings.set(SubstackPrefs.publications, "")
        return publications.fold(emptyList()) { follows, publication -> SubstackFollowing.followed(follows, publication, now()) }
    }

    private companion object {
        fun now(): String = XtaTime.sqlite(Instant.now())

        fun encode(follows: List<SubstackFollow>): String = JsonArray(
            follows.map { follow ->
                val publication = follow.publication
                SubstackSubscriptionRow(publication.id, publication.baseUrl, publication.name, publication.logoUrl, createdAt = follow.createdAt).toJson()
            },
        ).toString()

        fun decode(text: String): List<SubstackFollow> = J.parse(text).list.mapNotNull(SubstackSubscriptionRow::from).map { row ->
            SubstackFollow(SubstackPublication(row.id, row.baseUrl, row.name, logoUrl = row.logoUrl), row.createdAt)
        }.distinctBy { it.publication.id }
    }
}
