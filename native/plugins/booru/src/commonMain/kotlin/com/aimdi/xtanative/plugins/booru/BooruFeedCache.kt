package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruImageVariant
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.store.StoreDir
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A feed's first page as it was kept: its posts, its number among the host's pages, and whether more followed. */
internal data class KeptPage(val posts: List<BooruPost>, val page: Int, val hasMore: Boolean)

/**
 * The first page of a feed (Latest, Following), kept in the plugin's folder so
 * that a launch without a network still shows the last one (booru_feed_cache.dart).
 * Only a fallback: a page that loads replaces it, and one older than [KEEP]
 * is not shown. A key names the site, filters and query, so it is hashed into
 * a short file name.
 */
internal class BooruFeedCache(private val dir: StoreDir, private val clock: () -> Instant = Instant::now) {
    suspend fun read(key: String): KeptPage? = quietly {
        val root = J.parse(dir.file(fileName(key)).read() ?: return@quietly null)
        val saved = root["savedAt"].long?.let(Instant::ofEpochMilli) ?: return@quietly null
        if (Duration.between(saved, clock()) > KEEP) return@quietly null
        val posts = root["posts"].list.mapNotNull(BooruPostJson::decode)
        if (posts.isEmpty()) null else KeptPage(posts, root["page"].int ?: 1, root["hasMore"].boolean ?: true)
    }

    /** Keeps [page] under [key]; a failed write only means there is no copy. */
    suspend fun write(key: String, page: KeptPage) {
        quietly {
            val text = buildJsonObject {
                put("savedAt", clock().toEpochMilli())
                put("page", page.page)
                put("hasMore", page.hasMore)
                put("posts", JsonArray(page.posts.map(BooruPostJson::encode)))
            }.toString()
            dir.file(fileName(key)).write(text)
        }
    }

    private suspend fun <T> quietly(block: suspend () -> T?): T? = try {
        withContext(Dispatchers.Default) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    companion object {
        /** How long a kept page can still be shown. */
        val KEEP: Duration = Duration.ofDays(14)

        fun fileName(key: String): String =
            "feeds/" + MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json"
    }
}

/** A post as JSON, for the copy of a feed page kept on the device; [decode] reads it back, or nothing from a damaged one. */
internal object BooruPostJson {
    fun encode(post: BooruPost): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(post.id),
            "host" to JsonPrimitive(post.host),
            "engine" to JsonPrimitive(post.engine.id),
            "tags" to JsonArray(post.tags.map(::JsonPrimitive)),
            "rating" to text(post.rating?.code),
            "score" to number(post.score),
            "width" to JsonPrimitive(post.width),
            "height" to JsonPrimitive(post.height),
            "preview" to text(post.previewUrl),
            "sample" to text(post.sampleUrl),
            "file" to text(post.fileUrl),
            "ext" to text(post.fileExt),
            "source" to text(post.source),
            "created" to text(post.createdAt?.toString()),
            "updated" to text(post.updatedAt?.toString()),
            "kinds" to JsonObject(post.tagCategories.mapValues { JsonPrimitive(it.value.name.lowercase()) }),
            "size" to number(post.fileSize),
            "md5" to text(post.md5),
            "favs" to number(post.favCount),
            "up" to number(post.upScore),
            "down" to number(post.downScore),
            "parent" to text(post.parentId),
            "children" to JsonPrimitive(post.hasChildren),
            "uploader" to text(post.uploader),
            "variants" to JsonArray(post.variants.map { buildJsonObject { put("url", it.url); put("width", it.width) } }),
        ),
    )

    fun decode(json: J): BooruPost? {
        val id = json["id"].string ?: return null
        val host = json["host"].string ?: return null
        val engine = BooruEngine.parse(json["engine"].string) ?: return null
        return BooruPost(
            id = id,
            host = host,
            engine = engine,
            tags = json["tags"].list.mapNotNull { it.string },
            rating = BooruRating.parse(json["rating"].string),
            score = json["score"].int,
            width = json["width"].int ?: 0,
            height = json["height"].int ?: 0,
            previewUrl = json["preview"].string,
            sampleUrl = json["sample"].string,
            fileUrl = json["file"].string,
            fileExt = json["ext"].string,
            source = json["source"].string,
            createdAt = instant(json["created"].string),
            updatedAt = instant(json["updated"].string),
            tagCategories = kinds(json["kinds"]),
            fileSize = json["size"].long,
            md5 = json["md5"].string,
            favCount = json["favs"].int,
            upScore = json["up"].int,
            downScore = json["down"].int,
            parentId = json["parent"].string,
            hasChildren = json["children"].boolean ?: false,
            uploader = json["uploader"].string,
            variants = json["variants"].list.mapNotNull { variant ->
                val url = variant["url"].string
                val width = variant["width"].int
                if (url == null || width == null) null else BooruImageVariant(url, width)
            },
        )
    }

    private fun kinds(json: J): Map<String, BooruTagCategory> = json.keys.mapNotNull { tag ->
        BooruTagCategory.entries.firstOrNull { it.name.lowercase() == json[tag].string }?.let { tag to it }
    }.toMap()

    private fun instant(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private fun number(value: Number?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
}
