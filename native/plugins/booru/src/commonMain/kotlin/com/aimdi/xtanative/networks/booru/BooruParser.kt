package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.core.json.J
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** A host's answer to a tag lookup: JSON, or the XML Gelbooru 0.2 forks send for tags whatever `json=1` asks. */
sealed interface BooruTagReply {
    data class Json(val body: J) : BooruTagReply

    data class Xml(val text: String) : BooruTagReply
}

/** Reads posts and tag rows from every engine's answers. A field that is missing is null or empty, never a crash. */
object BooruParser {
    private val DANBOORU_TAG_FIELDS = listOf(
        "tag_string_artist", "tag_string_character", "tag_string_copyright", "tag_string_general", "tag_string_meta",
    )

    private val DANBOORU_CATEGORY_FIELDS = listOf(
        "tag_string_artist" to BooruTagCategory.ARTIST,
        "tag_string_copyright" to BooruTagCategory.COPYRIGHT,
        "tag_string_character" to BooruTagCategory.CHARACTER,
        "tag_string_general" to BooruTagCategory.GENERAL,
        "tag_string_meta" to BooruTagCategory.META,
    )

    private val E621_TAG_GROUPS = listOf("artist", "character", "copyright", "species", "general", "meta", "lore")

    private val E621_CATEGORY_GROUPS = listOf(
        "artist" to BooruTagCategory.ARTIST,
        "copyright" to BooruTagCategory.COPYRIGHT,
        "character" to BooruTagCategory.CHARACTER,
        "species" to BooruTagCategory.SPECIES,
        "general" to BooruTagCategory.GENERAL,
        "meta" to BooruTagCategory.META,
        "lore" to BooruTagCategory.META,
    )

    private val STILL_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private val GELBOORU_DATE = Regex("""^\w{3} (\w{3}) (\d{1,2}) (\d{2}):(\d{2}):(\d{2}) ([+-])(\d{2})(\d{2}) (\d{4})$""")

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /**
     * The posts of a post list from [host], spoken to as [engine]. Danbooru and Moebooru answer a
     * list; gelbooru.com wraps it in `post` and e621 in `posts`, while Gelbooru forks send it bare.
     */
    fun posts(root: J, engine: BooruEngine, host: String): List<BooruPost> {
        val list = when (engine) {
            BooruEngine.DANBOORU, BooruEngine.MOEBOORU -> root
            BooruEngine.GELBOORU_V2 -> root["post"].takeIf { it.exists } ?: root
            BooruEngine.E621 -> root["posts"].takeIf { it.exists } ?: root
        }
        if (list.raw !is JsonArray) return emptyList()
        return list.list.mapNotNull { if (engine == BooruEngine.E621) e621Post(it, host) else post(it, engine, host) }
    }

    /** Tag rows with their kinds and counts, from JSON or from a Gelbooru fork's XML. */
    fun tagSuggestions(reply: BooruTagReply, engine: BooruEngine): List<BooruTagSuggestion> = when (reply) {
        is BooruTagReply.Xml -> xmlTags(reply.text, engine)
        is BooruTagReply.Json -> jsonTags(reply.body, engine)
    }

    /**
     * Danbooru's autocomplete rows: the tag in `value`, and in `antecedent` the other name that led
     * to it. Rows that are not tags (users, pools) are left out. Null when the answer is not a list
     * of rows, so that the tags are asked for another way.
     */
    fun autocomplete(root: J): List<BooruTagSuggestion>? {
        if (root.raw !is JsonArray) return null
        return root.list.mapNotNull(::autocompleteRow)
    }

    /** Gelbooru writes dates like Ruby's `to_s`: `Sat Oct 05 13:52:20 -0500 2024`. */
    fun gelbooruDate(raw: String): Instant? {
        val parts = GELBOORU_DATE.matchEntire(raw.trim())?.groupValues ?: return null
        val month = MONTHS.indexOf(parts[1].lowercase()) + 1
        if (month == 0) return null
        val local = runCatching {
            LocalDateTime.of(parts[9].toInt(), month, parts[2].toInt(), parts[3].toInt(), parts[4].toInt(), parts[5].toInt())
        }.getOrNull() ?: return null
        val offset = Duration.ofHours(parts[7].toLong()).plusMinutes(parts[8].toLong())
        val utc = local.toInstant(ZoneOffset.UTC)
        return if (parts[6] == "-") utc.plus(offset) else utc.minus(offset)
    }

    internal fun categoryOf(json: J, engine: BooruEngine): BooruTagCategory? {
        val code = json["category"].integer ?: json["type"].integer
        return if (code != null) BooruTagCategory.fromWire(code.toInt(), engine) else BooruTagCategory.named(json["type"].string)
    }

    private fun post(json: J, engine: BooruEngine, host: String): BooruPost? {
        val id = idOf(json) ?: return null
        val sample = json["large_file_url"].string ?: json["sample_url"].string
        val file = json["file_url"].string ?: composedFileUrl(json)
        val preview = json["preview_file_url"].string ?: json["preview_url"].string
            ?: composedThumbnailUrl(json).takeIf { BooruPost.isVideoUrl(file.orEmpty()) }
        return BooruPost(
            id = id,
            host = host,
            engine = engine,
            tags = tagsOf(json, engine),
            rating = BooruRating.parseWire(json["rating"].string, engine),
            score = json["score"].integer?.toInt(),
            width = (json["image_width"].integer ?: json["width"].integer ?: 0).toInt(),
            height = (json["image_height"].integer ?: json["height"].integer ?: 0).toInt(),
            previewUrl = absolute(host, preview),
            sampleUrl = absolute(host, sample),
            fileUrl = absolute(host, file),
            fileExt = json["file_ext"].string ?: extensionIn(file ?: sample ?: preview),
            source = json["source"].string,
            createdAt = createdAt(json),
            updatedAt = changedAt(json, engine),
            tagCategories = DANBOORU_CATEGORY_FIELDS.flatMap { (field, kind) -> splitTags(json[field].string).map { it to kind } }.toMap(),
            fileSize = positive(json["file_size"]),
            md5 = nonEmpty(json["md5"].string ?: json["hash"].string),
            favCount = json["fav_count"].integer?.toInt(),
            upScore = json["up_score"].integer?.toInt(),
            downScore = json["down_score"].integer?.let { abs(it).toInt() },
            parentId = positive(json["parent_id"])?.toString(),
            hasChildren = flag(json["has_children"]),
            uploader = nonEmpty(json["author"].string ?: json["owner"].string ?: json["uploader_name"].string),
            variants = stillVariants(json["media_asset"]["variants"]),
        )
    }

    private fun e621Post(json: J, host: String): BooruPost? {
        val id = idOf(json) ?: return null
        val file = json["file"]
        val tags = json["tags"]
        val score = json["score"]
        val relationships = json["relationships"]
        return BooruPost(
            id = id,
            host = host,
            engine = BooruEngine.E621,
            tags = E621_TAG_GROUPS.flatMap { group -> tags[group].list.mapNotNull { it.string } },
            rating = BooruRating.parseWire(json["rating"].string, BooruEngine.E621),
            score = (score["total"].integer ?: score.integer)?.toInt(),
            width = (file["width"].integer ?: 0).toInt(),
            height = (file["height"].integer ?: 0).toInt(),
            previewUrl = absolute(host, json["preview"]["url"].string),
            sampleUrl = absolute(host, json["sample"]["url"].string),
            fileUrl = absolute(host, file["url"].string),
            fileExt = file["ext"].string,
            source = json["sources"][0].string ?: json["source"].string,
            createdAt = createdAt(json),
            tagCategories = E621_CATEGORY_GROUPS.flatMap { (group, kind) -> tags[group].list.mapNotNull { it.string }.map { it to kind } }.toMap(),
            fileSize = positive(file["size"]),
            md5 = nonEmpty(file["md5"].string),
            favCount = json["fav_count"].integer?.toInt(),
            upScore = score["up"].integer?.toInt(),
            downScore = score["down"].integer?.let { abs(it).toInt() },
            parentId = positive(relationships["parent_id"])?.toString(),
            hasChildren = flag(relationships["has_children"]) || relationships["children"].list.isNotEmpty(),
            uploader = nonEmpty(json["uploader_name"].string),
        )
    }

    private fun jsonTags(root: J, engine: BooruEngine): List<BooruTagSuggestion> {
        val list = when (engine) {
            BooruEngine.GELBOORU_V2 -> root["tag"].takeIf { it.exists } ?: root
            BooruEngine.E621 -> root["tags"].takeIf { it.exists } ?: root
            else -> root
        }
        if (list.raw !is JsonArray) return emptyList()
        return list.list.mapNotNull { suggestionOf(it, engine) }
    }

    /** `<tag type="4" count="900" name="..."/>` rows, read as the JSON rows are. */
    private fun xmlTags(xml: String, engine: BooruEngine): List<BooruTagSuggestion> =
        Jsoup.parse(xml, "", Parser.xmlParser()).select("tag").mapNotNull { tag ->
            suggestionOf(J(JsonObject(tag.attributes().associate { it.key to JsonPrimitive(it.value) })), engine)
        }

    private fun suggestionOf(json: J, engine: BooruEngine): BooruTagSuggestion? {
        val name = json["name"].string ?: json["tag"].string
        if (name.isNullOrEmpty()) return null
        val count = json["post_count"].integer ?: json["count"].integer ?: json["posts"].integer
        return BooruTagSuggestion(tagName(name, engine), count?.toInt(), categoryOf(json, engine))
    }

    private fun autocompleteRow(row: J): BooruTagSuggestion? {
        val name = row["value"].string
        val isTag = row["type"].string?.startsWith("tag") ?: true
        if (name.isNullOrEmpty() || !isTag) return null
        val alias = row["antecedent"].string?.takeIf { it.isNotEmpty() && it != name }
        return BooruTagSuggestion(name, row["post_count"].integer?.toInt(), categoryOf(row, BooruEngine.DANBOORU), alias)
    }

    private fun idOf(json: J): String? = json["id"].integer?.toString() ?: json["id"].string?.takeIf { it.isNotEmpty() }

    /** Gelbooru sends tag names HTML-escaped: `girls&#039;_frontline`. */
    private fun tagName(name: String, engine: BooruEngine): String =
        if (engine == BooruEngine.GELBOORU_V2 && '&' in name) Parser.unescapeEntities(name, false) else name

    private fun tagsOf(json: J, engine: BooruEngine): List<String> {
        val tagString = json["tag_string"].string ?: json["tags"].string
        return when {
            !tagString.isNullOrEmpty() -> splitTags(tagString).map { tagName(it, engine) }
            engine == BooruEngine.DANBOORU -> DANBOORU_TAG_FIELDS.flatMap { splitTags(json[it].string) }
            else -> emptyList()
        }
    }

    private fun splitTags(raw: String?): List<String> = raw.orEmpty().split(WHITESPACE).filter { it.isNotEmpty() }

    /** Danbooru's still sizes of the picture, smallest first. The original is left out: tiles never fetch it. */
    private fun stillVariants(variants: J): List<BooruImageVariant> = variants.list
        .filter { it["type"].string != "original" && it["file_ext"].string?.lowercase() in STILL_EXTENSIONS }
        .mapNotNull { variant ->
            val url = variant["url"].string
            val width = variant["width"].integer?.toInt()
            if (url.isNullOrEmpty() || width == null || width <= 0) null else BooruImageVariant(url, width)
        }
        .sortedBy { it.width }

    /** When the post went up: an ISO date, Gelbooru's Ruby date, or seconds (milliseconds past 10^12) since 1970. */
    private fun createdAt(json: J): Instant? {
        json["created_at"].string?.let { text -> (BooruDates.parse(text) ?: gelbooruDate(text))?.let { return it } }
        val number = json["created_at"].integer ?: return null
        return when {
            number <= 0 -> null
            number > 1_000_000_000_000 -> Instant.ofEpochMilli(number)
            else -> Instant.ofEpochSecond(number)
        }
    }

    /**
     * When the post last changed: Gelbooru's `change`, in seconds. Moebooru's `change` is a change
     * sequence number, not a time; XTA read it as seconds there too, which dated posts in 1970.
     */
    private fun changedAt(json: J, engine: BooruEngine): Instant? =
        json["change"].integer?.takeIf { it > 0 && engine == BooruEngine.GELBOORU_V2 }?.let(Instant::ofEpochSecond)

    /**
     * Gelbooru 0.2 keeps a post's file under its `directory`, which some hosts send as a number:
     * Rule34 and Xbooru leave out `file_url` for guests and send only the directory and image.
     */
    private fun directoryOf(json: J): String? = (json["directory"].string ?: json["directory"].integer?.toString())?.ifEmpty { null }

    private fun composedFileUrl(json: J): String? {
        val directory = directoryOf(json) ?: return null
        val image = json["image"].string?.ifEmpty { null } ?: return null
        return "/images/$directory/$image"
    }

    /** Gelbooru 0.2's thumbnail path, for a video sent without a preview: the video cannot stand in for one. */
    private fun composedThumbnailUrl(json: J): String? {
        val directory = directoryOf(json) ?: return null
        val hash = json["hash"].string?.ifEmpty { null } ?: return null
        return "/thumbnails/$directory/thumbnail_$hash.jpg"
    }

    private fun extensionIn(url: String?): String? {
        val path = pathOf(url ?: return null)
        val dot = path.lastIndexOf('.')
        return if (dot < 0 || dot == path.length - 1) null else path.substring(dot + 1).lowercase()
    }

    /** [url] made absolute against [host]; `//cdn/x.jpg` is taken as https. */
    private fun absolute(host: String, url: String?): String? = when {
        url.isNullOrEmpty() -> null
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        else -> host.toHttpUrlOrNull()?.resolve(url)?.toString() ?: url
    }

    private fun nonEmpty(value: String?): String? = value?.trim()?.ifEmpty { null }

    /** Hosts send no parent, size or count as null, 0 or an empty string. */
    private fun positive(value: J): Long? = value.integer?.takeIf { it > 0 }

    /** Gelbooru sends booleans as the strings "true" and "false". */
    private fun flag(value: J): Boolean = value.boolean ?: (value.string?.lowercase() == "true")
}

/** A whole number as XTA's `Json.integer` reads one: a number, cut to a whole one, or a string of digits. */
internal val J.integer: Long? get() = long ?: (raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.toLong()

/** Dates as Dart's `DateTime.tryParse` reads them, which XTA uses: ISO 8601, with a space allowed for the `T`. */
internal object BooruDates {
    private val ISO = Regex(
        """^([+-]?\d{4,6})-?(\d\d)-?(\d\d)""" +
            """(?:[ T](\d\d)(?::?(\d\d)(?::?(\d\d)(?:[.,](\d+))?)?)?""" +
            """( ?[zZ]| ?([-+])(\d\d)(?::?(\d\d))?)?)?$""",
    )

    /** The instant [text] names; a time without a zone is the device's local time, as in Dart. Null for anything else. */
    fun parse(text: String): Instant? {
        val parts = ISO.matchEntire(text)?.groupValues ?: return null
        val local = runCatching {
            LocalDateTime.of(
                parts[1].toInt(), parts[2].toInt(), parts[3].toInt(),
                parts[4].toIntOrZero(), parts[5].toIntOrZero(), parts[6].toIntOrZero(),
                parts[7].take(6).padEnd(6, '0').toInt() * 1000,
            )
        }.getOrNull() ?: return null
        return when {
            parts[8].isEmpty() -> local.atZone(ZoneId.systemDefault()).toInstant()
            parts[9].isEmpty() -> local.toInstant(ZoneOffset.UTC)
            else -> {
                val minutes = parts[10].toLong() * 60 + parts[11].toIntOrZero()
                local.toInstant(ZoneOffset.UTC).minusSeconds((if (parts[9] == "-") -minutes else minutes) * 60)
            }
        }
    }

    private fun String.toIntOrZero(): Int = toIntOrNull() ?: 0
}
