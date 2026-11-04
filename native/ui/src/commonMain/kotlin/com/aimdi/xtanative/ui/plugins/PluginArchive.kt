package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One of a plugin's posts as the app's archive keeps it (XTA's
 * PluginPostArchive): Saved, the note on it and the likes hold it under [id],
 * unique across networks ("bluesky:at://…", "reddit:t3_…"), with [authorId]
 * whose it is (what Saved's groups match on) and [content], the record Saved
 * reads back: XTA's own record of such a post where XTA has one
 * (`{"xtaPlugin": "bluesky", "post": …}`), else XTA's link record ([link]), so
 * a backup reads the same in XTA. It stays on the device: no network is told.
 */
class PluginPostArchive(val id: String, val authorId: String?, val content: JsonElement) {
    companion object {
        /**
         * XTA's link record of a post (plugin_link_post.dart), for a network
         * XTA keeps no record of its own for: the plugin [source] it came
         * from, where it lives, who wrote it, its words and its pictures,
         * under [id] ("<source>:<url>" unless given).
         */
        fun link(
            source: String,
            url: String,
            author: String,
            text: String,
            images: List<String> = emptyList(),
            id: String? = null,
            authorId: String? = null,
        ): PluginPostArchive {
            val content = buildJsonObject {
                put("archiveId", id)
                put("archiveUserId", authorId)
                put("xtaPlugin", "link")
                put("source", source)
                put("url", url)
                put("author", author)
                put("text", text)
                put("images", JsonArray(images.map(::JsonPrimitive)))
            }
            return PluginPostArchive(id ?: "$source:$url", authorId ?: author, content)
        }

        /**
         * The link record [content] is, kept from plugin [source] under the id
         * and author [link] gave it (XTA's PluginLinkPost.fromArchive): what a
         * plugin's row in Saved files and takes out. Null for another plugin's
         * record, another kind of record, or one without a web address.
         */
        fun ofLink(content: JsonElement, source: String): PluginPostArchive? {
            val root = J(content)
            val url = root["url"].string.orEmpty()
            val web = url.toHttpUrlOrNull()?.host?.isNotEmpty() == true
            if (root["xtaPlugin"].string != "link" || root["source"].string != source || !web) return null
            val author = root["author"].string.orEmpty()
            return PluginPostArchive(root["archiveId"].string ?: "$source:$url", root["archiveUserId"].string ?: author, content)
        }
    }
}

/**
 * One of a plugin's posts in Saved, as its own saved list shows it
 * ([PluginHost.savedPosts]): its record as the archive keeps it ([post]), and
 * the reader's note on it.
 */
class PluginSavedPost(val post: PluginPostArchive, val note: String? = null)

/**
 * The shared post menu a plugin opens over its screens ([PluginHost.openPostMenu],
 * XTA's plugin post actions): saving [post] on this device and taking it out,
 * the note on it, Add to group for its [author] (an account of the plugin, as
 * [PluginHost.addToGroup] takes it, with [follow]), Share link, Save to folder…,
 * Reposted by and Quotes where the plugin lists them ([reposts], [quotes]),
 * and Open in browser for [url]; then the running plugins' entries
 * ([XtaPlugin.postSheetActions]), which take the post by [url], by [title]
 * (the line it goes by elsewhere, [PluginSharedPost.titleOf]: none when not
 * given) and by the media of its record.
 */
class PluginPostMenu(
    val post: PluginPostArchive,
    val url: String,
    val author: String? = null,
    val reposts: (() -> Unit)? = null,
    val quotes: (() -> Unit)? = null,
    val title: String? = null,
    val follow: suspend () -> Unit = {},
)
