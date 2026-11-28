package com.aimdi.xtanative.core.x.signing

import com.aimdi.xtanative.core.x.XSigners
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class XSignersTest {
    private var now = 1_000_000L
    private var derived = 0

    private fun signers(store: SignerStore?) = XSigners(
        derive = { derived++; TransactionSigner(IntArray(48) { it + derived }, "animation-$derived") },
        clock = { now },
        store = store,
    )

    private fun tempFile(): File = Files.createTempDirectory("signers").toFile().resolve("signers.json")

    @Test
    fun aSignerDerivedOnceServesTheSessionUntilItsLifetimeEnds() = runBlocking {
        val signers = signers(null)
        val first = signers.forSession("auth_token=a")
        assertEquals(first, signers.forSession("auth_token=a"))
        assertEquals(1, derived)
        now += 6 * 60 * 60 * 1000L
        signers.forSession("auth_token=a")
        assertEquals(2, derived, "after six hours the signer is derived again")
    }

    @Test
    fun aColdStartSignsWithTheSignerTheLastRunDerived() = runBlocking {
        val file = tempFile()
        val before = signers(FileSignerStore(file)).forSession("auth_token=a")
        assertFalse(file.readText().contains("auth_token"), "the file keys by a digest, never the session")

        val after = signers(FileSignerStore(file)).forSession("auth_token=a")
        assertEquals(1, derived, "nothing derived on the second start")
        assertContentEquals(before.keyBytes, after.keyBytes)
        assertEquals(before.animationKey, after.animationKey)
    }

    @Test
    fun clearingForgetsTheStoredSignersToo() = runBlocking {
        val file = tempFile()
        val signers = signers(FileSignerStore(file))
        signers.forSession("auth_token=a")
        signers.clear()
        signers(FileSignerStore(file)).forSession("auth_token=a")
        assertEquals(2, derived)
    }

    @Test
    fun aFailureThatIsNotTheNetworksIsNotRetriedAtOnce() {
        var attempts = 0
        val signers = XSigners(derive = { attempts++; throw SigningUnavailableException("no key") }, clock = { now })
        repeat(2) { assertFailsWith<SigningUnavailableException> { runBlocking { signers.forSession(null) } } }
        assertEquals(1, attempts)
        now += 31_000
        assertFailsWith<SigningUnavailableException> { runBlocking { signers.forSession(null) } }
        assertEquals(2, attempts)
    }

    @Test
    fun aNetworkFailurePausesTheSessionsNextDerivationAFewSeconds() {
        var attempts = 0
        val signers = XSigners(derive = { attempts++; throw java.io.IOException("offline") }, clock = { now })
        repeat(2) { assertFailsWith<java.io.IOException> { runBlocking { signers.forSession("auth_token=a") } } }
        assertEquals(1, attempts, "the second read gets the same failure without asking x.com")
        now += 5_000
        assertFailsWith<java.io.IOException> { runBlocking { signers.forSession("auth_token=a") } }
        assertEquals(2, attempts)
    }

    @Test
    fun signersDerivedTogetherDoNotAllRunOutTogether() {
        val signers = signers(null)
        val lifetimes = (1..14).map { signers.lifetimeOf("key-$it") }
        assertTrue(lifetimes.toSet().size > 10, "spread over the hour: $lifetimes")
        assertTrue(lifetimes.all { it in 5 * 60 * 60 * 1000L..6 * 60 * 60 * 1000L })
        assertEquals(lifetimes, (1..14).map { signers.lifetimeOf("key-$it") }, "each session always gets the same share")
    }

    @Test
    fun aSlowDerivationHoldsBackOnlyItsOwnSession() = runBlocking {
        val slowStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val signers = XSigners(
            derive = { cookie ->
                if (cookie == "auth_token=slow") { slowStarted.complete(Unit); release.await() }
                TransactionSigner(IntArray(48) { it }, "animation-$cookie")
            },
            clock = { now },
            timeoutMillis = 60_000,
        )
        val slow = async { signers.forSession("auth_token=slow") }
        slowStarted.await()
        val fast = withTimeout(2_000) { signers.forSession("auth_token=fast") }
        assertEquals("animation-auth_token=fast", fast.animationKey)
        release.complete(Unit)
        assertEquals("animation-auth_token=slow", slow.await().animationKey)
    }

    @Test
    fun anUnreadableFileIsNoSigner() {
        val file = tempFile()
        file.parentFile.mkdirs()
        file.writeText("{not json")
        assertEquals(emptyMap(), FileSignerStore(file).read())
    }
}
