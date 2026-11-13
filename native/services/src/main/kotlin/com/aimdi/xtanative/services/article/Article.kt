package com.aimdi.xtanative.services.article

import com.aimdi.xtanative.core.x.model.XMedia

/**
 * A long-form X article, as XTA's reader shows it (article.dart): the
 * title, the preview line, the cover picture and the body as blocks, with
 * the entities the blocks point at by key.
 */
data class Article(
    val title: String,
    val previewText: String,
    val blocks: List<ArticleBlock> = emptyList(),
    val entities: Map<Int, ArticleEntity> = emptyMap(),
    val coverUrl: String? = null,
) {
    /**
     * The number each ordered list item shows, by block: 1, 2, 3 within a
     * run of ordered items, starting again after any other block; null for
     * every block that is not an ordered item.
     */
    val listNumbers: List<Int?>
        get() = blocks.runningFold(0) { count, block -> if (block.type == ArticleBlockType.ORDERED_LIST_ITEM) count + 1 else 0 }
            .drop(1)
            .map { count -> count.takeIf { it > 0 } }

    /**
     * What the reader draws under [block]'s text: each entity its ranges
     * point at, in their order, except links, which live inside the text.
     */
    fun embedsOf(block: ArticleBlock): List<ArticleEntity> =
        block.entityKeys.mapNotNull { entities[it] }.filter { it !is ArticleEntity.Link }

    /** The whole text, block by block, for reading aloud or copying. */
    val plainText: String get() = (listOf(title) + blocks.map { it.text }).filter { it.isNotBlank() }.joinToString("\n\n")
}

/** How a block is laid out; X's draft.js names unknown to XTA read as [UNSTYLED]. */
enum class ArticleBlockType { UNSTYLED, HEADER_TWO, UNORDERED_LIST_ITEM, ORDERED_LIST_ITEM, BLOCKQUOTE, ATOMIC }

/**
 * One paragraph, heading, list item or quote. [spans] cut its text where
 * its styles or targets change; [entityKeys] are the keys of every entity
 * its ranges point at, as X lists them. An atomic block has no text: it is
 * the place of its entities (a picture, a video, a divider, code).
 */
data class ArticleBlock(val type: ArticleBlockType, val spans: List<ArticleSpan>, val entityKeys: List<Int> = emptyList()) {
    val text: String get() = spans.joinToString("") { it.text }
}

/** A run of a block's text with one look and at most one place it leads. */
data class ArticleSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val target: ArticleTarget? = null,
)

/** Where a span leads when tapped. */
sealed interface ArticleTarget {
    /** An entity of the article; a [ArticleEntity.Link] makes the span a link, anything else leaves it text. */
    data class Entity(val key: Int) : ArticleTarget

    /** A link X marked in the block's own data. */
    data class Url(val url: String) : ArticleTarget

    /** An @mention X marked in the block's own data. */
    data class Mention(val screenName: String) : ArticleTarget
}

/** Something an article's blocks point at. */
sealed interface ArticleEntity {
    /** Code, split into the language its fence names and the code itself. */
    data class Markdown(val language: String, val code: String) : ArticleEntity {
        companion object {
            /** A fenced block (```` ```lang ````) as its language and code; anything else is all code. */
            fun of(markdown: String): Markdown {
                val trimmed = markdown.trim()
                val newline = trimmed.indexOf('\n')
                if (!trimmed.startsWith("```") || newline == -1) return Markdown("", trimmed)
                val body = trimmed.substring(newline + 1)
                val code = if (body.endsWith("```")) body.dropLast(3).trimEnd() else body
                return Markdown(trimmed.substring(3, newline).trim(), code)
            }
        }
    }

    data class Image(val url: String) : ArticleEntity

    /** A video, with its MP4s and poster, and the shape X gave it (1 when it gave none). */
    data class Video(val media: XMedia, val aspectRatio: Double) : ArticleEntity {
        /** What plays: the best MP4, or the first stream when there is none (a live broadcast). */
        val streamUrl: String? get() = media.mp4s.firstOrNull { it.bitrate != null }?.url ?: media.variants.firstOrNull()?.url
    }

    data class Link(val url: String) : ArticleEntity {
        /** The address a tap opens: a link written without a scheme is https. */
        val openUrl: String get() = articleLinkUrl(url)
    }

    data object Divider : ArticleEntity
}

/** [url] as a tap opens it: X sometimes writes links without a scheme. */
fun articleLinkUrl(url: String): String = if (url.startsWith("https://") || url.startsWith("http://")) url else "https://$url"
