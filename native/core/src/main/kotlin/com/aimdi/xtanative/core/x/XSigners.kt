package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.x.http.XRequestBudget
import com.aimdi.xtanative.core.x.http.withRequestBudget
import com.aimdi.xtanative.core.x.signing.SignerStore
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import com.aimdi.xtanative.core.x.signing.StoredSigner
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One derived [TransactionSigner] per session, as XTA's TwitterHeaders keeps
 * them: trusted for [lifetimeMillis] (X rotates what the key is built from),
 * and after a failure that is not the network's, not asked for again for
 * [cooldownMillis]: deriving costs requests to x.com. Deriving gives up after
 * [timeoutMillis], or sooner when the read asking has less time left; a
 * failure that is neither the network's nor the clock's is a
 * [SigningUnavailableException], as XTA's TransactionIdUnavailableException.
 */
class XSigners(
    private val derive: suspend (cookie: String?) -> TransactionSigner,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 6 * 60 * 60 * 1000L,
    private val cooldownMillis: Long = 30 * 1000L,
    private val store: SignerStore? = null,
    private val timeoutMillis: Long = XRequestBudget.SIGNING_MILLIS,
) {
    constructor(bootstrap: SigningBootstrap, store: SignerStore? = null) : this({ cookie -> bootstrap.derive(cookie) }, store = store)

    private class Failure(val error: Throwable, val at: Long)

    private val mutex = Mutex()
    private var signers: HashMap<String, StoredSigner>? = null
    private val failures = HashMap<String, Failure>()

    suspend fun forSession(cookie: String?): TransactionSigner = mutex.withLock {
        // Only a digest is kept as the key, never the session itself.
        val key = cookie?.let { digest(it) } ?: "anonymous"
        val now = clock()
        val known = signers ?: HashMap(store?.read().orEmpty()).also { signers = it }
        known[key]?.takeIf { now - it.derivedAt in 0 until lifetimeMillis }?.let { return@withLock it.signer }
        failures[key]?.takeIf { now - it.at < cooldownMillis }?.let { throw it.error }
        try {
            withRequestBudget(timeoutMillis, "Deriving the transaction signer") { derive(cookie) }.also {
                known[key] = StoredSigner(it, clock())
                failures.remove(key)
                store?.write(known.filterValues { stored -> clock() - stored.derivedAt < lifetimeMillis })
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            // A network blip or the deadline: the next request may try again at once.
            throw error
        } catch (error: Exception) {
            val unavailable = error as? SigningUnavailableException ?: SigningUnavailableException("The transaction signer could not be derived: $error", error)
            failures[key] = Failure(unavailable, clock())
            throw unavailable
        }
    }

    /** Forgets every signer: X answered as if they were stale. */
    suspend fun clear() = mutex.withLock {
        signers = HashMap()
        store?.write(emptyMap())
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
