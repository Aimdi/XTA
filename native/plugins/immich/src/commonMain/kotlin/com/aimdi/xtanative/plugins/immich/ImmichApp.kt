package com.aimdi.xtanative.plugins.immich

import com.aimdi.xtanative.networks.immich.ImmichClient
import com.aimdi.xtanative.networks.immich.ImmichHttp
import com.aimdi.xtanative.networks.immich.ImmichUploader
import com.aimdi.xtanative.networks.immich.OkHttpImmichHttp
import com.aimdi.xtanative.store.settings.ImmichPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.ui.plugins.ConnectionState
import com.aimdi.xtanative.ui.plugins.PluginFiledPost
import com.aimdi.xtanative.ui.plugins.PluginHost

/**
 * What the Immich plugin keeps while it runs: the reader's own server and
 * the upload options, in the app's settings under XTA's keys (the API key
 * among the secrets no export, backup or log carries, which uninstalling
 * erases with the rest), and the log of what was sent, in its own folder.
 * Its one write is a filed post's media sent to that server.
 */
internal class ImmichApp(val host: PluginHost, http: ImmichHttp = OkHttpImmichHttp()) {
    private val client = ImmichClient(http)
    val log = ImmichDeviceLog(host.store.file("uploads.json"))
    private val uploader = ImmichUploader(http, log)

    /** A post filed in a folder set to upload: its media sent, saying so meanwhile, then how it went (XTA's _uploadToImmich). */
    suspend fun filed(post: PluginFiledPost) {
        val language = host.language
        val report = host.working(Strings.uploading.get(language)) { ImmichSaves.upload(uploader, host.settings.current(), post) }
        ImmichSaves.notice(report.status)?.let { host.tell(it.get(language)) }
    }

    /** Test connection, for the fields as they are typed, in [language]. */
    suspend fun test(address: String, apiKey: String, language: String): ConnectionState {
        val (status, words) = ImmichSaves.test(client, address.trim(), apiKey.trim())
        return ConnectionState(status, words.get(language))
    }

    /** The fields kept, trimmed, as XTA's Save keeps them. */
    suspend fun keep(address: String, apiKey: String) {
        host.settings.tryUpdate { it.with(ImmichPrefs.serverUrl, address.trim()).with(ImmichPrefs.apiKey, apiKey.trim()) }
    }

    /** One of the upload options switched, kept at once as XTA's switches keep theirs. */
    suspend fun set(option: Pref<Boolean>, on: Boolean) {
        host.settings.tryUpdate { it.with(option, on) }
    }
}
