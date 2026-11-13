package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A plugin on this device: what its xta-plugin.json said, the file it is kept
 * in and how the store named it when it was installed, so it keeps its name
 * when the catalogue no longer lists it or cannot be read.
 */
data class InstalledPlugin(
    val id: String,
    val version: String,
    val apiVersion: Int,
    /** The class implementing XtaPlugin. */
    val entryClass: String,
    /** The file's name in its folder, `<id>/<version>/`. */
    val fileName: String,
    /** What the file has to be still when it is loaded. */
    val sha256: String,
    val size: Long,
    /** Epoch milliseconds. */
    val installedAt: Long,
    val category: PluginCategory,
    val name: LocalizedText,
    val description: LocalizedText,
)

/**
 * Where plugins are kept: `<root>/<id>/<version>/<file>`, under a directory
 * the app gives (on Android, in its private files), with downloads under way
 * in `<root>/.downloads/<id>/`, beside their final place, so putting one there
 * is a rename. A folder per version also holds what the platform derives from
 * the file (Android's optimized code), and goes with it.
 */
class PluginFiles(val root: File) {
    fun fileOf(plugin: InstalledPlugin): File = File(root, "${plugin.id}/${plugin.version}/${plugin.fileName}")

    /** Where [id]'s [version] downloads to; an unfinished download of another version of it is deleted. */
    fun downloadOf(id: String, version: String, platform: PluginPlatform): File {
        val folder = File(root, "$DOWNLOADS/$id")
        val part = File(folder, "$version.${platform.extension}.part")
        folder.listFiles()?.filter { it != part }?.forEach(::erase)
        return part
    }

    /**
     * Moves the checked [download] to its place as [id]'s [version] and makes it
     * read-only, as Android 14 requires of code it loads; whatever an earlier,
     * interrupted install left in that folder goes first.
     */
    fun keep(download: File, id: String, version: String, fileName: String): File {
        val folder = File(root, "$id/$version")
        erase(folder)
        folder.mkdirs()
        val file = File(folder, fileName)
        Files.move(download.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        file.setReadOnly()
        return file
    }

    /** Deletes every version of [id] but [version]: what an update replaced. */
    fun keepOnly(id: String, version: String) {
        File(root, id).listFiles()?.filter { it.name != version }?.forEach(::erase)
    }

    /** Deletes everything kept for [id], an unfinished download included. */
    fun remove(id: String) {
        erase(File(root, id))
        erase(File(root, "$DOWNLOADS/$id"))
    }

    /** The bytes [id]'s files take on the device. */
    fun bytesOf(id: String): Long = File(root, id).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun erase(file: File) = file.walkBottomUp().forEach {
        it.setWritable(true)
        it.delete()
    }

    private companion object {
        // Not a possible plugin id, which starts with a letter or digit.
        const val DOWNLOADS = ".downloads"
    }
}

/** The installed plugins' records, kept in the store; reads and changes take turns. */
internal class InstalledRecords(private val file: StoreFile) {
    private val mutex = Mutex()
    private var known: Map<String, InstalledPlugin>? = null

    suspend fun all(): Map<String, InstalledPlugin> = mutex.withLock { loaded() }

    suspend fun put(plugin: InstalledPlugin) = change { it + (plugin.id to plugin) }

    suspend fun remove(id: String) = change { it - id }

    private suspend fun change(edit: (Map<String, InstalledPlugin>) -> Map<String, InstalledPlugin>) = mutex.withLock {
        val next = edit(loaded())
        file.write(encode(next.values))
        known = next
    }

    private suspend fun loaded(): Map<String, InstalledPlugin> = known ?: decode(file.read().orEmpty()).also { known = it }

    companion object {
        private const val VERSION = 1
        private val safeFileName = Regex("^[0-9A-Za-z][0-9A-Za-z._+-]{0,200}$")
        private val hex = Regex("^[0-9a-f]{64}$")

        fun encode(plugins: Collection<InstalledPlugin>): String = buildJsonObject {
            put("v", VERSION)
            put("plugins", JsonArray(plugins.map(::encode)))
        }.toString()

        private fun encode(plugin: InstalledPlugin) = buildJsonObject {
            put("id", plugin.id)
            put("version", plugin.version)
            put("apiVersion", plugin.apiVersion)
            put("entry", plugin.entryClass)
            put("file", plugin.fileName)
            put("sha256", plugin.sha256)
            put("size", plugin.size)
            put("installedAt", plugin.installedAt)
            put("category", plugin.category.key)
            put("name", plugin.name.toJson())
            put("description", plugin.description.toJson())
        }

        /** The records in [text]; one that is not whole, or names a file outside its folder, is left out. */
        fun decode(text: String): Map<String, InstalledPlugin> {
            val root = J.parse(text)
            if (root["v"].int != VERSION) return emptyMap()
            return root["plugins"].list.mapNotNull(::decodeOne).associateBy { it.id }
        }

        private fun decodeOne(row: J): InstalledPlugin? {
            return InstalledPlugin(
                id = row["id"].string?.takeIf(PluginNames::isId) ?: return null,
                version = row["version"].string?.takeIf(PluginNames::isVersion) ?: return null,
                apiVersion = row["apiVersion"].int ?: return null,
                entryClass = row["entry"].string?.takeIf(PluginNames::isClassName) ?: return null,
                fileName = row["file"].string?.takeIf(safeFileName::matches) ?: return null,
                sha256 = row["sha256"].string?.takeIf(hex::matches) ?: return null,
                size = row["size"].long?.takeIf { it > 0 } ?: return null,
                installedAt = row["installedAt"].long ?: 0,
                category = PluginCategory.of(row["category"].string),
                name = LocalizedText.from(row["name"]),
                description = LocalizedText.from(row["description"]),
            )
        }
    }
}

/** The catalogue as last read, kept in the store so the plugin store has something to show before, or without, a round trip. */
internal class CatalogueCache(private val file: StoreFile) {
    data class Kept(val catalogue: PluginCatalogue, val fetchedAt: Long)

    suspend fun read(): Kept? {
        val root = J.parse(file.read() ?: return null)
        val catalogue = root["body"].string?.let(PluginCatalogue::parse) ?: return null
        return Kept(catalogue, root["fetchedAt"].long ?: 0)
    }

    /** Keeps [body], a catalogue as it arrived, read at [fetchedAt]. */
    suspend fun write(body: String, fetchedAt: Long) = file.write(
        buildJsonObject {
            put("v", 1)
            put("fetchedAt", fetchedAt)
            put("body", body)
        }.toString(),
    )
}
