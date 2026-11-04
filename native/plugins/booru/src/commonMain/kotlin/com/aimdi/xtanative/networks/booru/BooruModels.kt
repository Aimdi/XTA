package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.networks.Page
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A content rating, the same four on every engine. Danbooru sends g/s/q/e, where `s` is
 * Sensitive; Moebooru, e621 and Gelbooru 0.2 forks send s/q/e, where `s` is Safe (General).
 * [code] is the Danbooru-shaped letter XTA stores the reader's cap as.
 */
enum class BooruRating(val code: String) {
    GENERAL("g"),
    SENSITIVE("s"),
    QUESTIONABLE("q"),
    EXPLICIT("e"),
    ;

    /** The word Gelbooru spells the rating with. */
    val word: String get() = name.lowercase()

    /** Whether a post rated this is above the cap [max]. */
    fun exceeds(max: BooruRating): Boolean = ordinal > max.ordinal

    companion object {
        /** A stored cap: never engine-specific, so `s` is Sensitive. */
        fun parse(raw: String?): BooruRating? = when (raw?.trim()?.lowercase()) {
            "g", "general", "safe" -> GENERAL
            "s", "sensitive" -> SENSITIVE
            "q", "questionable" -> QUESTIONABLE
            "e", "explicit" -> EXPLICIT
            else -> null
        }

        /** A rating as [engine] sends it, its `s` read the engine's way. */
        fun parseWire(raw: String?, engine: BooruEngine): BooruRating? {
            if (raw.isNullOrEmpty()) return null
            val value = raw.trim().lowercase()
            return when (engine) {
                BooruEngine.DANBOORU -> parse(value)
                BooruEngine.MOEBOORU, BooruEngine.E621 -> safeLetters(value)
                // Classic Gelbooru forks mean Safe by `s`; newer Gelbooru also sends the word "sensitive".
                BooruEngine.GELBOORU_V2 -> if (value == "sensitive") SENSITIVE else safeLetters(value) ?: parse(value)
            }
        }

        private fun safeLetters(value: String): BooruRating? = when (value) {
            "s", "safe", "g", "general" -> GENERAL
            "q", "questionable" -> QUESTIONABLE
            "e", "explicit" -> EXPLICIT
            else -> null
        }
    }
}

/** The kinds of tag the hosts colour apart. Each engine numbers them its own way ([fromWire]). */
enum class BooruTagCategory {
    GENERAL,
    ARTIST,
    COPYRIGHT,
    CHARACTER,
    SPECIES,
    META,
    ;

    companion object {
        /** The kind [engine] means by [code]: e621 has species (5) and meta (7), a Moebooru circle (5) is artists. */
        fun fromWire(code: Int, engine: BooruEngine): BooruTagCategory? = when {
            code == 0 -> GENERAL
            code == 1 -> ARTIST
            code == 3 -> COPYRIGHT
            code == 4 -> CHARACTER
            engine == BooruEngine.E621 && code == 5 -> SPECIES
            engine == BooruEngine.E621 && code == 7 -> META
            engine == BooruEngine.MOEBOORU && code == 5 -> ARTIST
            engine == BooruEngine.MOEBOORU && code == 6 -> META
            code == 5 -> META
            else -> null
        }

        /** The kind named by a word, as Gelbooru forks and Moebooru's tag map send it; faults are notes about the file. */
        fun named(name: String?): BooruTagCategory? = when (name.orEmpty().lowercase()) {
            "general", "tag" -> GENERAL
            "artist", "circle" -> ARTIST
            "copyright" -> COPYRIGHT
            "character" -> CHARACTER
            "species" -> SPECIES
            "metadata", "meta", "faults", "lore" -> META
            else -> null
        }
    }
}

/** One still size the host keeps of a picture (Danbooru's `media_asset.variants`). */
data class BooruImageVariant(val url: String, val width: Int)

/**
 * A post on a booru: its files, tags and facts, whichever the host sent. [host] and [engine] are
 * the site it came from, so its details are asked there. Ids are only unique on one host ([key]).
 */
data class BooruPost(
    val id: String,
    val host: String,
    val engine: BooruEngine,
    val tags: List<String>,
    val rating: BooruRating?,
    val score: Int?,
    val width: Int,
    val height: Int,
    val previewUrl: String?,
    val sampleUrl: String?,
    val fileUrl: String?,
    val fileExt: String?,
    val source: String?,
    val createdAt: Instant?,
    /** When the post last changed (Gelbooru's `change`): Gelbooru 0.2 forks send no upload time besides. */
    val updatedAt: Instant? = null,
    /** Each tag's kind, when the post itself says (Danbooru, e621); other engines need [BooruClient.tagInfo]. */
    val tagCategories: Map<String, BooruTagCategory> = emptyMap(),
    val fileSize: Long? = null,
    val md5: String? = null,
    val favCount: Int? = null,
    val upScore: Int? = null,
    /** Down votes as a positive count, whatever sign the host sends. */
    val downScore: Int? = null,
    val parentId: String? = null,
    val hasChildren: Boolean = false,
    val uploader: String? = null,
    /** Still sizes of the picture, smallest first; empty where the host names none (all but Danbooru). */
    val variants: List<BooruImageVariant> = emptyList(),
) {
    /** Identity across hosts. */
    val key: String get() = "$host:$id"

    /** What feeds order the post by: when it went up, else when it last changed. */
    val feedDate: Instant? get() = createdAt ?: updatedAt

    val tagLine: String get() = tags.joinToString(" ")

    /** The preview, else a still sample or original. A video file never stands in: it cannot be decoded as a picture. */
    val thumbnailUrl: String
        get() {
            if (!previewUrl.isNullOrEmpty()) return previewUrl
            val fallback = sampleUrl ?: fileUrl ?: ""
            return if (isVideoUrl(fallback)) "" else fallback
        }

    /** The picture the viewer shows: the sample, else the original. A GIF's sample is a still, so a GIF shows its original. */
    val displayUrl: String
        get() = when {
            isGif && !fileUrl.isNullOrEmpty() -> fileUrl
            !sampleUrl.isNullOrEmpty() -> sampleUrl
            else -> fileUrl ?: previewUrl ?: ""
        }

    /**
     * The picture a grid tile shows: the sample, else the original, since the ~150 px preview
     * looks blurred across a phone's two-column tile. A video keeps its preview: its sample can be a webm.
     */
    val catalogUrl: String
        get() = when {
            isVideo -> thumbnailUrl
            !sampleUrl.isNullOrEmpty() -> sampleUrl
            !fileUrl.isNullOrEmpty() -> fileUrl
            else -> thumbnailUrl
        }

    /**
     * Whether the host gave a file to show. It withholds the file of a deleted post, or of one
     * banned or restricted for guests, and a Flash file is nothing a viewer opens.
     */
    val hasFile: Boolean get() = !isFlash && listOf(sampleUrl, fileUrl).any { !it.isNullOrEmpty() }

    /** The smallest still variant at least [pixelWidth] wide, else [catalogUrl]. */
    fun tileUrlFor(pixelWidth: Int): String = variants.firstOrNull { it.width >= pixelWidth }?.url ?: catalogUrl

    val aspectRatio: Double get() = if (width <= 0 || height <= 0) 1.0 else width.toDouble() / height

    val isVideo: Boolean get() = fileExt.orEmpty().lowercase() in VIDEO_EXTENSIONS || videoUrl != null

    /** A file a player opens: the original, else a sample. Danbooru keeps animations as a zip of frames sampled as webm. */
    val videoUrl: String? get() = listOfNotNull(fileUrl, sampleUrl).firstOrNull(::isVideoUrl)

    /** The original file, else the largest version the host gave. */
    val originalUrl: String get() = if (!fileUrl.isNullOrEmpty()) fileUrl else displayUrl

    /** Whether the post is in a parent/child set on its host. */
    val hasFamily: Boolean get() = parentId != null || hasChildren

    /** The post's own page on its host. */
    val hostPageUrl: String?
        get() {
            val base = BooruHosts.pageHost(host).toHttpUrlOrNull() ?: return null
            val page = base.newBuilder().query(null)
            return when (engine) {
                BooruEngine.DANBOORU, BooruEngine.E621 -> page.addPathSegments("posts").addPathSegment(id)
                BooruEngine.MOEBOORU -> page.addPathSegments("post/show").addPathSegment(id)
                BooruEngine.GELBOORU_V2 -> page.addPathSegment("index.php")
                    .addQueryParameter("page", "post").addQueryParameter("s", "view").addQueryParameter("id", id)
            }.build().toString()
        }

    private val isGif: Boolean get() = fileExt.orEmpty().lowercase() == "gif" || extensionOf(fileUrl.orEmpty()) == "gif"

    private val isFlash: Boolean get() = fileExt.orEmpty().lowercase() == "swf" || extensionOf(fileUrl.orEmpty()) == "swf"

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv")

        /** Whether [url] names a file a player opens rather than a picture. */
        fun isVideoUrl(url: String): Boolean = extensionOf(url) in VIDEO_EXTENSIONS
    }
}

/**
 * One page of posts. [posts] is what a feed shows; [allowed] also keeps, in the host's order, the
 * posts the reader's blacklist hid, so a feed can count them and put them back on request. Posts
 * over the rating cap, and those without a file, are in neither. [next] is the page that follows,
 * null at the end; [failedQueries] counts the queries of a merged page (Following) that failed.
 */
data class BooruPostPage(
    val posts: List<BooruPost>,
    val next: String?,
    val allowed: List<BooruPost> = posts,
    val failedQueries: Int = 0,
) {
    /** The posts the blacklist hid. */
    val hidden: List<BooruPost> get() = posts.map { it.key }.toSet().let { shown -> allowed.filterNot { it.key in shown } }

    /** The page as every network hands one out. */
    fun toPage(): Page<BooruPost> = Page(posts, next)
}

/** What a host says about a tag: its kind and how many posts carry it, each when it says. */
data class BooruTagInfo(val category: BooruTagCategory? = null, val postCount: Int? = null)

/** A tag offered while a search is typed. [alias] is the other name it was found through: an alias, a translation or the typo typed. */
data class BooruTagSuggestion(
    val name: String,
    val postCount: Int? = null,
    val category: BooruTagCategory? = null,
    val alias: String? = null,
)

/** A translation note: a box on the picture, in the picture's own pixels, and its text. */
data class BooruNote(val x: Int, val y: Int, val width: Int, val height: Int, val text: String)

/** A pool a post is in: a series or collection read in its own order ([BooruQuery.poolQuery]). */
data class BooruPool(val id: String, val name: String, val postCount: Int? = null)

/** What a host keeps about the artist behind an artist tag; [urls] are the links it still lists as active. */
data class BooruArtist(val name: String, val otherNames: List<String> = emptyList(), val urls: List<String> = emptyList())

/** A comment on a post, read only. [body] is DText or light HTML; [BooruText.plain] reads it. */
data class BooruComment(val id: String, val author: String?, val body: String, val createdAt: Instant?, val score: Int?)

/** A post a link names on a known booru, to be read from that booru ([BooruClient.postAt]). */
data class BooruPostRef(val host: String, val engine: BooruEngine, val id: String)

/** The newest posts of the followed tags and saved searches, merged; of the [asked] queries, [failed] did not load, the first because of [error]. */
data class BooruFollowedPosts(val posts: List<BooruPost>, val asked: Int, val failed: Int, val error: Throwable?)

private val AUTHORITY = Regex("^(?:[A-Za-z][A-Za-z0-9+.-]*:)?//[^/]*")

/** [url]'s path: what follows its scheme and host, without its query or fragment. */
internal fun pathOf(url: String): String = url.substringBefore('#').substringBefore('?').replaceFirst(AUTHORITY, "")

/** What follows the last dot of [url]'s path, lower case; empty when there is none. */
internal fun extensionOf(url: String): String {
    val path = pathOf(url)
    val dot = path.lastIndexOf('.')
    return if (dot < 0) "" else path.substring(dot + 1).lowercase()
}
