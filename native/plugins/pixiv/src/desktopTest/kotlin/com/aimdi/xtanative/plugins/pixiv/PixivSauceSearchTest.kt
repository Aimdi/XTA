package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.pixiv.PixivFailure
import com.aimdi.xtanative.networks.pixiv.PixivSauceMatch
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Search by image: the picture chosen, made ready, searched, and searched again on Retry. */
class PixivSauceSearchTest {
    private val match = PixivSauceMatch(42, 94.6, "Sky", "Hoshino")

    private suspend fun PixivSauceModel.settled(): PixivSauceState = withTimeout(5_000) { state.first { it is PixivSauceState.Found || it is PixivSauceState.Failed } }

    @Test
    fun theChosenPictureIsMadeReadyThenSearched(): Unit = runBlocking {
        coroutineScope {
            val sent = mutableListOf<ByteArray>()
            val model = PixivSauceModel(this, search = { png -> sent += png; listOf(match) }, prepare = { picture -> picture + 9 })
            model.chosen { byteArrayOf(1, 2) }
            assertEquals(PixivSauceState.Found(listOf(match)), model.settled())
            assertEquals(listOf(1, 2, 9), sent.single().map { it.toInt() }, "what goes up is the prepared picture")
            model.reset()
            assertEquals(PixivSauceState.Idle, model.state.value)
        }
    }

    @Test
    fun retrySearchesTheSamePictureAgain(): Unit = runBlocking {
        coroutineScope {
            var calls = 0
            val model = PixivSauceModel(this, search = { if (++calls == 1) throw IOException("offline") else listOf(match) }, prepare = { it })
            model.chosen { byteArrayOf(7) }
            assertIs<PixivSauceState.Failed>(model.settled())
            model.retry()
            withTimeout(5_000) { model.state.first { it is PixivSauceState.Found } }
            assertEquals(2, calls)
        }
    }

    @Test
    fun aPictureThatCannotBeReadOrPreparedFailsInSauceNaosWords(): Unit = runBlocking {
        coroutineScope {
            val unread = PixivSauceModel(this, search = { listOf(match) }, prepare = { it })
            unread.chosen { null }
            val failed = assertIs<PixivSauceState.Failed>(unread.settled())
            assertNull(PixivSauceModel.failureOf(failed.error))
            val undecoded = PixivSauceModel(this, search = { listOf(match) }, prepare = { null })
            undecoded.chosen { byteArrayOf(1) }
            assertNull(PixivSauceModel.failureOf(assertIs<PixivSauceState.Failed>(undecoded.settled()).error))
        }
    }

    @Test
    fun aFailedExchangeIsWordedAsPixivWordsItsFailures() {
        assertEquals(PixivFailure.NETWORK, PixivSauceModel.failureOf(IOException("offline")))
        assertEquals(PixivFailure.RATE_LIMITED, PixivSauceModel.failureOf(NetworkRequestException("pixiv", 429, "saucenao: 429")))
    }

    @Test
    fun searchingShowsWhileThePictureIsOnItsWay(): Unit = runBlocking {
        coroutineScope {
            val answer = CompletableDeferred<List<PixivSauceMatch>>()
            val model = PixivSauceModel(this, search = { answer.await() }, prepare = { it })
            model.chosen { byteArrayOf(1) }
            withTimeout(5_000) { model.state.first { it == PixivSauceState.Searching } }
            answer.complete(emptyList())
            assertEquals(PixivSauceState.Found(emptyList()), model.settled())
        }
    }

    @Test
    fun theSimilarityIsAPercentageInTheReadersLanguage() {
        assertEquals("94.6%", PixivSauceModel.similarity(94.57, "en"))
        assertTrue(PixivSauceModel.similarity(94.57, "de").startsWith("94,6"), PixivSauceModel.similarity(94.57, "de"))
    }
}
