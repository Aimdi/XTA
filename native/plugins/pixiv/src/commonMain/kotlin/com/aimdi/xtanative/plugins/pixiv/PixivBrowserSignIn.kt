package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivAuthUser
import com.aimdi.xtanative.networks.pixiv.PixivLogin
import com.aimdi.xtanative.networks.pixiv.PixivPkce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where a sign-in in the reader's own browser stands, as its screen shows it. */
internal data class PixivBrowserSignInState(
    /** Pixiv's page was opened and its code is awaited: until [PixivBrowserSignIn.LIFETIME_MS] after. */
    val waiting: Boolean = false,
    /** A code is being traded for the account's tokens. */
    val trading: Boolean = false,
    /** How many sign-ins finished here: a sign-in screen opened before one closes once it grows. */
    val finished: Int = 0,
)

/** How handing a code back ended. */
internal sealed interface PixivCodeOutcome {
    data class SignedIn(val user: PixivAuthUser) : PixivCodeOutcome

    /** Pixiv refused the code, or could not be reached. */
    data class Failed(val error: Exception) : PixivCodeOutcome

    /** What was handed back holds no code. */
    data object NoCode : PixivCodeOutcome

    /** No sign-in waits for a code: none was started here, or it was too long ago. */
    data object Expired : PixivCodeOutcome

    /** Another code is being traded already. */
    data object Busy : PixivCodeOutcome
}

/**
 * Signing in on the desktop, which has no web view: Pixiv's own page opens
 * in the reader's browser ([start]), and the one-time code its redirect
 * carries comes back here, either as the `pixiv://account/login?code=…`
 * address the browser hands the app or pasted by the reader ([handBack]).
 * The PKCE verifier stays in memory alone, for [LIFETIME_MS], and is used
 * once: a code only signs in the sign-in that was started here.
 */
internal class PixivBrowserSignIn(
    private val now: () -> Long = System::currentTimeMillis,
    private val fresh: () -> PixivPkce = { PixivLogin.pkce() },
) {
    private class Pending(val verifier: String, val startedAt: Long)

    private var pending: Pending? = null
    private val shown = MutableStateFlow(PixivBrowserSignInState())

    val state: StateFlow<PixivBrowserSignInState> = shown.asStateFlow()

    /** A new sign-in in place of any before it: the address of Pixiv's page for it, to open in the browser. */
    @Synchronized
    fun start(): String {
        val pkce = fresh()
        pending = Pending(pkce.verifier, now())
        shown.update { it.copy(waiting = true) }
        return PixivLogin.loginUrl(pkce.challenge).toString()
    }

    /** Whether a sign-in started here still waits for its code. */
    @Synchronized
    fun waiting(): Boolean = live() != null

    /**
     * Trades the code in [handedBack] (a `pixiv://` address, Pixiv's
     * callback address, or the bare code) for the account with [trade], with
     * the verifier of the sign-in started here; that sign-in is then over.
     */
    suspend fun handBack(handedBack: String, trade: suspend (code: String, verifier: String) -> PixivAuthUser): PixivCodeOutcome {
        val code = codeIn(handedBack) ?: return PixivCodeOutcome.NoCode
        val held = synchronized(this) {
            if (shown.value.trading) return PixivCodeOutcome.Busy
            val live = live() ?: return PixivCodeOutcome.Expired
            pending = null
            shown.update { it.copy(waiting = false, trading = true) }
            live
        }
        return try {
            val user = trade(code, held.verifier)
            shown.update { it.copy(trading = false, finished = it.finished + 1) }
            PixivCodeOutcome.SignedIn(user)
        } catch (e: CancellationException) {
            shown.update { it.copy(trading = false) }
            throw e
        } catch (e: Exception) {
            // A mistyped or late code leaves the sign-in waiting: another try needs no new page.
            synchronized(this) { if (pending == null) pending = held }
            shown.update { it.copy(trading = false, waiting = live() != null) }
            PixivCodeOutcome.Failed(e)
        }
    }

    /** The reader gave up, or Pixiv said the sign-in failed: nothing waits any more. */
    @Synchronized
    fun cancel() {
        pending = null
        shown.update { it.copy(waiting = false) }
    }

    private fun live(): Pending? {
        val held = pending ?: return null
        if (now() - held.startedAt <= LIFETIME_MS) return held
        pending = null
        shown.update { it.copy(waiting = false) }
        return null
    }

    companion object {
        /** How long a started sign-in waits for its code: Pixiv's page, its checks and a slow reader fit in it. */
        const val LIFETIME_MS = 10 * 60 * 1000L

        private val bareCode = Regex("[A-Za-z0-9_-]{8,512}")

        /**
         * The code in what the reader pasted or the browser handed back: the
         * `code` of a `pixiv://` address or of Pixiv's callback address, or
         * the code alone; null for anything else.
         */
        fun codeIn(text: String): String? {
            val trimmed = text.trim().removeSurrounding("\"")
            PixivLogin.codeFrom(trimmed)?.let { return it }
            return trimmed.takeIf { "://" !in it && bareCode.matches(it) }
        }
    }
}
