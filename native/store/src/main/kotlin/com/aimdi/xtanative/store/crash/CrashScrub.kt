package com.aimdi.xtanative.store.crash

import com.aimdi.xtanative.core.log.LogRedaction

/**
 * A crash report as it may be filed on a public issue tracker: credentials
 * taken out ([LogRedaction]), and what could tell who reads, or what they
 * read: the home folder and the user's name, @handles (X's, Bluesky's and
 * the fediverse's), the path and query of every address (its host stays),
 * and the excerpt of the input kotlinx.serialization quotes.
 */
object CrashScrub {
    private val handle = Regex("""(?<![\w.$])@\w+(?:\.[\w-]+)*(?:@[\w-]+(?:\.[\w-]+)+)?""")
    private val address = Regex("""(?i)\b(https?|wss?|ftp)://(?:[^\s/@?#"'`<>]*@)?([^\s/?#"'`<>]+)([^\s"'`<>)]*)""")
    private val jsonInput = Regex("""(JSON input:)[^\r\n]*""")

    fun scrubbed(text: String, home: String = System.getProperty("user.home").orEmpty(), user: String = System.getProperty("user.name").orEmpty()): String =
        LogRedaction.redact(text)
            .let { if (home.length > 1) Regex(Regex.escape(home.trimEnd('/')) + """(?![\w.-])""").replace(it, "~") else it }
            .let { if (user.length > 1) Regex("""(?<![\w.-])""" + Regex.escape(user) + """(?![\w-])""").replace(it, "[user]") else it }
            .let { handle.replace(it, "@[handle]") }
            .let { address.replace(it) { m -> "${m.groupValues[1]}://${m.groupValues[2]}" + if (m.groupValues[3].length > 1) "/[path]" else m.groupValues[3] } }
            .let { jsonInput.replace(it, "$1 [cut]") }
}
