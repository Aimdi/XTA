package com.aimdi.xtanative.store.crash

import com.aimdi.xtanative.core.log.LogRedaction

/**
 * A crash report as it may be filed on a public issue tracker: credentials
 * taken out ([LogRedaction]), and what could tell who reads, or what they
 * read: the home folder and the user's name, @handles (X's, Bluesky's and
 * the fediverse's), e-mail addresses, the path and query of every address,
 * and its host too unless it is one of the app's own ([knownHost]: a
 * reader's own server, a WebDAV or Immich one, would name them), and the
 * excerpt of the input kotlinx.serialization quotes. Kotlin and
 * Java annotations an error names (`@Composable`, `@kotlin.Metadata`) are
 * code, not a reader, and stay.
 */
object CrashScrub {
    private val handle = Regex("""(?<![\w.$])@\w+(?:\.[\w-]+)*(?:@[\w-]+(?:\.[\w-]+)+)?""")

    /** Annotations an error message may name bare; a handle that is one says nothing of a reader. */
    private val annotations = setOf(
        "Composable", "ReadOnlyComposable", "NonRestartableComposable", "Stable", "Immutable", "UiComposable", "ComposableTarget",
        "Serializable", "SerialName", "Contextual", "Transient", "Polymorphic", "JvmStatic", "JvmField", "JvmName", "JvmOverloads",
        "JvmInline", "Volatile", "Synchronized", "Throws", "Deprecated", "Suppress", "OptIn", "RequiresOptIn", "Override",
        "FunctionalInterface", "Nullable", "NotNull", "Metadata", "DslMarker", "PublishedApi", "Test",
    )

    /** Java's way of naming one: lowercase packages, then an uppercase class (`kotlin.Metadata`, `androidx.compose.runtime.Composable`). */
    private val qualifiedAnnotation = Regex("""(?:[a-z][a-z0-9_]*\.)+[A-Z]\w*""")

    /** An opt-in marker (`ExperimentalFoundationApi`), a known annotation, or a qualified one. */
    private fun isAnnotation(name: String) =
        name in annotations || (name.first().isUpperCase() && name.endsWith("Api")) || qualifiedAnnotation.matches(name)
    /** An e-mail address; a fediverse handle (`@carol@mastodon.social`) is no address, nor `java.base@17.0.21`. */
    private val email = Regex("""(?<![\w.+@-])[\w.+-]+@[\w-]+(?:\.[\w-]+)*\.[A-Za-z]{2,}(?![\w-])""")

    /** Domains the app reads or files to itself, each with every host under it: naming them says nothing of a reader. */
    private val knownDomains = setOf(
        "x.com", "twitter.com", "twimg.com", "t.co", "github.com", "githubusercontent.com",
        "deepl.com", "openai.com", "openrouter.ai", "x.ai", "libretranslate.com",
    )

    /** Single hosts the app calls, whose domains host others too. */
    private val knownHosts = setOf("web.archive.org", "xy-threads.fly.dev", "translate.fedilab.app", "lt.vern.cc", "k2-fsa.github.io")

    /** Whether [host] (a port may follow) is one of the app's own, kept in a filed report. */
    internal fun knownHost(host: String): Boolean {
        val name = host.substringBeforeLast(':').takeIf { ':' in host && !host.startsWith('[') } ?: host
        val lower = name.lowercase().trimEnd('.')
        return lower in knownHosts || knownDomains.any { lower == it || lower.endsWith(".$it") }
    }

    private val address = Regex("""(?i)\b(https?|wss?|ftp)://(?:[^\s/@?#"'`<>]*@)?([^\s/?#"'`<>)]+)([^\s"'`<>)]*)""")
    private val jsonInput = Regex("""(JSON input:)[^\r\n]*""")

    private fun hostShown(host: String) = if (knownHost(host)) host else "[host]"

    fun scrubbed(text: String, home: String = System.getProperty("user.home").orEmpty(), user: String = System.getProperty("user.name").orEmpty()): String =
        LogRedaction.redact(text)
            .let { address.replace(it) { m -> "${m.groupValues[1]}://${hostShown(m.groupValues[2])}" + if (m.groupValues[3].length > 1) "/[path]" else m.groupValues[3] } }
            .let { email.replace(it, "[email]") }
            .let { if (home.length > 1) Regex(Regex.escape(home.trimEnd('/')) + """(?![\w.-])""").replace(it, "~") else it }
            .let { if (user.length > 1) Regex("""(?<![\w.-])""" + Regex.escape(user) + """(?![\w-])""").replace(it, "[user]") else it }
            .let { text -> handle.replace(text) { m -> if (isAnnotation(m.value.drop(1))) m.value else "@[handle]" } }
            .let { jsonInput.replace(it, "$1 [cut]") }
}
