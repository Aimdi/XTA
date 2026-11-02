package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One derived [TransactionSigner] per session, as XTA's TwitterHeaders keeps
 * them: trusted for [lifetimeMillis] (X rotates what the key is built from),
 * and after a failure that is not the network's, not asked for again for
 * [cooldownMillis]: deriving costs requests to x.com.
 */
class XSigners(
    private val derive: suspend (cookie: String?) -> TransactionSigner,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 6 * 60 * 60 * 1000L,
    private val cooldownMillis: Long = 30 * 1000L,
) {
    constructor(bootstrap: SigningBootstrap) : this({ cookie -> bootstrap.derive(cookie) })

    private class Entry(val signer: TransactionSigner, val derivedAt: Long)
    private class Failure(val error: Throwable, val at: Long)

    private val mutex = Mutex()
    private val signers = HashMap<String, Entry>()
    private val failures = HashMap<String, Failure>()

    suspend fun forSession(cookie: String?): TransactionSigner = mutex.withLock {
        // Only a digest is kept as the key, never the session itself.
        val key = cookie?.let { digest(it) } ?: "anonymous"
        val now = clock()
        signers[key]?.takeIf { now - it.derivedAt < lifetimeMillis }?.let { return@withLock it.signer }
        failures[key]?.takeIf { now - it.at < cooldownMillis }?.let { throw it.error }
        try {
            derive(cookie).also {
                signers[key] = Entry(it, clock())
                failures.remove(key)
            }
        } catch (error: IOException) {
            // A network blip: the next request may try again at once.
            throw error
        } catch (error: Exception) {
            failures[key] = Failure(error, clock())
            throw error
        }
    }

    /** Forgets every signer: X answered as if they were stale. */
    suspend fun clear() = mutex.withLock { signers.clear() }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
