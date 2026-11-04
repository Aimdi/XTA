package com.aimdi.xtanative.plugins.deepmarks

import com.aimdi.xtanative.networks.deepmarks.DeepmarksClient
import com.aimdi.xtanative.store.settings.DeepmarksPrefs
import com.aimdi.xtanative.ui.plugins.ConnectionState
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginSharedPost

/**
 * What the Deepmarks plugin keeps while it runs: the reader's account (its
 * API base and API key) and their Nostr signing key, in the app's settings
 * under XTA's keys, both keys among the secrets no export, backup or log
 * carries, which uninstalling erases with the rest; and the one write it
 * makes, a bookmark of a post's link, signed here and handed to Deepmarks.
 */
internal class DeepmarksApp(val host: PluginHost) {
    private val client = DeepmarksClient(host.http)

    /** "Save to Deepmarks": [post]'s link and title as a signed bookmark, and the notice saying how that went. */
    suspend fun save(post: PluginSharedPost) {
        host.tell(DeepmarksSaves.save(client, host.settings.current(), post.url, post.title).get(host.language))
    }

    /** Test connection, for the fields as they are typed, in [language]. */
    suspend fun test(apiKey: String, secret: String, apiBase: String, language: String): ConnectionState {
        val (status, words) = DeepmarksSaves.test(client, apiBase.trim(), apiKey.trim(), secret.trim())
        return ConnectionState(status, words.get(language))
    }

    /** The fields kept, trimmed, as XTA's Save keeps them. */
    suspend fun keep(apiKey: String, secret: String, apiBase: String) {
        host.settings.tryUpdate {
            it.with(DeepmarksPrefs.apiKey, apiKey.trim()).with(DeepmarksPrefs.secretKey, secret.trim()).with(DeepmarksPrefs.apiBase, apiBase.trim())
        }
    }
}
