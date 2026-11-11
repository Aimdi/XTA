package com.aimdi.xtanative.plugins.karakeep

import com.aimdi.xtanative.networks.karakeep.KarakeepClient
import com.aimdi.xtanative.store.settings.KarakeepPrefs
import com.aimdi.xtanative.ui.plugins.ConnectionState
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginSharedPost

/**
 * What the Karakeep plugin keeps while it runs: the reader's own server,
 * named in the app's settings under XTA's keys (the key among the secrets no
 * export, backup or log carries, which uninstalling erases with the rest),
 * and the one write it makes there: a bookmark of a post's link.
 */
internal class KarakeepApp(val host: PluginHost) {
    private val client = KarakeepClient(host.http)

    /**
     * "Save to Karakeep": [post]'s link and title kept in the reader's
     * Karakeep, saying so while the server answers (a home server can be
     * slow), then the notice saying how that went.
     */
    suspend fun save(post: PluginSharedPost) {
        val language = host.language
        val said = host.working(Strings.saving.get(language)) { KarakeepSaves.save(client, host.settings.current(), post.url, post.title) }
        host.tell(said.get(language))
    }

    /** Test connection, for the fields as they are typed, in [language]. */
    suspend fun test(address: String, apiKey: String, language: String): ConnectionState {
        val (status, words) = KarakeepSaves.test(client, address.trim(), apiKey.trim())
        return ConnectionState(status, words.get(language))
    }

    /** The fields kept, trimmed, as XTA's Save keeps them. */
    suspend fun keep(address: String, apiKey: String) {
        host.settings.tryUpdate { it.with(KarakeepPrefs.serverUrl, address.trim()).with(KarakeepPrefs.apiKey, apiKey.trim()) }
    }
}
