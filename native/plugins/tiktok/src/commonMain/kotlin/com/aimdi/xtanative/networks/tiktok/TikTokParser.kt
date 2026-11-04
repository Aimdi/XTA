package com.aimdi.xtanative.networks.tiktok

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * TikTok's pages and answers turned into accounts and videos, as XTA's
 * tiktok_parse.dart reads them: the JSON a profile or video page carries
 * for TikTok's web app, the unsigned creator video list, the discover page
 * and the search suggestions. Nothing here throws: what is missing is null
 * or empty, and a video without an id or an author is left out.
 */
object TikTokParser {
    private const val REHYDRATION = "__UNIVERSAL_DATA_FOR_REHYDRATION__"
    private val handlePattern = Regex("^[A-Za-z0-9._]{2,24}$")
    private val quality = Regex("(\\d+p)", RegexOption.IGNORE_CASE)

    /** TikTok's short-link hosts: a link there names no account until it is followed. */
    private val shortLinkHosts = setOf("vm.tiktok.com", "vt.tiktok.com")

    /**
     * The username [raw] names, lowercased: "@name", "name", or a tiktok.com
     * link to the account or one of its videos. Null for a short link or
     * text that cannot be a username.
     */
    fun handle(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (value.startsWith("@")) return validHandle(value.drop(1).trim())
        val url = (if ("://" in value) value else "https://$value").toHttpUrlOrNull()
        return when {
            url == null || !isTikTok(url.host) -> if ('/' in value) null else validHandle(value)
            url.host in shortLinkHosts -> null
            else -> url.pathSegments.firstOrNull().orEmpty().trim().takeIf { it.startsWith("@") }?.let { validHandle(it.drop(1)) }
        }
    }

    /**
     * The `__DEFAULT_SCOPE__` of the JSON a TikTok page carries for its web
     * app (the `__UNIVERSAL_DATA_FOR_REHYDRATION__` script), its HTML
     * entities undone; null when the page has none.
     */
    fun universalScope(html: String): J? {
        val script = Jsoup.parse(html).selectFirst("script#$REHYDRATION") ?: return null
        val json = J.parse(Parser.unescapeEntities(script.data(), false))
        return if (json.raw is JsonObject) json["__DEFAULT_SCOPE__"] else null
    }

    /** The account a profile page (`/@name`) describes; null when it names none. */
    fun profileHtml(html: String): TikTokProfile? = universalScope(html)?.let { profile(it["webapp.user-detail"]["userInfo"]) }

    /** The account in a `userInfo`: its `user` and its `stats`. */
    fun profile(userInfo: J): TikTokProfile? {
        val user = userInfo["user"]
        val uniqueId = user["uniqueId"].string.orEmpty().trim()
        val secUid = user["secUid"].string.orEmpty().trim()
        if (uniqueId.isEmpty() || secUid.isEmpty()) return null
        val stats = if (userInfo["stats"].exists) userInfo["stats"] else user["stats"]
        return TikTokProfile(
            id = user["id"].string ?: uniqueId,
            secUid = secUid,
            uniqueId = uniqueId,
            nickname = user["nickname"].string ?: uniqueId,
            signature = user["signature"].string,
            avatarUrl = firstUrl(user["avatarLarger"], user["avatarMedium"], user["avatarThumb"]),
            privateAccount = user["privateAccount"].boolean ?: false,
            verified = user["verified"].boolean ?: false,
            followerCount = stats["followerCount"].long ?: 0,
            followingCount = stats["followingCount"].long ?: 0,
            videoCount = stats["videoCount"].long ?: 0,
            heartCount = stats["heartCount"].long ?: stats["diggCount"].long ?: 0,
        )
    }

    /** The video a video page (`/@name/video/id`) shows; null when it shows none. */
    fun videoHtml(html: String): TikTokPost? =
        universalScope(html)?.let { post(it["webapp.video-detail"]["itemInfo"]["itemStruct"]) }

    /**
     * A page of `api/creator/item_list`. Its cursor is TikTok's, else the
     * last video's time in milliseconds, which is what the list is paged by.
     */
    fun itemList(json: J): TikTokItemList {
        val posts = json["itemList"].list.mapNotNull(::post)
        val given = json["cursor"].string ?: json["cursor"].long?.toString()
        val cursor = given?.takeIf { it.isNotBlank() } ?: posts.lastOrNull()?.let { "${(it.createdAt ?: Instant.EPOCH).toEpochMilli()}" }
        return TikTokItemList(
            posts = posts,
            cursor = cursor,
            hasMore = json["hasMorePrevious"].boolean ?: json["hasMore"].boolean ?: false,
            statusCode = json["statusCode"].int,
        )
    }

    /** A video from a page or the creator list (an `itemStruct`); null without an id or an author. */
    fun post(item: J): TikTokPost? {
        val id = (item["id"].string ?: item["id"].long?.toString()).orEmpty().trim()
        val author = if (item["author"].exists) item["author"] else item["authorInfo"]
        val uniqueId = (author["uniqueId"].string ?: author["author"].string).orEmpty().trim()
        if (id.isEmpty() || uniqueId.isEmpty()) return null
        val video = item["video"]
        val stats = item["stats"]
        val duration = video["duration"].int ?: 0
        return TikTokPost(
            id = id,
            desc = item["desc"].string.orEmpty(),
            createdAt = epoch(item["createTime"].long),
            author = TikTokAuthor(
                uniqueId = uniqueId,
                nickname = author["nickname"].string ?: uniqueId,
                secUid = author["secUid"].string ?: author["authorSecId"].string,
                avatarUrl = firstUrl(author["avatarLarger"], author["avatarMedium"], author["avatarThumb"]),
                verified = author["verified"].boolean ?: false,
            ),
            coverUrl = coverOf(item, video),
            // TikTok gives seconds, and on some pages milliseconds.
            durationSeconds = if (duration > 1000) (duration / 1000.0).roundToInt() else duration,
            width = video["width"].int ?: 0,
            height = video["height"].int ?: 0,
            diggCount = stats["diggCount"].long ?: 0,
            commentCount = stats["commentCount"].long ?: 0,
            shareCount = stats["shareCount"].long ?: 0,
            playCount = stats["playCount"].long ?: 0,
            sources = videoSources(video),
        )
    }

    /**
     * Every file a video plays from, each once: its bitrate ladder with the
     * quality each step names, then `playAddr`, then `downloadAddr`. Links
     * back to tiktok.com itself are not files and are left out.
     */
    fun videoSources(video: J): List<TikTokVideoSource> {
        val ladder = video["bitrateInfo"].list.flatMap { step ->
            val address = if (step["PlayAddr"].exists) step["PlayAddr"] else step["playAddr"]
            val label = qualityOf(address["UrlKey"].string ?: step["GearName"].string)
            address["UrlList"].list.map { it.string to label }
        }
        val single = listOf(stringOrSrc(video["playAddr"]) to null, stringOrSrc(video["downloadAddr"]) to "download")
        return (ladder + single)
            .mapNotNull { (url, label) -> playable(url)?.let { TikTokVideoSource(it, label) } }
            .distinctBy { it.url }
    }

    /** The people on TikTok's discover page (`node/share/discover`), each once. */
    fun discoverUsers(json: J): List<TikTokSearchUser> =
        json["body"].list.flatMap { it["exploreList"].list }
            .mapNotNull { discoverUser(it["cardItem"]) }
            .distinctBy { it.uniqueId.lowercase() }

    /** A discover card that is a person (type 2); null for tags, music and the rest. */
    fun discoverUser(item: J): TikTokSearchUser? {
        if ((item["type"].int ?: 0) != 2) return null
        val uniqueId = handleOfLink(item["link"].string ?: item["subTitle"].string) ?: return null
        val extra = item["extraInfo"]
        return TikTokSearchUser(
            uniqueId = uniqueId,
            nickname = item["title"].string ?: uniqueId,
            avatarUrl = firstUrl(item["cover"]),
            signature = item["description"].string,
            verified = extra["verified"].boolean ?: false,
            followerCount = extra["fans"].long ?: extra["followerCount"].long ?: 0,
        )
    }

    /** The searches TikTok suggests: as you type (`sug_list`) and trending (`data`), each once. */
    fun suggestList(json: J): List<String> {
        val typed = json["sug_list"].list.map { it["content"].string ?: it["word_record"]["words_content"].string }
        val trending = json["data"].list.map { it["word"].string }
        return (typed + trending).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinctBy { it.lowercase() }
    }

    private fun coverOf(item: J, video: J): String? =
        firstUrl(video["originCover"], video["cover"], video["dynamicCover"])
            ?: firstUrl(item["imagePost"]["cover"]["imageURL"]["urlList"][0])
            ?: firstUrl(item["imagePost"]["images"][0]["imageURL"]["urlList"][0])

    /** A URL TikTok sends as a string, a list of them, or an object holding one. */
    private fun stringOrSrc(value: J): String? =
        value.string
            ?: value.list.firstNotNullOfOrNull { it.string ?: it["src"].string ?: it["url"].string }
            ?: value["src"].string
            ?: value["url"].string
            ?: value["UrlList"][0].string
            ?: value["urlList"][0].string

    private fun firstUrl(first: J, second: J = J(null), third: J = J(null)): String? =
        listOf(first, second, third).firstNotNullOfOrNull { node -> stringOrSrc(node)?.takeIf { it.startsWith("http") } }

    private fun playable(url: String?): String? {
        if (url == null || !url.startsWith("http")) return null
        val host = url.toHttpUrlOrNull()?.host ?: return null
        return url.takeUnless { host == "www.tiktok.com" || host == "tiktok.com" }
    }

    private fun qualityOf(key: String?): String? = key?.let { quality.find(it)?.groupValues?.get(1) }

    private fun handleOfLink(raw: String?): String? = when {
        raw.isNullOrEmpty() -> null
        raw.startsWith("/") -> handle("https://www.tiktok.com$raw")
        else -> handle(raw)
    }

    private fun validHandle(handle: String): String? = handle.takeIf(handlePattern::matches)?.lowercase()

    private fun isTikTok(host: String): Boolean = host == "tiktok.com" || host.endsWith(".tiktok.com")

    /** TikTok's times are seconds, now and then milliseconds; nothing or zero is no time. */
    private fun epoch(value: Long?): Instant? = when {
        value == null || value <= 0 -> null
        value > 100_000_000_000 -> Instant.ofEpochMilli(value)
        else -> Instant.ofEpochSecond(value)
    }
}
