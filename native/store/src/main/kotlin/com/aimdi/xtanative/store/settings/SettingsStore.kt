package com.aimdi.xtanative.store.settings

import com.aimdi.xtanative.store.StoreDir
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * The settings on disk, in one file of the store. The file is read on first
 * use and XTA's launch steps ([SettingsMigrations]) run then, as they do at
 * every XTA launch; each change is written whole and reaches every observer.
 * A file that cannot be read is kept as `settings.json.unreadable`.
 */
class SettingsStore(private val dir: StoreDir) {
    private val file = dir.file(FILE)
    private val mutex = Mutex()
    private val current = MutableStateFlow<Settings?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<Settings?> = current.asStateFlow()

    /** The settings, read on first collection, then each change. */
    val settings: Flow<Settings> = flow {
        load()
        emitAll(current.filterNotNull())
    }

    /** [pref]'s value, then each time it changes. */
    fun <T> observe(pref: Pref<T>): Flow<T> = settings.map { it[pref] }.distinctUntilChanged()

    suspend fun load(): Settings = mutex.withLock { loaded() }

    suspend fun <T> get(pref: Pref<T>): T = load()[pref]

    suspend fun <T> set(pref: Pref<T>, value: T) {
        update { it.with(pref, value) }
    }

    /** [pref] back to its default. */
    suspend fun reset(pref: Pref<*>) {
        update { it.without(pref) }
    }

    /** The settings changed by [transform], written and announced only when something did change. */
    suspend fun update(transform: (Settings) -> Settings): Settings = mutex.withLock {
        val before = loaded()
        transform(before).also { after -> if (after != before) save(after) }
    }

    /** A backup's settings section applied as XTA applies it, then XTA's launch steps, as its next launch would run them. */
    suspend fun restore(preferences: Map<String, JsonElement>): Settings =
        update { SettingsMigrations.apply(FeedStripMigration.of(it.restoring(preferences))) }

    /** The settings section for a backup; see [Settings.exported]. */
    suspend fun exported(includeSettings: Boolean = true, includeSubscriptions: Boolean = true): Map<String, JsonElement>? =
        load().exported(includeSettings, includeSubscriptions)

    private suspend fun loaded(): Settings {
        current.value?.let { return it }
        val text = file.read()
        val stored = text?.let(::decode)
        if (text != null && stored == null) dir.file("$FILE.unreadable").write(text)
        val migrated = SettingsMigrations.apply(stored ?: Settings(), firstLaunch = text == null)
        if (migrated != stored) file.write(encode(migrated))
        current.value = migrated
        return migrated
    }

    private suspend fun save(settings: Settings) {
        file.write(encode(settings))
        current.value = settings
    }

    internal companion object {
        const val FILE = "settings.json"
        private const val VERSION = 1

        fun encode(settings: Settings): String = buildJsonObject {
            put("v", VERSION)
            put("preferences", JsonObject(settings.values.mapValues { it.value.toJson() }))
        }.toString()

        /** The settings in [text]; null when it is not this file. */
        fun decode(text: String): Settings? {
            val root = strictJson(text) as? JsonObject ?: return null
            if ((root["v"] as? JsonPrimitive)?.intOrNull != VERSION) return null
            val preferences = root["preferences"] as? JsonObject ?: return null
            return Settings(preferences.mapNotNull { (key, value) -> PrefValue.fromJson(value)?.let { key to it } }.toMap())
        }
    }
}
