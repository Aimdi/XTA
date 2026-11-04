package com.aimdi.xtanative.plugins.karakeep

import com.aimdi.xtanative.networks.karakeep.KarakeepClient
import com.aimdi.xtanative.networks.karakeep.KarakeepProblem
import com.aimdi.xtanative.networks.karakeep.KarakeepSaveOutcome
import com.aimdi.xtanative.networks.karakeep.KarakeepServer
import com.aimdi.xtanative.store.settings.KarakeepPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.ConnectionStatus
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.answeredOr

/**
 * A post's link saved in the reader's own Karakeep, and the settings' test
 * of the server, each said in XTA's words (karakeep_save.dart,
 * karakeep_settings_screen.dart): saved, already there, or what went wrong.
 */
internal object KarakeepSaves {
    /** Saves [url] under [title] where [settings] point; what XTA then says. */
    suspend fun save(client: KarakeepClient, settings: Settings, url: String, title: String): PluginText {
        val server = KarakeepServer.of(settings[KarakeepPrefs.serverUrl], settings[KarakeepPrefs.apiKey]) ?: return Strings.not_configured
        return answeredOr({ problem(KarakeepProblem.of(it)) }) {
            when (client.saveLink(server, url, title).outcome) {
                KarakeepSaveOutcome.SAVED -> Strings.saved
                KarakeepSaveOutcome.ALREADY_SAVED -> Strings.already_saved
            }
        }
    }

    /** Whether the server at [address] takes [apiKey], as Test connection says it. */
    suspend fun test(client: KarakeepClient, address: String, apiKey: String): Pair<ConnectionStatus, PluginText> {
        val server = KarakeepServer.of(address, apiKey) ?: return ConnectionStatus.FAILED to Strings.not_configured
        return answeredOr({ ConnectionStatus.FAILED to problem(KarakeepProblem.of(it)) }) {
            client.verify(server)
            ConnectionStatus.OK to Strings.test_ok
        }
    }

    fun problem(problem: KarakeepProblem): PluginText = when (problem) {
        KarakeepProblem.NOT_CONFIGURED -> Strings.not_configured
        KarakeepProblem.UNAUTHORIZED -> Strings.error_unauthorized
        KarakeepProblem.BAD_SERVER -> Strings.error_server_url
        KarakeepProblem.NETWORK -> Strings.error_network
        KarakeepProblem.SERVER -> Strings.error_generic
    }
}
