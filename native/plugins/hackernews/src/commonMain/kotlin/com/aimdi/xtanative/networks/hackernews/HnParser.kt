package com.aimdi.xtanative.networks.hackernews

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** HN text arrives as HTML (`<p>`, `<i>`, `<a>`): read as plain text, paragraphs kept. */
object HnHtml {
    private const val BASE = "https://news.ycombinator.com/"

    /** [raw] flattened to text: a blank line for each `<p>` and `<br>`, links as their text. */
    fun toText(raw: String?): String = read(raw).text

    /**
     * [raw] flattened as [toText] does, with where each link stands in the
     * text and where it leads: Hacker News shortens a long address in the
     * text, so the address itself is only in the link. A link within the
     * site (`item?id=…`) leads to news.ycombinator.com.
     */
    fun read(raw: String?): HnText {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return HnText()
        val out = StringBuilder()
        val links = mutableListOf<HnTextLink>()
        write(Jsoup.parseBodyFragment(source, BASE).body(), out, links)
        val lead = out.length - out.trimStart().length
        val text = out.trim().toString()
        return HnText(text, links.mapNotNull { it.within(lead, text.length) }.sortedBy { it.start })
    }

    private fun write(node: Node, out: StringBuilder, links: MutableList<HnTextLink>) {
        when (node) {
            is TextNode -> out.append(node.wholeText)
            is Element -> {
                if (node.normalName() == "p" || node.normalName() == "br") out.append("\n\n")
                val start = out.length
                node.childNodes().forEach { write(it, out, links) }
                val url = node.takeIf { it.normalName() == "a" }?.absUrl("href")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                if (url != null && out.length > start) links += HnTextLink(start, out.length, url)
            }
        }
    }
}

/** A link inside a text flattened by [HnHtml]: its characters from [start] until [end] lead to [url]. */
data class HnTextLink(val start: Int, val end: Int, val url: String) {
    /** Where it stands once [lead] characters are trimmed off a text [length] long; null when nothing of it is left. */
    internal fun within(lead: Int, length: Int): HnTextLink? {
        val from = (start - lead).coerceIn(0, length)
        val to = (end - lead).coerceIn(0, length)
        return if (to > from) HnTextLink(from, to, url) else null
    }
}

/** A text flattened by [HnHtml], and its links. */
data class HnText(val text: String = "", val links: List<HnTextLink> = emptyList())

/**
 * Hacker News' two APIs read into stories, comments and users: Algolia's
 * search (lists, search, threads) and Firebase's (Best, items, users).
 */
object HnParser {
    /** One Algolia page of [page], and the next page's number when Algolia has more. */
    fun searchPage(body: J, page: Int): Page<HnStory> {
        val pages = body["nbPages"].int ?: 0
        return Page(body["hits"].list.mapNotNull(::storyFromHit), next = if (page + 1 < pages) "${page + 1}" else null)
    }

    /** An Algolia search hit; null without an id. */
    fun storyFromHit(hit: J): HnStory? {
        val id = hit["objectID"].long ?: hit["story_id"].long ?: return null
        val body = HnHtml.read(hit["story_text"].string ?: hit["text"].string)
        return HnStory(
            id = id,
            title = hit["title"].string.orEmpty(),
            url = hit["url"].string,
            text = body.text,
            links = body.links,
            author = hit["author"].string,
            score = hit["points"].int ?: 0,
            commentCount = hit["num_comments"].int ?: 0,
            createdAt = seconds(hit["created_at_i"].long),
            type = typeFromTags(hit["_tags"]),
        )
    }

    /** An Algolia item: the story and its whole comment tree. */
    fun thread(item: J): HnThread {
        val body = HnHtml.read(item["text"].string)
        return HnThread(threadStory(item, body), comments(item["children"]))
    }

    private fun threadStory(item: J, body: HnText) = HnStory(
        id = item["id"].long ?: 0,
        title = item["title"].string.orEmpty(),
        url = item["url"].string,
        text = body.text,
        links = body.links,
        author = item["author"].string,
        score = item["points"].int ?: 0,
        commentCount = countComments(item["children"]),
        createdAt = seconds(item["created_at_i"].long),
        type = item["type"].string ?: "story",
    )

    /** A Firebase list of ids, such as `beststories`. */
    fun ids(list: J): List<Long> = list.list.mapNotNull { it.long }

    /** A Firebase item as a story; null when it is missing or is a comment. */
    fun firebaseStory(item: J, id: Long): HnStory? {
        if (!item.exists || item["type"].string == "comment") return null
        val body = HnHtml.read(item["text"].string)
        return HnStory(
            id = item["id"].long ?: id,
            title = item["title"].string.orEmpty(),
            url = item["url"].string,
            text = body.text,
            links = body.links,
            author = item["by"].string,
            score = item["score"].int ?: 0,
            commentCount = item["descendants"].int ?: 0,
            createdAt = seconds(item["time"].long),
            type = item["type"].string ?: "story",
        )
    }

    /** A Firebase user; null when Firebase has none by that id (it answers `null`). */
    fun user(json: J, id: String): HnUser? {
        if (!json.exists) return null
        val about = HnHtml.read(json["about"].string)
        return HnUser(
            id = json["id"].string ?: id,
            karma = json["karma"].int ?: 0,
            about = about.text,
            aboutLinks = about.links,
            createdAt = seconds(json["created"].long),
            submittedCount = json["submitted"].list.size,
        )
    }

    private fun comments(children: J): List<HnComment> = children.list.mapNotNull(::comment)

    private fun comment(item: J): HnComment? {
        val id = item["id"].long ?: return null
        val body = HnHtml.read(item["text"].string)
        return HnComment(
            id = id,
            author = item["author"].string,
            text = body.text,
            links = body.links,
            createdAt = seconds(item["created_at_i"].long),
            children = comments(item["children"]),
            deleted = item["author"].string == null || item["text"].string == null,
        )
    }

    private fun countComments(children: J): Int =
        children.list.filter { it["id"].long != null }.sumOf { 1 + countComments(it["children"]) }

    private fun typeFromTags(tags: J): String {
        val values = tags.list.mapNotNull { it.string }.toSet()
        return when {
            "job" in values -> "job"
            "ask_hn" in values -> "ask"
            "show_hn" in values -> "show"
            else -> "story"
        }
    }

    private fun seconds(value: Long?): Instant? = value?.let(Instant::ofEpochSecond)
}
