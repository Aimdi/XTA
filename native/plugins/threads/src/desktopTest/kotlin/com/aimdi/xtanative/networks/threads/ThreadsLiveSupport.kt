package com.aimdi.xtanative.networks.threads

import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * [read]'s answer, or the live test skipped when Threads throttles the
 * guest (429): a rate limit says nothing of whether the plugin still reads
 * Threads, so it is no failure. Any other failure still fails the test.
 */
internal suspend fun <T> unlessThrottled(what: String, read: suspend () -> T): T = try {
    read()
} catch (e: ThreadsException) {
    assumeTrue(e.failure != ThreadsFailure.THROTTLED, "Threads throttled the guest reading $what: ${e.code}")
    throw e
}
