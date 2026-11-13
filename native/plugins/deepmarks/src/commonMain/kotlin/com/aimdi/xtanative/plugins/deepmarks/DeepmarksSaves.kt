package com.aimdi.xtanative.plugins.deepmarks

import com.aimdi.xtanative.networks.deepmarks.DeepmarksAccount
import com.aimdi.xtanative.networks.deepmarks.DeepmarksClient
import com.aimdi.xtanative.networks.deepmarks.DeepmarksProblem
import com.aimdi.xtanative.networks.deepmarks.Nostr
import com.aimdi.xtanative.store.settings.DeepmarksPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.ConnectionStatus
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.answeredOr
import java.time.Instant

/**
 * A post's link saved to the reader's Deepmarks, signed on this device, and
 * the settings' test of the keys, each said in XTA's words
 * (deepmarks_save.dart, deepmarks_settings_screen.dart).
 */
internal object DeepmarksSaves {
    /** Signs [url] under [title] with the key [settings] hold, at [now], and hands the event to Deepmarks; what XTA then says. */
    suspend fun save(client: DeepmarksClient, settings: Settings, url: String, title: String, now: Instant = Instant.now()): PluginText {
        val secret = settings[DeepmarksPrefs.secretKey]
        val account = DeepmarksAccount.of(settings[DeepmarksPrefs.apiBase], settings[DeepmarksPrefs.apiKey])
        if (account == null || secret.isBlank()) return Strings.not_configured
        return answeredOr({ problem(DeepmarksProblem.of(it)) }) {
            val event = Nostr.signWebBookmark(Nostr.normaliseSecretKey(secret), url, title, now = now)
            if (client.publishBookmark(account, event).reachedNoRelay) Strings.saved_no_relay else Strings.saved
        }
    }

    /**
     * Test connection: the signing key [secret] must read, then Deepmarks is
     * asked about [apiKey] at [apiBase], and the two must be one account's
     * when Deepmarks can tell.
     */
    suspend fun test(client: DeepmarksClient, apiBase: String, apiKey: String, secret: String): Pair<ConnectionStatus, PluginText> {
        val mine = publicKey(secret)
        if (secret.isNotBlank() && mine == null) return ConnectionStatus.FAILED to Strings.error_secret_key
        val account = DeepmarksAccount.of(apiBase, apiKey) ?: return ConnectionStatus.FAILED to Strings.not_configured
        return answeredOr({ ConnectionStatus.FAILED to problem(DeepmarksProblem.of(it)) }) { verdict(client.verify(account), mine) }
    }

    /** Whom [secret] signs as (its public key), or null for a key that does not read. */
    fun publicKey(secret: String): String? = try {
        Nostr.publicKey(Nostr.normaliseSecretKey(secret))
    } catch (_: Exception) {
        null
    }

    fun problem(problem: DeepmarksProblem): PluginText = when (problem) {
        DeepmarksProblem.NOT_CONFIGURED -> Strings.not_configured
        DeepmarksProblem.BAD_SECRET_KEY -> Strings.error_secret_key
        DeepmarksProblem.UNAUTHORIZED -> Strings.error_unauthorized
        DeepmarksProblem.NOT_LIFETIME_MEMBER -> Strings.error_lifetime
        DeepmarksProblem.KEY_MISMATCH -> Strings.error_key_mismatch
        DeepmarksProblem.REJECTED -> Strings.error_rejected
        DeepmarksProblem.RATE_LIMITED -> Strings.error_rate_limited
        DeepmarksProblem.BAD_SERVER -> Strings.error_server
        DeepmarksProblem.NETWORK -> Strings.error_network
    }

    /** What Deepmarks' [owner] of the API key says of the signing key [mine]: an account with no public bookmark cannot tell. */
    private fun verdict(owner: String?, mine: String?): Pair<ConnectionStatus, PluginText> = when {
        owner != null && mine != null && owner != mine -> ConnectionStatus.FAILED to Strings.error_key_mismatch
        owner == null -> ConnectionStatus.WARNING to Strings.test_ok_unverified
        else -> ConnectionStatus.OK to Strings.test_ok
    }
}
