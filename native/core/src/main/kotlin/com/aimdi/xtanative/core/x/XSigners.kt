package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.x.http.XRequestBudget
import com.aimdi.xtanative.core.x.http.withRequestBudget
import com.aimdi.xtanative.core.x.signing.SignerStore
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import com.aimdi.xtanative.core.x.signing.StoredSigner
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One derived [TransactionSigner] per session, as XTA's TwitterHeaders keeps
 * them: trusted for [lifetimeMillis] (X rotates what the key is built from),
 * less a share of [jitterMillis] fixed per session, so signers derived
 * together do not all run out together. After a failure that is not the
 * network's, not asked for again for [cooldownMillis]; after the network's,
 * for [networkPauseMillis]: deriving costs requests to x.com. Each session
 * derives under its own lock, so one slow derivation holds back only that
 * session's reads. Deriving gives up after [timeoutMillis], or sooner when
 * the read asking has less time left; a failure that is neither the
 * network's nor the clock's is a [SigningUnavailableException], as XTA's
 * TransactionIdUnavailableException.
 */
class XSigners(
    private val derive: suspend (cookie: String?) -> TransactionSigner,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 6 * 60 * 60 * 1000L,
    private val cooldownMillis: Long = 30 * 1000L,
    private val store: SignerStore? = null,
    private val timeoutMillis: Long = XRequestBudget.SIGNING_MILLIS,
    private val jitterMillis: Long = 60 * 60 * 1000L,
    private val networkPauseMillis: Long = 5 * 1000L,
) {
    constructor(bootstrap: SigningBootstrap, store: SignerStore? = null) : this({ cookie -> bootstrap.derive(cookie) }, store = store)

    private class Failure(val error: Throwable, val until: Long)

    /** Guards [signers], [failures] and [locks]; never held while deriving. */
    private val state = Any()
    private var signers: HashMap<String, StoredSigner>? = null
    private val failures = HashMap<String, Failure>()
    private val locks = HashMap<String, Mutex>()

    suspend fun forSession(cookie: String?): TransactionSigner {
        // Only a digest is kept as the key, never the session itself.
        val key = cookie?.let { digest(it) } ?: "anonymous"
        known(key)?.let { return it }
        return lockOf(key).withLock { known(key) ?: derived(key, cookie) }
    }

    /** [key]'s signer while it is trusted; throws the failure it is still cooling down from. */
    private fun known(key: String): TransactionSigner? = synchronized(state) {
        val now = clock()
        loaded()[key]?.takeIf { now - it.derivedAt in 0 until lifetimeOf(key) }?.let { return it.signer }
        failures[key]?.takeIf { now < it.until }?.let { throw it.error }
        null
    }

    private suspend fun derived(key: String, cookie: String?): TransactionSigner = try {
        withRequestBudget(timeoutMillis, "Deriving the transaction signer") { derive(cookie) }.also { signer ->
            synchronized(state) {
                val known = loaded()
                known[key] = StoredSigner(signer, clock())
                failures.remove(key)
                store?.write(known.filterValues { stored -> clock() - stored.derivedAt < lifetimeMillis })
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: InterruptedIOException) {
        // The deadline, often the asking read's own: the next read may try again at once.
        throw error
    } catch (error: IOException) {
        // A network blip: a short pause, not a cooldown, before the next try.
        synchronized(state) { failures[key] = Failure(error, clock() + networkPauseMillis) }
        throw error
    } catch (error: Exception) {
        val unavailable = error as? SigningUnavailableException ?: SigningUnavailableException("The transaction signer could not be derived: $error", error)
        synchronized(state) { failures[key] = Failure(unavailable, clock() + cooldownMillis) }
        throw unavailable
    }

    private fun loaded(): HashMap<String, StoredSigner> = signers ?: HashMap(store?.read().orEmpty()).also { signers = it }

    private fun lockOf(key: String): Mutex = synchronized(state) { locks.getOrPut(key, ::Mutex) }

    /** [lifetimeMillis] less a share of [jitterMillis] that [key] always gets. */
    internal fun lifetimeOf(key: String): Long =
        lifetimeMillis - if (jitterMillis <= 0) 0 else Math.floorMod(key.hashCode().toLong(), jitterMillis)

    /** Forgets every signer: X answered as if they were stale. */
    fun clear() = synchronized(state) {
        signers = HashMap()
        failures.clear()
        store?.write(emptyMap())
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
