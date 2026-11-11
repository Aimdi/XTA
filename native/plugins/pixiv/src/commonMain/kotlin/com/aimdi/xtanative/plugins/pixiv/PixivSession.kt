package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivAuthUser
import com.aimdi.xtanative.store.settings.PixivPrefs
import kotlinx.coroutines.CancellationException

/**
 * Signing in, switching and signing out, as XTA does them: before another
 * account takes over, the one in use keeps its newest token among the stored
 * ones. The lists read for an account are dropped when it changes
 * ([PixivHomeModel] follows [PixivSessionKey]).
 */
internal object PixivSession {
    /** Trades a sign-in's code for tokens and keeps the account. */
    suspend fun signIn(runtime: PixivRuntime, code: String, verifier: String): PixivAuthUser {
        val tokens = runtime.client.exchangeCode(code, verifier)
        runtime.settings.update(PixivAccounts::keepingActive)
        val user = runtime.client.applyLoginTokens(tokens)
        runtime.accountChanged()
        runtime.settings.update { PixivAccounts.remembered(it, user) }
        return user
    }

    fun switchTo(runtime: PixivRuntime, account: PixivAccount) {
        runtime.settings.update(PixivAccounts::keepingActive)
        val known = PixivAccounts.of(runtime.settings.now).firstOrNull { it.userId == account.userId } ?: account
        runtime.client.switchTo(known.userId, known.refreshToken, known.isPremium)
        runtime.accountChanged()
    }

    /** Puts a typed or pasted token in use: it may be another account's, so it is a switch. */
    fun useToken(runtime: PixivRuntime, token: String) {
        if (token == runtime.settings[PixivPrefs.refreshToken].trim()) return
        runtime.settings.update(PixivAccounts::keepingActive)
        runtime.client.useRefreshToken(token)
        runtime.accountChanged()
    }

    fun remove(runtime: PixivRuntime, account: PixivAccount) = runtime.settings.update { PixivAccounts.without(it, account.userId) }

    /** Signs the account in use out of this device; another stored one takes over when there is one, and is answered. */
    fun signOut(runtime: PixivRuntime): PixivAccount? {
        val id = runtime.settings[PixivPrefs.userId]
        if (id > 0) runtime.settings.update { PixivAccounts.without(it, id) }
        val next = PixivAccounts.of(runtime.settings.now).firstOrNull()
        if (next == null) runtime.client.signOut() else runtime.client.switchTo(next.userId, next.refreshToken, next.isPremium)
        runtime.accountChanged()
        return next
    }

    /** Checks the token in use and keeps whose it is; null when it does not work. */
    suspend fun verified(runtime: PixivRuntime): PixivAuthUser? = tested(runtime).getOrNull()

    /** The token in use, checked: whose it is, kept; or why Pixiv would not say (no connection, a refused token, a limit). */
    suspend fun tested(runtime: PixivRuntime): Result<PixivAuthUser> = try {
        Result.success(runtime.client.verify().also { user -> runtime.settings.update { PixivAccounts.remembered(it, user) } })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
