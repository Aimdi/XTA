package com.aimdi.xtanative.plugins.hackernews

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.hackernews.HnLink
import com.aimdi.xtanative.networks.hackernews.HnLinks
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import kotlinx.serialization.json.JsonElement

/**
 * A story as the app's shared rows draw it among other networks' posts (a
 * group's feed, Saved), and as Saved keeps it: XTA's link record under the
 * story's page (plugin_link_post.dart), its title and text as XTA joins
 * them, read back into the story it names.
 */
internal object HnShared {
    private const val SOURCE = "hackernews"

    fun archive(story: HnStory): PluginPostArchive =
        PluginPostArchive.link(SOURCE, story.hnUrl, story.author.orEmpty(), listOf(story.title, story.text).filter { it.isNotEmpty() }.joinToString("\n"))

    /** The story one of its records names, by its page: its title the record's first line, its text the rest; null for another record. */
    fun story(content: JsonElement): HnStory? {
        val root = J(content)
        if (root["xtaPlugin"].string != "link" || root["source"].string != SOURCE) return null
        val id = (HnLinks.parse(root["url"].string.orEmpty()) as? HnLink.Story)?.id ?: return null
        val words = root["text"].string.orEmpty()
        return HnStory(id, words.substringBefore('\n'), text = words.substringAfter('\n', ""), author = root["author"].string?.ifEmpty { null })
    }

    /**
     * [story] in the shared row, as hn_story_card.dart: its title, the site it
     * links to, its points and comments, its author and age. With [words], its
     * text too, as Saved shows a story's record.
     */
    fun post(story: HnStory, words: Boolean = false): NetworkPost {
        val author = story.author.orEmpty()
        return NetworkPost(
            id = "${story.id}",
            authorId = author,
            authorName = author,
            authorHandle = author,
            createdAt = story.createdAt,
            text = if (words && story.text.isNotEmpty()) listOf(TextRun.Plain(story.text)) else emptyList(),
            card = story.url?.let { LinkCard(it) },
            replies = story.commentCount.toLong(),
            likes = story.score.toLong(),
            url = story.hnUrl,
            title = story.title,
        )
    }
}
