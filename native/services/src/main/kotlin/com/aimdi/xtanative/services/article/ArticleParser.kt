package com.aimdi.xtanative.services.article

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads an X article out of a post's JSON (article.dart,
 * article_block_parser.dart, entity_map_parser.dart). Nothing here throws:
 * a missing or reshaped field is an empty block, a skipped entity or a null
 * article.
 */
object ArticleParser {
    /**
     * The article a tweet result carries, under `article.article_results.result`
     * or `article.article`; null when it carries none.
     */
    fun fromTweet(result: J): Article? {
        val article = result["article"]["article_results"]["result"].takeIf { it.exists } ?: result["article"]["article"]
        return if (article.exists) fromGraphql(article) else null
    }

    /**
     * An article result. Entities are read whenever the content has an
     * entity list; XTA read them only when `media_entities` was there too,
     * which left the links of an article without pictures dead.
     */
    fun fromGraphql(article: J): Article {
        val content = article["content_state"]
        return Article(
            title = article["title"].string.orEmpty(),
            previewText = article["preview_text"].string.orEmpty(),
            blocks = content["blocks"].list.map(::block),
            entities = if (content["entityMap"].raw is JsonArray) entities(content["entityMap"], article["media_entities"]) else emptyMap(),
            coverUrl = article["cover_media"]["media_info"]["original_img_url"].string,
        )
    }

    /** The short form a saved post keeps of its article: title, preview and cover. */
    fun stored(article: Article): JsonObject = buildJsonObject {
        put("title", article.title)
        put("previewText", article.previewText)
        put("coverMediaUrl", article.coverUrl)
    }

    fun fromStored(json: J): Article = Article(
        title = json["title"].string.orEmpty(),
        previewText = json["previewText"].string.orEmpty(),
        coverUrl = json["coverMediaUrl"].string,
    )

    // ---- blocks ----

    /** One draft.js block: its type, its text cut into spans, and the keys its entity ranges name. */
    fun block(block: J): ArticleBlock {
        val text = block["text"].string.orEmpty()
        val type = blockType(block["type"].string ?: "unstyled")
        val entityRanges = block["entityRanges"].list
        val keys = entityRanges.mapNotNull { it["key"].int }
        if (type == ArticleBlockType.ATOMIC) return ArticleBlock(type, emptyList(), keys)
        val marks = styleMarks(text, block["inlineStyleRanges"].list) +
            entityMarks(text, entityRanges) +
            dataMarks(text, block["data"]["urls"].list) { ArticleTarget.Url(it) } +
            dataMarks(text, block["data"]["mentions"].list) { ArticleTarget.Mention(it) }
        return ArticleBlock(type, spans(text, marks), keys)
    }

    private fun blockType(name: String) = when (name) {
        "atomic" -> ArticleBlockType.ATOMIC
        "unordered-list-item" -> ArticleBlockType.UNORDERED_LIST_ITEM
        "ordered-list-item" -> ArticleBlockType.ORDERED_LIST_ITEM
        "header-two" -> ArticleBlockType.HEADER_TWO
        "blockquote" -> ArticleBlockType.BLOCKQUOTE
        else -> ArticleBlockType.UNSTYLED
    }

    /** A range of the text, in UTF-16 units, carrying a style or a target. */
    private data class Mark(val start: Int, val end: Int, val style: String? = null, val target: ArticleTarget? = null)

    /** X counts offsets in code points; Kotlin strings count UTF-16 units, two for an emoji. */
    private fun units(text: String, codePoints: Int): Int {
        if (codePoints < 0) return text.length
        var unit = 0
        var point = 0
        while (unit < text.length && point < codePoints) {
            unit += Character.charCount(text.codePointAt(unit))
            point++
        }
        return unit
    }

    private fun mark(text: String, from: Int, to: Int, style: String? = null, target: ArticleTarget? = null): Mark? {
        val start = units(text, from)
        val end = units(text, to)
        return if (start < 0 || end > text.length || start >= end) null else Mark(start, end, style, target)
    }

    private fun styleMarks(text: String, ranges: List<J>): List<Mark> = ranges.mapNotNull { range ->
        val offset = range["offset"].int ?: 0
        mark(text, offset, offset + (range["length"].int ?: 0), style = range["style"].string.orEmpty())
    }

    private fun entityMarks(text: String, ranges: List<J>): List<Mark> = ranges.mapNotNull { range ->
        val key = range["key"].int ?: return@mapNotNull null
        val offset = range["offset"].int ?: 0
        mark(text, offset, offset + (range["length"].int ?: 0), target = ArticleTarget.Entity(key))
    }

    private fun dataMarks(text: String, entries: List<J>, target: (String) -> ArticleTarget): List<Mark> = entries.mapNotNull { entry ->
        if (entry.keys.isEmpty()) return@mapNotNull null
        mark(text, entry["fromIndex"].int ?: 0, entry["toIndex"].int ?: 0, target = target(entry["text"].string.orEmpty()))
    }

    /**
     * The text cut at every place a mark starts or ends. A piece takes every
     * style over it; its target is the first entity over it, else the first
     * link, else the first mention, as XTA picks.
     */
    private fun spans(text: String, marks: List<Mark>): List<ArticleSpan> {
        if (text.isEmpty()) return listOf(ArticleSpan(""))
        if (marks.isEmpty()) return listOf(ArticleSpan(text))
        val cuts = (marks.flatMap { listOf(it.start, it.end) } + 0 + text.length).filter { it in 0..text.length }.distinct().sorted()
        return cuts.zipWithNext().map { (from, to) ->
            val over = marks.filter { from >= it.start && from < it.end }
            val styles = over.mapNotNull { it.style?.lowercase() }.toSet()
            val targets = over.mapNotNull { it.target }
            ArticleSpan(
                text = text.substring(from, to),
                bold = "bold" in styles,
                italic = "italic" in styles,
                underline = "underline" in styles,
                target = targets.firstOrNull { it is ArticleTarget.Entity }
                    ?: targets.firstOrNull { it is ArticleTarget.Url }
                    ?: targets.firstOrNull { it is ArticleTarget.Mention },
            )
        }
    }

    // ---- entities ----

    /**
     * The entity map: a list of `{key, value: {type, data}}`, or that list as
     * JSON text, or an object holding it under `entityMap`. [media] is the
     * article's `media_entities`, which `MEDIA` entities name by id.
     */
    fun entities(map: J, media: J): Map<Int, ArticleEntity> {
        val items = entityList(map)
        return items.mapNotNull { item ->
            val key = item["key"].int ?: return@mapNotNull null
            val value = item["value"]
            val data = value["data"].takeIf { it.raw is JsonObject } ?: return@mapNotNull null
            entity(value["type"].string.orEmpty(), data, media.list)?.let { key to it }
        }.toMap()
    }

    private fun entityList(map: J): List<J> {
        val decoded = map.string?.let(J::parse) ?: map
        return decoded["entityMap"].takeIf { it.exists }?.list ?: decoded.list
    }

    private fun entity(type: String, data: J, media: List<J>): ArticleEntity? = when (type) {
        "MARKDOWN" -> text(data["markdown"])?.let(ArticleEntity.Markdown::of)
        "MEDIA" -> mediaEntity(data, media)
        "LINK" -> text(data["url"])?.let(ArticleEntity::Link)
        "DIVIDER" -> ArticleEntity.Divider
        else -> null
    }

    /** A string as it is, anything else that is there as its JSON, as Dart's `toString` reads a field. */
    private fun text(value: J): String? = if (value.exists) value.string ?: value.raw.toString() else null

    /** The first item of a `MEDIA` entity, looked up among the article's media; a picture or a video. */
    private fun mediaEntity(data: J, media: List<J>): ArticleEntity? {
        val id = text(data["mediaItems"][0]["mediaId"])?.ifEmpty { null } ?: return null
        val info = media.firstOrNull { it["media_id"].string == id }?.get("media_info") ?: return null
        return when (info["__typename"].string) {
            "ApiImage" -> ArticleEntity.Image(info["original_img_url"].string.orEmpty())
            "ApiVideo" -> video(info)
            else -> null
        }
    }

    private fun video(info: J): ArticleEntity.Video {
        val numerator = info["aspect_ratio"]["numerator"].double
        val denominator = info["aspect_ratio"]["denominator"].double
        val media = XMedia(
            kind = XMedia.Kind.VIDEO,
            url = info["preview_image"]["original_img_url"].string.orEmpty(),
            width = numerator?.toInt(),
            height = denominator?.toInt(),
            variants = info["variants"].list.mapNotNull { variant ->
                XVideoVariant(
                    url = variant["url"].string ?: return@mapNotNull null,
                    bitrate = variant["bit_rate"].int,
                    contentType = variant["content_type"].string.orEmpty(),
                )
            },
        )
        return ArticleEntity.Video(media, (numerator ?: 1.0) / (denominator ?: 1.0))
    }
}
