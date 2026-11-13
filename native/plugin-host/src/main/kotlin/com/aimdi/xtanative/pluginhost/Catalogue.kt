package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.core.json.J
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The platforms a plugin is built for; each has its own file in the catalogue. */
enum class PluginPlatform(val key: String, val extension: String) {
    /** A code-only APK, never installed, loaded with DexClassLoader. */
    ANDROID("android", "apk"),

    /** A JAR signed with jarsigner, loaded with a URLClassLoader. */
    DESKTOP("desktop", "jar");

    /** The name a plugin's file is kept under, as CI names it. */
    fun fileName(id: String, version: String): String = "xta-plugin-$id-$version.$extension"
}

/** Why a plugin exists, for the store's groups, in the order XTA's store shows them. */
enum class PluginCategory {
    SOCIAL,
    COMMUNITIES,
    NEWSLETTERS,
    ART,
    MARKETS,
    BOOKMARKS,
    MEDIA,
    SITES,

    /** A category this app does not know yet: listed last. */
    OTHER;

    /** As the catalogue writes it: "social". */
    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String?): PluginCategory = entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: OTHER
    }
}

/**
 * Text the catalogue gives in several languages, keyed by language tag
 * ("en", "de", "pt-BR"); a plain string there stands for every language.
 */
data class LocalizedText(val byTag: Map<String, String>) {
    /** The text for [locale]: its full tag, its language with script or country, its language, English, then any. */
    fun resolve(locale: Locale): String? {
        val texts = byTag.mapKeys { normalized(it.key) }
        return candidates(locale).firstNotNullOfOrNull { texts[it] } ?: byTag.values.firstOrNull()
    }

    internal fun toJson(): JsonObject = JsonObject(byTag.mapValues { JsonPrimitive(it.value) })

    companion object {
        val EMPTY = LocalizedText(emptyMap())

        internal fun from(json: J): LocalizedText {
            val plain = json.string
            val texts = if (plain != null) mapOf("" to plain) else json.keys.associateWith { json[it].string.orEmpty() }
            return LocalizedText(texts.filterValues { it.isNotBlank() })
        }

        private fun normalized(tag: String) = tag.trim().replace('_', '-').lowercase()

        /**
         * The language is read off the BCP 47 tag, which says "id", "he" and
         * "yi" as the catalogue does, where Android's Locale still says "in",
         * "iw" and "ji".
         */
        private fun candidates(locale: Locale): List<String> {
            val tag = locale.toLanguageTag()
            val language = tag.substringBefore('-')
            return listOfNotNull(
                tag,
                locale.script.takeIf { it.isNotEmpty() }?.let { "$language-$it" },
                locale.country.takeIf { it.isNotEmpty() }?.let { "$language-$it" },
                language,
                "en",
            ).map(::normalized)
        }
    }
}

/** A plugin's file for one platform, and what it has to be: [size] bytes whose SHA-256 is [sha256] (lowercase hex). */
data class PluginArtifact(val url: String, val sha256: String, val size: Long) {
    companion object {
        private val hex = Regex("^[0-9a-fA-F]{64}$")

        /** The file [json] describes; null when any part is missing or not what it should be. */
        internal fun from(json: J): PluginArtifact? {
            val url = json["url"].string?.trim()?.takeIf { it.toHttpUrlOrNull() != null } ?: return null
            val sha256 = json["sha256"].string?.trim()?.takeIf(hex::matches)?.lowercase() ?: return null
            val size = json["size"].long?.takeIf { it > 0 } ?: return null
            return PluginArtifact(url, sha256, size)
        }
    }
}

/** One plugin the catalogue describes, as `plugins.json` gives it (PLUGINS.md). */
data class CatalogueEntry(
    val id: String,
    val version: String,
    /** The `:plugin-api` version it was built against. */
    val apiVersion: Int,
    /** False when the catalogue holds it back: then it is not offered, though a copy already installed stays. */
    val available: Boolean,
    /** Offered only while "show private plugins" is on. */
    val isPrivate: Boolean,
    val category: PluginCategory,
    val name: LocalizedText,
    val description: LocalizedText,
    val artifacts: Map<PluginPlatform, PluginArtifact>,
) {
    fun artifact(platform: PluginPlatform): PluginArtifact? = artifacts[platform]
}

/** The catalogue published with the plugins (`plugins.json`): what can be installed, in the order it lists it. */
data class PluginCatalogue(
    /** The plugin API the document was written for; an entry that gives none takes this one. */
    val apiVersion: Int?,
    val entries: List<CatalogueEntry>,
) {
    fun entry(id: String): CatalogueEntry? = entries.firstOrNull { it.id == id }

    companion object {
        /**
         * The catalogue in [text], read as XTA reads its own: an entry that is
         * not the shape expected is skipped rather than thrown into a screen,
         * one whose id or version could not be a folder name is ignored, and
         * the first entry for an id wins. Null when [text] is no catalogue at
         * all, so a broken answer never replaces a good copy.
         */
        fun parse(text: String): PluginCatalogue? {
            val root = J.parse(text)
            if (root["plugins"].raw !is JsonArray) return null
            val apiVersion = root["apiVersion"].int
            val entries = root["plugins"].list.mapNotNull { entryOf(it, apiVersion ?: 1) }.distinctBy { it.id }
            return PluginCatalogue(apiVersion, entries)
        }

        private fun entryOf(json: J, documentApi: Int): CatalogueEntry? {
            val id = json["id"].string?.takeIf(PluginNames::isId) ?: return null
            val version = json["version"].string?.takeIf(PluginNames::isVersion) ?: return null
            return CatalogueEntry(
                id = id,
                version = version,
                apiVersion = json["apiVersion"].int ?: documentApi,
                // As XTA reads it: only `false` itself holds an entry back.
                available = json["available"].boolean != false,
                isPrivate = json["private"].boolean == true,
                category = PluginCategory.of(json["category"].string),
                name = LocalizedText.from(json["name"]),
                description = LocalizedText.from(json["description"]),
                artifacts = PluginPlatform.entries.mapNotNull { platform -> PluginArtifact.from(json[platform.key])?.let { platform to it } }.toMap(),
            )
        }
    }
}

/** What a plugin's id, version and entry class may be: the first two become folder names under the plugins directory. */
internal object PluginNames {
    private val id = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
    private val version = Regex("^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")
    private val className = Regex("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")

    fun isId(text: String): Boolean = id.matches(text)

    fun isVersion(text: String): Boolean = version.matches(text)

    fun isClassName(text: String): Boolean = text.length <= 512 && className.matches(text)
}
