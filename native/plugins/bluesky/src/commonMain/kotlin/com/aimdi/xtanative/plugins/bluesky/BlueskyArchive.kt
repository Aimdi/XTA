package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskySnapshot
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A Bluesky post as the app's archive keeps it, in XTA's record
 * (bluesky_archive.dart): `bluesky:<uri>`, its author's DID, and
 * `{"xtaPlugin": "bluesky", "post": …}` with the post as XTA keeps one, so
 * Saved, the likes and a backup read it the same in both apps.
 */
internal object BlueskyArchive {
    fun of(post: BlueskyPost): PluginPostArchive =
        PluginPostArchive("bluesky:" + post.uri, post.did.ifEmpty { null }, buildJsonObject { put("xtaPlugin", "bluesky"); put("post", BlueskySnapshot.json(post)) })

    /** The post a record holds; null for one of another network, or one without its `at://` URI. */
    fun post(content: JsonElement): BlueskyPost? {
        val root = J(content)
        if (root["xtaPlugin"].string != "bluesky") return null
        return BlueskySnapshot.post(root["post"])
    }
}
