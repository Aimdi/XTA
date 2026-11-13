package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.store.backup.XtaTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A post as XTA keeps one on the device (its `RedditPost.toJson` and
 * `fromSnapshot`): the saved list holds these, under XTA's keys, so a backup
 * reads the same in both apps. A row without its id, title, community or
 * permalink is no post.
 */
internal object RedditPostJson {
    fun encode(post: RedditPost): JsonObject = buildJsonObject {
        put("id", post.id)
        put("title", post.title)
        put("subreddit", post.subreddit)
        put("permalink", post.permalink)
        put("author", post.author)
        put("score", post.score)
        put("commentCount", post.commentCount)
        put("createdAt", post.createdAt?.let(XtaTime::iso))
        put("url", post.url)
        put("isSelf", post.isSelf)
        put("selfText", post.selfText)
        put("over18", post.over18)
        put("spoiler", post.spoiler)
        put("stickied", post.stickied)
        put("thumbnail", post.thumbnail)
        put("flair", post.flair)
        put("domain", post.domain)
        put("galleryImages", JsonArray(post.galleryImages.map(::JsonPrimitive)))
        put("previewImage", post.previewImage)
        put("videoDashUrl", post.videoManifestUrl)
        put("videoFallbackUrl", post.videoFallbackUrl)
        put("videoAspectRatio", post.videoAspectRatio)
    }

    fun decode(row: J): RedditPost? {
        return RedditPost(
            id = row["id"].string ?: return null,
            title = row["title"].string ?: return null,
            subreddit = row["subreddit"].string ?: return null,
            permalink = row["permalink"].string ?: return null,
            author = row["author"].string,
            score = row["score"].long?.toInt() ?: row["score"].double?.toInt() ?: 0,
            commentCount = row["commentCount"].long?.toInt() ?: row["commentCount"].double?.toInt() ?: 0,
            createdAt = XtaTime.parse(row["createdAt"].string),
            url = row["url"].string,
            isSelf = row["isSelf"].boolean == true,
            selfText = row["selfText"].string,
            over18 = row["over18"].boolean == true,
            spoiler = row["spoiler"].boolean == true,
            stickied = row["stickied"].boolean == true,
            thumbnail = row["thumbnail"].string,
            flair = row["flair"].string,
            domain = row["domain"].string,
            galleryImages = row["galleryImages"].list.mapNotNull { it.string },
            previewImage = row["previewImage"].string,
            videoManifestUrl = row["videoDashUrl"].string,
            videoFallbackUrl = row["videoFallbackUrl"].string,
            videoAspectRatio = row["videoAspectRatio"].double,
        )
    }

    /** The posts of a saved list; a list that does not read is none. */
    fun list(text: String?): List<RedditPost> = J.parse(text.orEmpty()).list.mapNotNull(::decode)

    fun listText(posts: List<RedditPost>): String = JsonArray(posts.map(::encode)).toString()
}
