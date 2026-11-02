package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import kotlinx.serialization.json.JsonObject

/**
 * Reddit's JSON: the API's answers with an account, and the public `.json`
 * pages where a host still serves them. A listing is `{data: {children,
 * after}}`, a thread `[postListing, commentsListing]` (XTA's `RedditPost.fromChild`,
 * `reddit_comments_json.dart` and `reddit_search_json.dart`).
 */
internal object RedditJson {
    /** The widest a picture is ever drawn, full bleed on the largest phone XTA runs on: wider copies are bytes nobody sees. */
    const val DISPLAY_WIDTH = 1080

    /** A listing's posts and where the next page starts, or null when the answer is no listing at all. */
    fun listing(root: J): Page<RedditPost>? {
        val data = root["data"]
        if (data.raw !is JsonObject) return null
        return Page(data["children"].list.mapNotNull(::post), data["after"].string?.ifEmpty { null })
    }

    /** One `t3` child of a listing, or null for any other child and for a post without an id or a title. */
    fun post(child: J): RedditPost? {
        if (child["kind"].string != "t3") return null
        val data = child["data"]
        val id = data["id"].string?.ifEmpty { null } ?: return null
        val title = data["title"].string ?: return null
        val video = videoOf(data)
        return RedditPost(
            id = id,
            title = title,
            subreddit = data["subreddit"].string ?: "",
            permalink = data["permalink"].string ?: "/comments/$id",
            author = data["author"].string,
            score = data["score"].integer ?: 0,
            commentCount = data["num_comments"].integer ?: 0,
            createdAt = instantOfSeconds(data["created_utc"].double),
            url = data["url"].string,
            isSelf = data["is_self"].boolean == true,
            selfText = data["selftext"].string?.trim(),
            over18 = data["over_18"].boolean == true,
            spoiler = data["spoiler"].boolean == true,
            stickied = data["stickied"].boolean == true,
            thumbnail = data["thumbnail"].string,
            flair = data["link_flair_text"].string?.trim(),
            domain = data["domain"].string,
            galleryImages = galleryImagesOf(data),
            previewImage = previewImageOf(data),
            videoManifestUrl = unescape(video["dash_url"].string),
            videoFallbackUrl = unescape(video["fallback_url"].string),
            videoAspectRatio = aspectOf(video),
        )
    }

    /** `secure_media` on the post itself, or `media`; a crosspost carries it on the original. */
    private fun videoOf(data: J): J =
        listOf(data["secure_media"]["reddit_video"], data["media"]["reddit_video"]).firstOrNull { it.exists }
            ?: data["crosspost_parent_list"][0]["secure_media"]["reddit_video"]

    private fun aspectOf(video: J): Double? {
        val width = video["width"].double ?: return null
        val height = video["height"].double?.takeIf { it > 0 } ?: return null
        return width / height
    }

    /**
     * A gallery's files in the author's order: `media_metadata` holds them by
     * id and `gallery_data` the order. A crosspost sometimes brings the files
     * and no order; they are still worth showing in the order the map has.
     */
    private fun galleryImagesOf(data: J): List<String> {
        val metadata = data["media_metadata"]
        if (!metadata.exists) return emptyList()
        val ordered = data["gallery_data"]["items"].list.mapNotNull { it["media_id"].string }
        return ordered.ifEmpty { metadata.keys.toList() }.mapNotNull { galleryFileOf(metadata[it]) }
    }

    /**
     * One gallery picture at a size a phone can use: `p` holds Reddit's scaled
     * copies and `s` the upload, routinely 4000px wide. An animation keeps its
     * source, since its `p` is a still frame of it.
     */
    private fun galleryFileOf(item: J): String? {
        val gif = item["s"]["gif"].string
        return if (gif != null || item["e"].string == "AnimatedImage") {
            unescape(gif ?: item["s"]["u"].string)
        } else {
            unescape(scaledVariant(item["p"]) ?: item["s"]["u"].string)
        }
    }

    /** Reddit's preview, the scaled copy nearest the screen: it is only ever a banner or a poster frame. */
    private fun previewImageOf(data: J): String? {
        val image = data["preview"]["images"][0]
        return unescape(scaledVariant(image["resolutions"]) ?: image["source"]["url"].string)
    }

    /** The widest of [variants] that still fits [DISPLAY_WIDTH]. A preview spells them `url`/`width`, a gallery `u`/`x`. */
    private fun scaledVariant(variants: J): String? = variants.list
        .mapNotNull { variant ->
            val width = variant["width"].integer ?: variant["x"].integer
            val url = variant["url"].string ?: variant["u"].string
            if (width == null || url == null || width <= 0 || width > DISPLAY_WIDTH) null else width to url
        }
        .maxByOrNull { it.first }?.second

    /** Reddit escapes media URLs inside its own JSON (`&amp;`), and its CDN rejects them so. */
    private fun unescape(url: String?): String? = url?.replace("&amp;", "&")

    /** A thread's comments, with what the post itself says, from `[postListing, commentsListing]`. */
    fun thread(root: J, postPermalink: String? = null): RedditThread {
        val post = post(root[0]["data"]["children"][0])
        return RedditThread(
            comments = comments(root[1], postPermalink ?: post?.permalink),
            selfText = post?.selfText,
            postUrl = post?.url,
            postImages = post?.galleryImages.orEmpty(),
        )
    }

    /** The comments and stubs of a comment listing; none when its shape is wrong. */
    fun comments(listing: J, parentPermalink: String? = null): List<RedditComment> =
        listing["data"]["children"].list.mapNotNull { comment(it, parentPermalink) }

    /** One `t1` comment or `more` stub; null for any other child, or a comment with neither words nor media. */
    fun comment(child: J, parentPermalink: String? = null): RedditComment? = when (child["kind"].string) {
        "more" -> moreStub(child["data"], parentPermalink)
        "t1" -> t1(child["data"])
        else -> null
    }

    private fun t1(data: J): RedditComment? {
        val id = data["id"].string?.ifEmpty { null } ?: return null
        val content = RedditCommentContent.ofMarkdown(data["body"].string ?: "")
        if (content.body.isEmpty() && content.mediaUrls.isEmpty()) return null
        val permalink = data["permalink"].string
        return RedditComment(
            id = id,
            author = data["author"].string,
            body = content.body,
            score = data["score"].integer,
            createdAt = instantOfSeconds(data["created_utc"].double),
            isSubmitter = data["is_submitter"].boolean ?: false,
            authorFlair = data["author_flair_text"].string?.trim()?.ifEmpty { null },
            mediaUrls = content.mediaUrls,
            // Leaf comments send `replies: ""` rather than an empty listing.
            replies = if (data["replies"].string != null) emptyList() else comments(data["replies"], permalink),
            permalink = permalink,
        )
    }

    /** Held-back replies live on their parent's page, so that is where the stub points: without it the row did nothing. */
    private fun moreStub(data: J, parentPermalink: String?) = RedditComment(
        id = data["id"].string ?: data["children"][0].string ?: "more",
        body = "",
        permalink = parentPermalink,
        moreCount = data["count"].integer ?: -1,
    )

    /** Posts of a search listing. */
    fun searchPosts(root: J): List<RedditPost> = root["data"]["children"].list.mapNotNull(::post)

    /** Communities of a search listing (`t5` children), each once. */
    fun searchSubreddits(root: J): List<RedditSubredditResult> {
        val seen = HashSet<String>()
        return root["data"]["children"].list.filter { it["kind"].string == "t5" }.mapNotNull { child ->
            val data = child["data"]
            val name = (data["display_name"].string ?: data["display_name_prefixed"].string)?.let(::normaliseSubreddit)
            if (name == null || !seen.add(name.lowercase())) return@mapNotNull null
            RedditSubredditResult(
                name = name,
                subscribers = data["subscribers"].integer,
                description = data["public_description"].string?.trim()?.ifEmpty { null },
                iconUrl = RedditMedia.communityIconUrl(data["community_icon"].string, data["icon_img"].string),
            )
        }
    }

    /** Accounts of a search listing (`t2` children), each once. */
    fun searchUsers(root: J): List<RedditUserResult> {
        val seen = HashSet<String>()
        return root["data"]["children"].list.filter { it["kind"].string == "t2" }.mapNotNull { child ->
            val data = child["data"]
            val name = data["name"].string?.trim()
            if (name.isNullOrEmpty() || !seen.add(name.lowercase())) null
            else RedditUserResult(name, data["total_karma"].integer ?: data["link_karma"].integer)
        }
    }

    /** What `about.json` says about a community, named [fallbackName] when it does not say its own name. */
    fun about(data: J, fallbackName: String): RedditSubredditAbout {
        val publicDescription = data["public_description"].string?.trim()
        return RedditSubredditAbout(
            name = data["display_name"].string ?: fallbackName,
            title = data["title"].string?.trim(),
            description = if (publicDescription.isNullOrEmpty()) data["description"].string?.trim() else publicDescription,
            subscribers = data["subscribers"].integer,
            activeUsers = data["active_user_count"].integer,
            over18 = data["over18"].boolean ?: false,
            iconUrl = RedditMedia.communityIconUrl(data["community_icon"].string, data["icon_img"].string),
        )
    }
}
