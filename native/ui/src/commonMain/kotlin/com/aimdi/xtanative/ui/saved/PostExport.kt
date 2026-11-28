package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedPost
import com.aimdi.xtanative.ui.post.PostView
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A picture of an exported post: its address, or the copy kept on the device ([local]) when there is one. */
data class ExportedPicture(val url: String, val local: String? = null, val video: Boolean = false) {
    val shown: String get() = local ?: url
}

/** A post as an export writes it, whatever network it came from: only what was already read, nothing fetched for it. */
data class ExportedPost(
    val authorName: String,
    val handle: String? = null,
    val at: Instant? = null,
    val title: String? = null,
    val text: String = "",
    val url: String? = null,
    val pictures: List<ExportedPicture> = emptyList(),
    val quoted: ExportedPost? = null,
    /** The reader's own note on it, from Saved. */
    val note: String? = null,
)

/**
 * Posts as a readable file: Markdown for a notes app (Obsidian, Joplin), or a
 * plain HTML page to print from a browser. Pictures are linked where they
 * are on the web, or to the copy the offline collection keeps; nothing is
 * downloaded for an export.
 */
object PostExport {
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

    /** [post] as it is shown (a repost's original), its quote too, linking [url] and [local]'s copies of its pictures. */
    fun of(post: XPost, url: String = post.shown.url, local: (String) -> String? = { null }, note: String? = null): ExportedPost {
        val shown = post.shown
        return ExportedPost(
            authorName = shown.author.name.ifEmpty { shown.author.screenName },
            handle = shown.author.screenName.takeIf { it.isNotEmpty() },
            at = shown.createdAt,
            text = PostView.copiedText(shown).trim(),
            url = url,
            pictures = shown.media.map { ExportedPicture(it.url, local(it.url), it.kind != XMedia.Kind.PHOTO) },
            quoted = shown.quoted?.let { of(it, local = local) },
            note = note?.takeIf { it.isNotBlank() },
        )
    }

    /** A saved post of any network, from what Saved keeps of it; null when nothing readable was kept. */
    fun of(saved: SavedPost, local: (String) -> String? = { null }): ExportedPost? {
        val about = saved.about
        about.xPost?.let { return of(it, local = local, note = saved.note) }
        if (about.kind == SavedContent.Kind.NONE) return null
        val post = about.post
        val name = first(post, "authorName", "name", "author", "handle", "acct", "subreddit") ?: about.network.orEmpty()
        return ExportedPost(
            authorName = name,
            handle = first(post, "handle", "acct")?.takeIf { it != name },
            at = savedTime(saved),
            title = first(post, "title"),
            text = first(post, "text", "selfText").orEmpty().trim(),
            url = first(post, "url", "permalink"),
            pictures = about.media.map { ExportedPicture(it.url, local(it.url), it.kind != XMedia.Kind.PHOTO) },
            note = saved.note?.takeIf { it.isNotBlank() },
        )
    }

    private fun first(post: J, vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> post[key].string?.takeIf { it.isNotBlank() } }

    private fun savedTime(saved: SavedPost): Instant? = saved.savedAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /** [posts] as Markdown, under [title] when there is one. */
    fun markdown(title: String?, posts: List<ExportedPost>): String = buildString {
        title?.let { append("# ").append(inline(it)).append("\n\n") }
        append(posts.joinToString("\n---\n\n") { markdownPost(it, quoteDepth = 0) })
    }

    private fun markdownPost(post: ExportedPost, quoteDepth: Int): String {
        val lines = buildList {
            add(header(post))
            post.title?.let { add("**" + inline(it) + "**") }
            if (post.text.isNotEmpty()) add(post.text.lines().joinToString("  \n") { block(it) })
            post.pictures.forEach { picture -> add(if (picture.video) "[▶](${target(picture.shown)})" else "![](${target(picture.shown)})") }
            post.quoted?.let { add(markdownPost(it, quoteDepth + 1)) }
            post.url?.let { add("<$it>") }
            post.note?.let { note -> add(note.lines().joinToString("\n") { "> " + block(it) }) }
        }
        val body = lines.joinToString("\n\n")
        return if (quoteDepth == 0) body + "\n" else body.lines().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }
    }

    private fun header(post: ExportedPost): String {
        val who = listOfNotNull("**" + inline(post.authorName) + "**", post.handle?.let { "@" + inline(it) }).joinToString(" ")
        return listOfNotNull(who, post.at?.let(time::format)).joinToString(" · ")
    }

    /** A file's address as a Markdown link takes it: spaces and brackets encoded. */
    private fun target(address: String): String = address.replace(" ", "%20").replace("(", "%28").replace(")", "%29")

    private val inlineSpecials = Regex("""([\\`*_\[\]<>|])""")

    private val leadingMarks = Regex("""^(\s*)([#+\-=])""")

    private val leadingNumber = Regex("""^(\s*\d+)([.)])""")

    /** Text as Markdown shows it as written: its marks escaped. */
    internal fun inline(text: String): String = text.replace(inlineSpecials, "\\\\$1")

    /** A line of a post as Markdown shows it as written: a leading # or - starts no heading or list. */
    internal fun block(line: String): String = inline(line)
        .replace(leadingMarks) { it.groupValues[1] + "\\" + it.groupValues[2] }
        .replace(leadingNumber) { it.groupValues[1] + "\\" + it.groupValues[2] }

    /** [posts] as a page to print, under [title]. */
    fun html(title: String?, posts: List<ExportedPost>): String = buildString {
        append("<!doctype html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">")
        append("<title>").append(escape(title ?: posts.firstOrNull()?.let(::plainHeader).orEmpty())).append("</title>")
        append("<style>").append(STYLE).append("</style></head><body>\n")
        title?.let { append("<h1>").append(escape(it)).append("</h1>\n") }
        posts.forEach { append(htmlPost(it, quoted = false)).append('\n') }
        append("</body></html>\n")
    }

    private fun htmlPost(post: ExportedPost, quoted: Boolean): String = buildString {
        append(if (quoted) "<blockquote>" else "<article>")
        append("<header><b>").append(escape(post.authorName)).append("</b>")
        post.handle?.let { append(" @").append(escape(it)) }
        post.at?.let { append(" · <time>").append(time.format(it)).append("</time>") }
        append("</header>")
        post.title?.let { append("<h2>").append(escape(it)).append("</h2>") }
        if (post.text.isNotEmpty()) append("<p>").append(escape(post.text).replace("\n", "<br>")).append("</p>")
        post.pictures.forEach { picture ->
            val src = escape(picture.shown)
            if (picture.video) append("<p><a href=\"$src\">▶ $src</a></p>") else append("<img src=\"$src\" alt=\"\">")
        }
        post.quoted?.let { append(htmlPost(it, quoted = true)) }
        post.url?.let { append("<p class=\"link\"><a href=\"").append(escape(it)).append("\">").append(escape(it)).append("</a></p>") }
        post.note?.let { append("<aside>").append(escape(it).replace("\n", "<br>")).append("</aside>") }
        append(if (quoted) "</blockquote>" else "</article>")
    }

    private fun plainHeader(post: ExportedPost): String = listOfNotNull(post.authorName, post.handle?.let { "@$it" }).joinToString(" ")

    internal fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    /** A file name for [base]: what file systems refuse taken out, never empty. */
    fun fileName(base: String, extension: String): String {
        val cleaned = base.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ").replace(Regex("\\s+"), " ").trim().take(80).trim('.', ' ')
        return cleaned.ifEmpty { "xta" } + "." + extension
    }

    private const val STYLE = "body{font:16px/1.5 system-ui,sans-serif;max-width:42rem;margin:2rem auto;padding:0 1rem;color:#111}" +
        "article{border-bottom:1px solid #ccc;padding:1rem 0;break-inside:avoid}header{color:#555;margin-bottom:.5rem}" +
        "img{max-width:100%;display:block;margin:.5rem 0}blockquote{border-left:3px solid #ccc;margin:.5rem 0;padding-left:1rem}" +
        "aside{background:#f4f4f4;padding:.5rem 1rem;border-radius:4px}.link{font-size:.85rem;word-break:break-all}a{color:#1d4ed8}" +
        "@media print{body{margin:0;max-width:none}a{color:inherit}}"
}
