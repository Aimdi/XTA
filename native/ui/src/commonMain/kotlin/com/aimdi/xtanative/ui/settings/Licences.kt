package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.json.J
import java.io.File

/** A library the app ships: its [module] coordinates, the licences its POM names, and the keys of their texts. */
data class ShippedLibrary(val name: String, val module: String, val version: String, val url: String?, val licences: List<String>, val texts: List<String>)

/**
 * Every library the app and its plugins ship with their licence texts (XTA's
 * licence page, showLicensePage), as the build lists them
 * (gradle/licences.gradle.kts writes [RESOURCE] into each app), in name
 * order, with what the platform adds at run time ([JavaRuntimeLicence]).
 */
class LicenceList(val libraries: List<ShippedLibrary>, private val texts: Map<String, String>) {
    /** [library]'s licence texts, each whole, in its order. */
    fun textsOf(library: ShippedLibrary): List<String> = library.texts.mapNotNull(texts::get)

    fun find(module: String): ShippedLibrary? = libraries.firstOrNull { it.module == module }

    /** This list with [other]'s libraries among its own, in name order. */
    operator fun plus(other: LicenceList): LicenceList = LicenceList(sorted(libraries + other.libraries), texts + other.texts)

    companion object {
        /** The list the build puts in the app's resources. */
        const val RESOURCE = "xta-native-licences.json"

        val EMPTY = LicenceList(emptyList(), emptyMap())

        fun parse(json: String): LicenceList {
            val root = J.parse(json)
            val libraries = root["libraries"].list.mapNotNull { library ->
                val module = library["module"].string ?: return@mapNotNull null
                ShippedLibrary(
                    name = library["name"].string ?: module.substringAfter(':'),
                    module = module,
                    version = library["version"].string.orEmpty(),
                    url = library["url"].string,
                    licences = library["licences"].list.mapNotNull { it.string },
                    texts = library["texts"].list.mapNotNull { it.string },
                )
            }
            val texts = root["texts"].keys.associateWith { key -> root["texts"][key].string.orEmpty() }
            return LicenceList(sorted(libraries), texts)
        }

        /** The list the build bundled, with the Java runtime the desktop carries, read once. */
        val shipped: LicenceList by lazy { bundled() + runCatching { JavaRuntimeLicence.current() }.getOrDefault(EMPTY) }

        private fun bundled(): LicenceList = runCatching {
            LicenceList::class.java.classLoader?.getResourceAsStream(RESOURCE)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()?.let(::parse) ?: EMPTY

        private fun sorted(libraries: List<ShippedLibrary>): List<ShippedLibrary> =
            libraries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, ShippedLibrary::name).thenBy { it.module })
    }
}

/**
 * The Java runtime the desktop app carries (its tarball and package bring
 * their own), with the licence and exception its image keeps in
 * legal/java.base. Android's runtime is the system's and keeps no such
 * folder, so nothing is added there.
 */
object JavaRuntimeLicence {
    private val NOTICES = listOf("LICENSE", "ASSEMBLY_EXCEPTION", "ADDITIONAL_LICENSE_INFO")

    fun current(): LicenceList {
        val home = System.getProperty("java.home")?.takeIf { it.isNotEmpty() } ?: return LicenceList.EMPTY
        return of(File(home, "legal/java.base"), System.getProperty("java.runtime.version").orEmpty())
    }

    fun of(legal: File, version: String): LicenceList {
        val texts = NOTICES.mapNotNull { name -> File(legal, name).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { "java:$name" to it } }
        if (texts.isEmpty()) return LicenceList.EMPTY
        val library = ShippedLibrary("OpenJDK", "openjdk:java-runtime", version, "https://openjdk.org", listOf("GPL-2.0-with-classpath-exception"), texts.map { it.first })
        return LicenceList(listOf(library), texts.toMap())
    }
}

/** A library's licence texts as its page shows them: paragraph by paragraph, a mark between one licence and the next. */
object LicenceText {
    sealed interface Paragraph {
        data class Words(val text: String) : Paragraph

        data object Between : Paragraph
    }

    fun paragraphs(texts: List<String>): List<Paragraph> = texts.flatMapIndexed { index, text ->
        val words = text.replace("\r\n", "\n").split(Regex("""\n[ \t]*\n""")).map { it.trimEnd() }.filter { it.isNotBlank() }.map { Paragraph.Words(it.trimStart('\n')) }
        if (index == 0) words else listOf(Paragraph.Between) + words
    }
}
