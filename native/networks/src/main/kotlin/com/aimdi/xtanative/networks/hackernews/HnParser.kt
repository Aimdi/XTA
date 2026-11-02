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
    /** [raw] flattened to text: a blank line for each `<p>` and `<br>`, links as their text. */
    fun toText(raw: String?): String {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return ""
        return write(Jsoup.parseBodyFragment(source).body()).trim()
    }

    private fun write(node: Node): String = when (node) {
        is TextNode -> node.wholeText
        is Element -> (if (node.normalName() == "p" || node.normalName() == "br") "\n\n" else "") +
            node.childNodes().joinToString("") { write(it) }
        else -> ""
    }
}

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
        return HnStory(
            id = id,
            title = hit["title"].string.orEmpty(),
            url = hit["url"].string,
            text = HnHtml.toText(hit["story_text"].string ?: hit["text"].string),
            author = hit["author"].string,
            score = hit["points"].int ?: 0,
            commentCount = hit["num_comments"].int ?: 0,
            createdAt = seconds(hit["created_at_i"].long),
            type = typeFromTags(hit["_tags"]),
        )
    }

    /** An Algolia item: the story and its whole comment tree. */
    fun thread(item: J): HnThread = HnThread(
        HnStory(
            id = item["id"].long ?: 0,
            title = item["title"].string.orEmpty(),
            url = item["url"].string,
            text = HnHtml.toText(item["text"].string),
            author = item["author"].string,
            score = item["points"].int ?: 0,
            commentCount = countComments(item["children"]),
            createdAt = seconds(item["created_at_i"].long),
            type = item["type"].string ?: "story",
        ),
        comments(item["children"]),
    )

    /** A Firebase list of ids, such as `beststories`. */
    fun ids(list: J): List<Long> = list.list.mapNotNull { it.long }

    /** A Firebase item as a story; null when it is missing or is a comment. */
    fun firebaseStory(item: J, id: Long): HnStory? {
        if (!item.exists || item["type"].string == "comment") return null
        return HnStory(
            id = item["id"].long ?: id,
            title = item["title"].string.orEmpty(),
            url = item["url"].string,
            text = HnHtml.toText(item["text"].string),
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
        return HnUser(
            id = json["id"].string ?: id,
            karma = json["karma"].int ?: 0,
            about = HnHtml.toText(json["about"].string),
            createdAt = seconds(json["created"].long),
            submittedCount = json["submitted"].list.size,
        )
    }

    private fun comments(children: J): List<HnComment> = children.list.mapNotNull(::comment)

    private fun comment(item: J): HnComment? {
        val id = item["id"].long ?: return null
        return HnComment(
            id = id,
            author = item["author"].string,
            text = HnHtml.toText(item["text"].string),
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
