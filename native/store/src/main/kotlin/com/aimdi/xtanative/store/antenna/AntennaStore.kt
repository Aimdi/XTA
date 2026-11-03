package com.aimdi.xtanative.store.antenna

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The reader's antennas, in one file of the store, newest first; a file that cannot be read is kept as `antennas.json.unreadable`. */
class AntennaStore(
    private val dir: StoreDir,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val file = dir.file(FILE)
    private val mutex = Mutex()
    private val current = MutableStateFlow<List<Antenna>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<Antenna>?> = current.asStateFlow()

    /** The antennas, read on first collection, then each change. */
    val antennas: Flow<List<Antenna>> = flow {
        load()
        emitAll(current.filterNotNull())
    }

    suspend fun load(): List<Antenna> = mutex.withLock { loaded() }

    /**
     * A new antenna, or with [id] the one changed, keeping when it was made.
     * Null when there is no name or no term to listen for, which the editor
     * never offers to save.
     */
    suspend fun save(
        name: String,
        includeTerms: List<String>,
        excludeTerms: List<String> = emptyList(),
        scope: AntennaScope = AntennaScope.SEARCH,
        id: String? = null,
    ): Antenna? {
        val include = includeTerms.map(String::trim).filter(String::isNotEmpty)
        if (!Antenna.canSave(name, include)) return null
        return mutex.withLock {
            val all = loaded()
            val createdAt = all.firstOrNull { it.id == id }?.createdAt ?: clock()
            val antenna = Antenna(id ?: newId(), name.trim(), include, excludeTerms.map(String::trim).filter(String::isNotEmpty), scope, createdAt)
            save(all.filterNot { it.id == antenna.id } + antenna)
            antenna
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        val all = loaded()
        if (all.any { it.id == id }) save(all.filterNot { it.id == id })
    }

    /** Antennas from a backup: each replaces the one with its id, as XTA's import does, and the others stay. */
    suspend fun merge(antennas: List<Antenna>) = mutex.withLock {
        val incoming = antennas.associateBy { it.id }
        save(loaded().filterNot { it.id in incoming } + incoming.values)
    }

    private suspend fun loaded(): List<Antenna> {
        current.value?.let { return it }
        val text = file.read()
        val stored = text?.let(::decode)
        if (text != null && stored == null) dir.file("$FILE.unreadable").write(text)
        return (stored ?: emptyList()).also { current.value = it }
    }

    private suspend fun save(antennas: List<Antenna>) {
        val ordered = antennas.sortedByDescending { it.createdAt }
        file.write(encode(ordered))
        current.value = ordered
    }

    internal companion object {
        const val FILE = "antennas.json"
        private const val VERSION = 1

        fun encode(antennas: List<Antenna>): String = buildJsonObject {
            put("v", VERSION)
            put(
                "antennas",
                JsonArray(
                    antennas.map { antenna ->
                        buildJsonObject {
                            put("id", antenna.id)
                            put("name", antenna.name)
                            put("include", JsonArray(antenna.includeTerms.map(::JsonPrimitive)))
                            put("exclude", JsonArray(antenna.excludeTerms.map(::JsonPrimitive)))
                            put("scope", antenna.scope.stored)
                            put("createdAt", antenna.createdAt)
                        }
                    },
                ),
            )
        }.toString()

        fun decode(text: String): List<Antenna>? {
            val root = J.parse(text)
            if (root["v"].int != VERSION) return null
            return root["antennas"].list.mapNotNull { row ->
                Antenna(
                    id = row["id"].string ?: return@mapNotNull null,
                    name = row["name"].string ?: "",
                    includeTerms = row["include"].list.mapNotNull { it.string },
                    excludeTerms = row["exclude"].list.mapNotNull { it.string },
                    scope = AntennaScope.of(row["scope"].string),
                    createdAt = row["createdAt"].long ?: 0,
                )
            }.sortedByDescending { it.createdAt }
        }
    }
}
