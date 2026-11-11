// The libraries the app ships with their licences, for Settings › App info ›
// Licences, as XTA's licence page lists every package it is built with.
// Applied by :app and :desktop, which call extra["writeLicences"] with the
// configuration their app runs with and a folder that becomes part of its
// resources (xta-native-licences.json). Plugins bundle no library of their own
// (plugins/build.gradle.kts), so the app's list is theirs too.
//
// Each library Gradle resolves there is listed with the licences its POM (or
// a parent's) names. Their texts are the licence and notice files its jar
// carries, else the standard text of each licence (gradle/licences/), else the
// licence's name and address. Beside them, the voice engine the app downloads
// (Android) or carries (the desktop): sherpa-onnx, ONNX Runtime and espeak-ng,
// whose text is XTA's own (assets/licenses/espeak-ng.txt); and XTA, Fritter and
// Quacker, which XTA Native is written again from.

import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Element

// The licence list, from here to its end mark: Kotlin and the JDK alone, so the
// list can be made outside Gradle from POMs and jars at hand.

/** A licence a POM names. */
data class PomLicence(val name: String, val url: String)

/** What a library's POM says of it: its name, its site, its licences, and its parent's coordinates. */
data class PomFacts(val name: String?, val url: String?, val licences: List<PomLicence>, val parent: String?)

/** A library in the list: [texts] are keys of the list's texts. */
data class ShippedLibrary(val name: String, val module: String, val version: String, val url: String?, val licences: List<String>, val texts: List<String>)

fun childrenOf(parent: Element, tag: String): List<Element> =
    (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

fun textOf(parent: Element, tag: String): String = childrenOf(parent, tag).firstOrNull()?.textContent?.trim().orEmpty()

fun pomFacts(file: File): PomFacts? = try {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    val root = factory.newDocumentBuilder().parse(file).documentElement
    val licences = childrenOf(root, "licenses").flatMap { childrenOf(it, "license") }
        .map { PomLicence(textOf(it, "name"), textOf(it, "url")) }
        .filter { it.name.isNotEmpty() || it.url.isNotEmpty() }
    val parent = childrenOf(root, "parent").firstOrNull()?.let { listOf("groupId", "artifactId", "version").joinToString(":") { tag -> textOf(it, tag) } }
    PomFacts(
        name = textOf(root, "name").takeIf { it.isNotEmpty() && '$' !in it },
        url = textOf(root, "url").takeIf { it.startsWith("http") && '$' !in it },
        licences = licences,
        parent = parent,
    )
} catch (e: Exception) {
    null
}

/** The SPDX id of a licence a POM names, for those whose standard text gradle/licences/ holds; null for any other. */
fun licenceId(licence: PomLicence): String? {
    val said = (licence.name + " " + licence.url).lowercase()
    return when {
        "apache" in said && "2" in said -> "Apache-2.0"
        Regex("""\bmit\b""").containsMatchIn(said) -> "MIT"
        "bsd-3" in said || "3-clause" in said || "new bsd" in said || "bsd 3" in said -> "BSD-3-Clause"
        "bsd-2" in said || "2-clause" in said || "simplified bsd" in said || "bsd 2" in said -> "BSD-2-Clause"
        else -> null
    }
}

val licenceFile = Regex("""^(META-INF/)?(LICEN[CS]E|NOTICE|COPYING)([._-][A-Za-z0-9._-]*)?$""", RegexOption.IGNORE_CASE)

/** The licence and notice files [archive] carries at its root or in META-INF, in name order. */
fun licenceFilesIn(archive: File): List<String> = try {
    ZipFile(archive).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory && licenceFile.matches(it.name) && it.size in 1..200_000 }
            .sortedBy { it.name }
            .map { zip.getInputStream(it).use { input -> input.readBytes().toString(Charsets.UTF_8) }.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
} catch (e: Exception) {
    emptyList()
}

/** A text's key in the list: the same text is kept once. */
fun textKey(text: String): String =
    "t" + MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

/**
 * A library as the list shows it: its licences by id (or by the name its POM
 * gives them), and their texts, put in [texts]: the files its own archive
 * carries, else the standard text of each, else what its POM says of each.
 */
fun shippedLibrary(
    module: String,
    version: String,
    pom: PomFacts?,
    licences: List<PomLicence>,
    carried: List<String>,
    standard: Map<String, String>,
    texts: MutableMap<String, String>,
): ShippedLibrary {
    val named = licences.map { licence -> licenceId(licence) ?: licence.name.ifEmpty { licence.url } }.distinct()
    val written = carried.ifEmpty {
        licences.map { licence -> licenceId(licence)?.let(standard::get) ?: listOf(licence.name, licence.url).filter { it.isNotEmpty() }.joinToString("\n") }
    }.distinct()
    val keys = written.map { text -> textKey(text).also { texts[it] = text } }
    return ShippedLibrary(pom?.name ?: module.substringAfter(':'), module, version, pom?.url, named, keys)
}

fun jsonString(text: String): String = buildString {
    append('"')
    text.forEach { c ->
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}

/** The list as the app reads it (ui/settings/Licences.kt): the libraries in name order, and their texts by key. */
fun licencesJson(libraries: List<ShippedLibrary>, texts: Map<String, String>): String = buildString {
    append("{\"libraries\":[")
    libraries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, ShippedLibrary::name).thenBy { it.module }).forEachIndexed { index, library ->
        if (index > 0) append(',')
        append("{\"name\":").append(jsonString(library.name))
        append(",\"module\":").append(jsonString(library.module))
        append(",\"version\":").append(jsonString(library.version))
        library.url?.let { append(",\"url\":").append(jsonString(it)) }
        append(",\"licences\":[").append(library.licences.joinToString(",", transform = ::jsonString)).append(']')
        append(",\"texts\":[").append(library.texts.joinToString(",", transform = ::jsonString)).append("]}")
    }
    append("],\"texts\":{")
    append(texts.entries.sortedBy { it.key }.joinToString(",") { (key, text) -> jsonString(key) + ":" + jsonString(text) })
    append("}}")
}

// The end of the licence list.

/** The standard texts in gradle/licences/, by SPDX id. */
fun standardTexts(): Map<String, String> = listOf("Apache-2.0", "MIT", "BSD-3-Clause", "BSD-2-Clause")
    .associateWith { rootProject.file("gradle/licences/$it.txt").readText().trim() }

/** A parent POM, by its coordinates, for a library whose own names no licence. */
fun parentPom(coordinates: String): File? = try {
    configurations.detachedConfiguration(dependencies.create("$coordinates@pom")).apply { isTransitive = false }.resolve().firstOrNull()
} catch (e: Exception) {
    null
}

/** The licences [pom] names, else the first parent's that names any, a few parents up. */
fun licencesOf(pom: PomFacts?): List<PomLicence> {
    var facts = pom
    repeat(4) {
        val current = facts ?: return emptyList()
        if (current.licences.isNotEmpty()) return current.licences
        facts = current.parent?.let(::parentPom)?.let(::pomFacts)
    }
    return facts?.licences.orEmpty()
}

/** The POM of each of [modules], by its identifier. */
fun pomsOf(modules: Collection<ModuleComponentIdentifier>): Map<ModuleComponentIdentifier, File> {
    if (modules.isEmpty()) return emptyMap()
    val result = dependencies.createArtifactResolutionQuery()
        .forComponents(modules)
        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
        .execute()
    return result.resolvedComponents.mapNotNull { component ->
        val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
        component.getArtifacts(MavenPomArtifact::class.java).filterIsInstance<ResolvedArtifactResult>().firstOrNull()?.let { id to it.file }
    }.toMap()
}

/**
 * What the app ships beside the libraries Gradle resolves: the voice engine
 * downloaded voices read with, as XTA's page lists it, and the apps XTA
 * Native is XTA written again from, whose MIT licences go with it
 * (the repository's LICENSE, and Fritter's and Quacker's in gradle/licences/).
 */
fun beside(standard: Map<String, String>, texts: MutableMap<String, String>): List<ShippedLibrary> {
    fun kept(text: String): String = textKey(text).also { texts[it] = text }
    fun text(path: String): String? = rootProject.file(path).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    fun mit(name: String, module: String, url: String, path: String): ShippedLibrary? =
        text(path)?.let { ShippedLibrary(name, module, "", url, listOf("MIT"), listOf(kept(it))) }
    return listOfNotNull(
        ShippedLibrary("sherpa-onnx", "k2-fsa:sherpa-onnx", "", "https://github.com/k2-fsa/sherpa-onnx", listOf("Apache-2.0"), listOf(kept(standard.getValue("Apache-2.0")))),
        ShippedLibrary("ONNX Runtime", "microsoft:onnxruntime", "", "https://onnxruntime.ai", listOf("MIT"), listOf(kept(rootProject.file("gradle/licences/onnxruntime.txt").readText().trim()))),
        text("gradle/licences/espeak-ng.txt")?.let {
            ShippedLibrary("espeak-ng", "espeak-ng:espeak-ng", "", "https://github.com/espeak-ng/espeak-ng", listOf("GPL-3.0-or-later"), listOf(kept(it)))
        },
        mit("XTA", "aimdi:xta", "https://github.com/Aimdi/XTA", "../LICENSE"),
        mit("Fritter", "jonjomckay:fritter", "https://github.com/jonjomckay/fritter", "gradle/licences/fritter.txt"),
        mit("Quacker", "thehcj:quacker", "https://github.com/TheHCJ/Quacker", "gradle/licences/quacker.txt"),
    )
}

/**
 * Writes xta-native-licences.json into [out]: every library the
 * configuration [configurationName] ships (a module that brings a file:
 * no platform, no redirect of a multiplatform library to its variant),
 * with what goes [beside] them. A library whose POM cannot be read is
 * listed by its coordinates.
 */
fun writeLicences(configurationName: String, out: File) {
    val artifacts = configurations.getByName(configurationName).incoming.artifactView {
        lenient(true)
        componentFilter { it is ModuleComponentIdentifier }
    }.artifacts.artifacts
    val byModule = artifacts.groupBy { it.id.componentIdentifier as ModuleComponentIdentifier }
    val poms = pomsOf(byModule.keys)
    val standard = standardTexts()
    val texts = sortedMapOf<String, String>()
    val libraries = byModule.map { (id, files) ->
        val pom = poms[id]?.let(::pomFacts)
        val carried = files.flatMap { licenceFilesIn(it.file) }
        shippedLibrary("${id.group}:${id.module}", id.version, pom, licencesOf(pom), carried, standard, texts)
    } + beside(standard, texts)
    out.mkdirs()
    File(out, "xta-native-licences.json").writeText(licencesJson(libraries, texts))
    logger.lifecycle("Licences: ${libraries.size} libraries, ${texts.size} texts, from $configurationName")
}

extra["writeLicences"] = { configurationName: String, out: File -> writeLicences(configurationName, out) }
