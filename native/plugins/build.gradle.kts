import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.w3c.dom.Element

// The downloadable plugins (PLUGINS.md), one project per folder, :plugins:<id>,
// all built alike from here:
//
// - plugins/<id>/plugin.json says what the plugin is: its id (the folder's
//   name), version, entry class (implementing :ui's XtaPlugin), store category
//   and whether it is private. A folder without one is a network whose screens
//   have not come yet: its reading layer builds and is tested, and nothing is
//   packaged.
// - plugins/<id>/strings/values/strings.xml (English) and values-<qualifier>/
//   (each other language) hold its text, as Android writes strings. They become
//   the Kotlin object Strings in the entry class's package (generated), so the
//   text loads from any class loader; plugin_name and plugin_description name
//   it in the store.
// - src/commonMain is the reading layer and the screens (Compose, android and
//   desktop alike); src/desktopTest the tests, run with desktopTest.
//
// A plugin is compiled against what the app provides and never bundles it
// (compileOnly): :ui with Compose and its plugin API, :core, :networks, :store,
// :services, jsoup. pluginJar makes the desktop file, signed with jarsigner;
// pluginApk the Android one, a code-only APK signed like the app (where an
// Android SDK is installed); pluginEntry what the catalogue says of it. Both
// files carry xta-plugin.json. -PpluginBuild=<n> numbers a build: version
// 1.0.0 becomes 1.0.0.<n>, newer than every build published before it.

val androidSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null

/** The plugin API the app is built with, as :ui declares it: a plugin says it was built against it. */
val pluginApi: Int = Regex("""const val PLUGIN_API_VERSION: Int = (\d+)""")
    .find(rootProject.file("ui/src/commonMain/kotlin/com/aimdi/xtanative/ui/plugins/XtaPlugin.kt").readText())
    ?.groupValues?.get(1)?.toInt() ?: error("PLUGIN_API_VERSION is not in :ui's XtaPlugin.kt")

val buildNumber = (findProperty("pluginBuild") as String?)?.trim().orEmpty()

@Suppress("UNCHECKED_CAST")
val signing = rootProject.extra["signing"] as Map<String, String>

/** What the app has that plugins compile against. */
val provided = listOf(":ui", ":core", ":networks", ":store", ":services")
val jsoup = "org.jsoup:jsoup:1.18.3"

/** What plugins/<id>/plugin.json says, with the version this build gives it. */
class PluginMeta(val id: String, val version: String, val entry: String, val category: String, val isPrivate: Boolean) {
    val packageName: String get() = entry.substringBeforeLast('.')
    val apk: String get() = "xta-plugin-$id-$version.apk"
    val jar: String get() = "xta-plugin-$id-$version.jar"
}

fun readMeta(dir: File): PluginMeta? {
    val file = File(dir, "plugin.json")
    if (!file.isFile) return null
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(file) as Map<String, Any?>
    val id = checkNotNull(json["id"] as? String) { "plugins/${dir.name}/plugin.json has no id" }
    check(id == dir.name) { "plugins/${dir.name}/plugin.json names the plugin $id, not ${dir.name}" }
    val version = checkNotNull(json["version"] as? String) { "plugins/$id/plugin.json has no version" }
    val entry = checkNotNull(json["entry"] as? String) { "plugins/$id/plugin.json has no entry class" }
    return PluginMeta(
        id = id,
        version = if (buildNumber.isEmpty()) version else "$version.$buildNumber",
        entry = entry,
        category = json["category"] as? String ?: "other",
        isPrivate = json["private"] as? Boolean ?: false,
    )
}

// The strings generator, from here to its end mark: tool/plugincheck.sh runs these
// functions with kotlinc -script, so they use the JDK and Kotlin alone.

/** The text of strings/: by language tag ("" for values/, the default), each string by name, a plural's forms as "name#one". */
fun readStrings(dir: File): Map<String, Map<String, String>> =
    dir.listFiles().orEmpty()
        .filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
        .sortedBy { it.name }
        .associate { folder ->
            languageOf(folder.name) to folder.listFiles().orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }.flatMap(::stringsIn).toMap()
        }

/** "values" is the default, "values-de" German, "values-pt-rBR" "pt-BR", "values-b+sr+Latn" "sr-Latn". */
fun languageOf(folder: String): String {
    val qualifier = folder.removePrefix("values").removePrefix("-")
    return when {
        qualifier.isEmpty() -> ""
        qualifier.startsWith("b+") -> qualifier.removePrefix("b+").replace('+', '-')
        else -> qualifier.replace("-r", "-")
    }
}

fun stringsIn(file: File): List<Pair<String, String>> {
    val children = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
    return (0 until children.length).map { children.item(it) }.filterIsInstance<Element>().flatMap { element ->
        val name = element.getAttribute("name")
        check(Regex("^[a-z][a-z0-9_]*$").matches(name)) { "$file: \"$name\" is no name for a text (a-z, 0-9 and _)" }
        when (element.tagName) {
            "string" -> listOf(name to androidText(element.textContent))
            "plurals" -> element.getElementsByTagName("item").let { items ->
                (0 until items.length).map { items.item(it) as Element }.map { "$name#" + it.getAttribute("quantity") to androidText(it.textContent) }
            }
            else -> emptyList()
        }
    }
}

/** A string as Android reads it: quotes keep it as it is, else runs of white space are one; \n, \t and \' as Android takes them. */
fun androidText(raw: String): String {
    val trimmed = raw.trim()
    val quoted = trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')
    val text = if (quoted) trimmed.substring(1, trimmed.length - 1) else trimmed.replace(Regex("\\s+"), " ")
    return buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    else -> append(next)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}

fun kotlinString(text: String): String = buildString {
    append('"')
    text.forEach { c ->
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }
    append('"')
}

val keywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null", "object",
    "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
)

/** The Kotlin object `Strings` holding [tables], with a property for each text of the default language. */
fun stringsSource(meta: PluginMeta, tables: Map<String, Map<String, String>>): String {
    val defaults = tables[""].orEmpty()
    check("plugin_name" in defaults && "plugin_description" in defaults) {
        "plugins/${meta.id}/strings/values/strings.xml needs plugin_name and plugin_description: the store shows them"
    }
    val texts = defaults.keys.filter { '#' !in it }.sorted()
    val plurals = defaults.keys.filter { '#' in it }.map { it.substringBefore('#') }.distinct().sorted()
    val identifier = { name: String -> if (name in keywords) "`$name`" else name }
    val table = tables.entries.filter { it.value.isNotEmpty() }.joinToString(",\n") { (language, strings) ->
        val rows = strings.entries.sortedBy { it.key }.joinToString(",\n") { (name, text) -> "            ${kotlinString(name)} to ${kotlinString(text)}" }
        "        ${kotlinString(language)} to mapOf(\n$rows,\n        )"
    }
    return buildString {
        appendLine("// Generated by the plugins build (plugins/build.gradle.kts) from plugins/${meta.id}/strings: edit those files.")
        appendLine("package ${meta.packageName}")
        appendLine()
        appendLine("import com.aimdi.xtanative.ui.plugins.PluginPlural")
        appendLine("import com.aimdi.xtanative.ui.plugins.PluginStrings")
        appendLine("import com.aimdi.xtanative.ui.plugins.PluginText")
        appendLine()
        appendLine("/** ${meta.id}'s text in each language it has. */")
        appendLine("internal object Strings : PluginStrings(")
        appendLine("    mapOf(")
        appendLine("$table,")
        appendLine("    ),")
        appendLine(") {")
        texts.forEach { appendLine("    val ${identifier(it)}: PluginText = text(${kotlinString(it)})") }
        plurals.forEach { appendLine("    val ${identifier(it)}: PluginPlural = plural(${kotlinString(it)})") }
        appendLine("}")
    }
}

// The end of the strings generator.

/** What the catalogue says of the plugin besides its files: plugins.json's entry, which CI completes with each file's address, size and SHA-256. */
fun catalogueEntry(meta: PluginMeta, tables: Map<String, Map<String, String>>): String {
    fun inLanguages(name: String) = tables.entries.mapNotNull { (language, strings) -> strings[name]?.let { (language.ifEmpty { "en" }) to it } }.toMap()
    return JsonOutput.prettyPrint(
        JsonOutput.toJson(
            mapOf(
                "id" to meta.id,
                "version" to meta.version,
                "apiVersion" to pluginApi,
                "available" to true,
                "private" to meta.isPrivate,
                "category" to meta.category,
                "name" to inLanguages("plugin_name"),
                "description" to inLanguages("plugin_description"),
                "android" to meta.apk,
                "desktop" to meta.jar,
            ),
        ),
    )
}

/** What a plugin's file says of itself (PluginManifest in :plugin-host). */
fun manifestJson(meta: PluginMeta): String =
    JsonOutput.toJson(mapOf("id" to meta.id, "version" to meta.version, "apiVersion" to pluginApi, "entry" to meta.entry))

subprojects {
    val meta = readMeta(projectDir)
    val android = androidSdk && meta != null
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    if (android) apply(plugin = "com.android.application")

    val generated = layout.buildDirectory.dir("generated/xta-plugin")
    val out = layout.buildDirectory.dir("xta-plugin")

    extensions.configure<KotlinMultiplatformExtension> {
        jvm("desktop") {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
        if (android) {
            androidTarget {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
        sourceSets.getByName("commonMain").dependencies {
            provided.forEach { compileOnly(project(it)) }
            compileOnly(jsoup)
        }
        sourceSets.getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            provided.forEach { implementation(project(it)) }
            implementation(jsoup)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // The live job asks for *LiveTest in every plugin; one may have none.
        filter { isFailOnNoMatchingTests = false }
        // RUN_LIVE=true lets a *LiveTest read the live network (the native workflow's live job).
        val live = System.getenv("RUN_LIVE") ?: "false"
        environment("RUN_LIVE", live)
        inputs.property("live", live)
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = live == "true"
        }
    }

    if (meta == null) return@subprojects
    extra["pluginVersion"] = meta.version

    val pluginStrings = tasks.register("pluginStrings") {
        val sources = fileTree("strings")
        val dir = generated.map { it.dir("kotlin") }
        inputs.files(sources)
        inputs.property("package", meta.packageName)
        outputs.dir(dir)
        doLast {
            val tables = readStrings(file("strings"))
            val file = dir.get().file(meta.packageName.replace('.', '/') + "/Strings.kt").asFile
            file.parentFile.mkdirs()
            file.writeText(stringsSource(meta, tables))
        }
    }
    val pluginManifest = tasks.register("pluginManifest") {
        val dir = generated.map { it.dir("manifest") }
        inputs.property("manifest", manifestJson(meta))
        outputs.dir(dir)
        doLast {
            val root = dir.get().asFile
            root.mkdirs()
            File(root, "xta-plugin.json").writeText(manifestJson(meta))
        }
    }
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.getByName("commonMain").kotlin.srcDir(pluginStrings)
        // At the JAR's root; the APK carries it in its assets (plugins/android.gradle).
        sourceSets.getByName("desktopMain").resources.srcDir(pluginManifest)
    }
    tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach { dependsOn(pluginStrings) }

    tasks.register<Exec>("pluginJar") {
        description = "The desktop plugin: the desktop JAR, signed with jarsigner and the app's key."
        dependsOn(":signingKey")
        val jar = tasks.named<Jar>("desktopJar").flatMap { it.archiveFile }
        val signed = out.map { it.file("desktop/" + meta.jar) }
        inputs.file(jar)
        inputs.property("key", signing.getValue("path") + "#" + signing.getValue("alias"))
        outputs.file(signed)
        executable = File(System.getProperty("java.home"), "bin/jarsigner").path
        environment("XTA_STORE_PASSWORD", signing.getValue("storePassword"))
        environment("XTA_KEY_PASSWORD", signing.getValue("keyPassword"))
        doFirst {
            signed.get().asFile.parentFile.mkdirs()
            args(
                "-keystore", signing.getValue("path"),
                "-storepass:env", "XTA_STORE_PASSWORD",
                "-keypass:env", "XTA_KEY_PASSWORD",
                "-signedjar", signed.get().asFile.path,
                jar.get().asFile.path,
                signing.getValue("alias"),
            )
            logger.lifecycle("Signing ${meta.jar} with ${signing.getValue("kind")}")
        }
    }

    tasks.register("pluginEntry") {
        description = "What the catalogue says of the plugin, for CI to complete with its files."
        dependsOn(pluginStrings)
        val entry = out.map { it.file("${meta.id}.json") }
        inputs.files(fileTree("strings"))
        inputs.property("version", meta.version)
        outputs.file(entry)
        doLast {
            entry.get().asFile.apply { parentFile.mkdirs() }.writeText(catalogueEntry(meta, readStrings(file("strings"))))
        }
    }

    if (android) {
        apply(from = rootProject.file("plugins/android.gradle"))
        tasks.register<Copy>("pluginApk") {
            description = "The Android plugin: the release APK, code only, signed like the app."
            dependsOn("assembleRelease")
            from(layout.buildDirectory.file("outputs/apk/release/${project.name}-release.apk"))
            into(out.map { it.dir("android") })
            rename { meta.apk }
        }
    }
}

/** The plugins that are packaged: those with a plugin.json. */
fun packaged(): List<Project> = subprojects.filter { File(it.projectDir, "plugin.json").isFile }

/** The plugins' JARs and catalogue entries in plugins/build/out/desktop, for CI to publish. */
tasks.register<Sync>("desktopPlugins") {
    packaged().forEach { from(it.tasks.named("pluginJar")); from(it.tasks.named("pluginEntry")) }
    into(layout.buildDirectory.dir("out/desktop"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

/** The plugins' APKs and catalogue entries in plugins/build/out/android, for CI to publish: only where an Android SDK is installed. */
if (androidSdk) {
    tasks.register<Sync>("androidPlugins") {
        packaged().forEach { from(it.tasks.named("pluginApk")); from(it.tasks.named("pluginEntry")) }
        into(layout.buildDirectory.dir("out/android"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

/** Every plugin's tests. */
tasks.register("test") {
    dependsOn(subprojects.map { "${it.path}:desktopTest" })
}
