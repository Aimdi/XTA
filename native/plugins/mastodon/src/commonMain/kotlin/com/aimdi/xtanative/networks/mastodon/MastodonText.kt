package com.aimdi.xtanative.networks.mastodon

import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A piece of a status's text as it is drawn, each kind of link with a destination of its own. */
sealed interface MastodonTextPart {
    val text: String

    data class Text(override val text: String) : MastodonTextPart

    /** An http(s) address written out in the text; [url] is what it opens. */
    data class Link(override val text: String, val url: String) : MastodonTextPart

    /** `@name` or `@name@domain`; [acct] is the account it opens, resolved through the status's mentions. */
    data class Mention(override val text: String, val acct: String) : MastodonTextPart

    /** `#name`; [name] has no `#`. */
    data class Tag(override val text: String, val name: String) : MastodonTextPart

    /** `:shortcode:` naming one of the status's custom emoji. */
    data class Emoji(override val text: String, val emoji: MastodonEmoji) : MastodonTextPart
}

/**
 * A status's HTML as XTA reads it: first plain text, then that text cut into
 * links, mentions and tags found in it, the way Tusky and Ivory show a body.
 * The parts always join back into the text.
 */
object MastodonText {
    private const val SPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
    private val lineBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val blankLines = Regex("\n{3,}")
    private val entity = Regex(
        "https?://[^$SPACE<>\"\\x00-\\x1f]+|@[A-Za-z0-9_]+(?:@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)?|#[\\p{L}\\p{M}\\p{N}_]+",
        RegexOption.IGNORE_CASE,
    )
    private val shortcode = Regex(":([A-Za-z0-9_]{2,}):")
    private val closing = mapOf(')' to '(', ']' to '[', '}' to '{')
    private val wordTypes: Set<Byte> = setOf(
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER,
        Character.OTHER_LETTER, Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
    )

    /** The words of [text], split on any white space. */
    internal val whiteSpace = Regex("[$SPACE]+")

    /**
     * Status or bio HTML as plain text for a card: a line break per `<br>`
     * and per paragraph, at most one empty line in a row, trimmed.
     */
    fun ofHtml(html: String?): String {
        if (html == null || html.isBlank()) return ""
        val document = Jsoup.parse(html.replace(lineBreak, "\n"))
        document.select("p, div").forEach { it.appendChild(TextNode("\n")) }
        return textOf(document.body()).replace(blankLines, "\n\n").trim()
    }

    /** The parts of a post's [MastodonPost.text], its mentions resolved and its emoji found. */
    fun parts(post: MastodonPost): List<MastodonTextPart> = parts(post.text, post.mentionAccts, post.emojis)

    /**
     * [text] cut into parts. Addresses are matched first, so their paths and
     * fragments never turn into tags or mentions; an entity glued to a word
     * (`mail@example.org`, `embedded#fragment`) stays text, as do addresses
     * with credentials or no host. A `:shortcode:` becomes an emoji only when
     * it is one of [emojis].
     */
    fun parts(text: String, mentionAccts: List<String> = emptyList(), emojis: List<MastodonEmoji> = emptyList()): List<MastodonTextPart> {
        val parts = ArrayList<MastodonTextPart>()
        var cursor = 0
        for (match in entity.findAll(text)) {
            val start = match.range.first
            if (start > cursor) parts += MastodonTextPart.Text(text.substring(cursor, start))
            val part = entityPart(match.value, start == 0 || !endsWord(text, start), mentionAccts)
            parts += part
            cursor = start + part.text.length
        }
        if (cursor < text.length) parts += MastodonTextPart.Text(text.substring(cursor))
        return if (emojis.isEmpty()) parts else parts.flatMap { if (it is MastodonTextPart.Text) withEmoji(it.text, emojis) else listOf(it) }
    }

    /** [text] with its `:shortcode:`s that are among [emojis] as emoji: for names and content warnings. */
    fun withEmoji(text: String, emojis: List<MastodonEmoji>): List<MastodonTextPart> {
        val known = emojis.associateBy { it.shortcode }
        val parts = ArrayList<MastodonTextPart>()
        var cursor = 0
        for (match in shortcode.findAll(text)) {
            val emoji = known[match.groupValues[1]] ?: continue
            if (match.range.first > cursor) parts += MastodonTextPart.Text(text.substring(cursor, match.range.first))
            parts += MastodonTextPart.Emoji(match.value, emoji)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) parts += MastodonTextPart.Text(text.substring(cursor))
        return parts
    }

    /**
     * The account `@name` stands for: written with a domain it is that
     * address, else the mention of the status it matches, else the bare name.
     */
    fun resolveMention(raw: String, mentionAccts: List<String>): String {
        val handle = raw.replaceFirst(Regex("^@+"), "").lowercase()
        if ('@' in handle) return handle
        return mentionAccts.map { it.lowercase() }.firstOrNull { it == handle || it.startsWith("$handle@") } ?: handle
    }

    /** The one link in a profile field's HTML, when there is exactly one and it is safe to open. */
    internal fun singleLink(html: String?): String? =
        Jsoup.parseBodyFragment(html.orEmpty()).select("a[href]").singleOrNull()?.let { MastodonUrls.web(it.attr("href")) }

    private fun entityPart(raw: String, boundary: Boolean, mentionAccts: List<String>): MastodonTextPart = when {
        raw.lowercase().startsWith("http") -> trimUrl(raw).let { link ->
            val url = WebUrl.parse(link)
            if (boundary && url != null && url.host.isNotEmpty() && url.userInfo.isEmpty()) MastodonTextPart.Link(link, link) else MastodonTextPart.Text(raw)
        }
        !boundary -> MastodonTextPart.Text(raw)
        raw.startsWith("#") -> MastodonTextPart.Tag(raw, raw.substring(1))
        else -> MastodonTextPart.Mention(raw, resolveMention(raw, mentionAccts))
    }

    /** An address loses trailing punctuation, and closing brackets it never opened. */
    private tailrec fun trimUrl(raw: String): String {
        val last = raw.lastOrNull() ?: return raw
        val open = closing[last]
        val unbalanced = open != null && raw.count { it == last } > raw.count { it == open }
        return if (last in ".;,!?" || unbalanced) trimUrl(raw.dropLast(1)) else raw
    }

    /** Whether [text] has a letter, mark, digit, `_` or `@` just before [index]: an entity there is part of a word. */
    private fun endsWord(text: String, index: Int): Boolean {
        val before = text.codePointBefore(index)
        return before == '_'.code || before == '@'.code || Character.getType(before).toByte() in wordTypes
    }

    /** All the text below [node], as Dart's `Element.text` gathers it: elements add nothing of their own. */
    private fun textOf(node: Node): String = buildString {
        node.forEachNode { child ->
            when (child) {
                is TextNode -> append(child.wholeText)
                is DataNode -> append(child.wholeData)
            }
        }
    }
}
