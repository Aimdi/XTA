package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.pixiv.PixivFailure
import com.aimdi.xtanative.networks.pixiv.PixivSauceMatch
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a search by image stands: nothing chosen yet, searching, failed, or what SauceNAO found. */
internal sealed interface PixivSauceState {
    data object Idle : PixivSauceState

    data object Searching : PixivSauceState

    data class Failed(val error: Throwable) : PixivSauceState

    data class Found(val matches: List<PixivSauceMatch>) : PixivSauceState
}

/** A chosen picture that could not be read, or turned into one SauceNAO takes. */
internal class PixivUnreadablePicture : Exception("The picture could not be read")

/**
 * Search by image (pixiv_saucenao_sheet.dart's PixivSauceNaoStore): the
 * picture the reader chose, [prepare]d (made a PNG no wider than
 * [MAX_WIDTH]) and [search]ed; Retry searches the same picture again.
 */
internal class PixivSauceModel(
    private val scope: CoroutineScope,
    private val search: suspend (png: ByteArray) -> List<PixivSauceMatch>,
    private val prepare: suspend (picture: ByteArray) -> ByteArray?,
) {
    private val mutable = MutableStateFlow<PixivSauceState>(PixivSauceState.Idle)
    val state: StateFlow<PixivSauceState> = mutable.asStateFlow()
    private var picture: ByteArray? = null
    private var running: Job? = null

    /** The picture the reader chose, read through [read], searched. */
    fun chosen(read: suspend () -> ByteArray?) = searchFor {
        val bytes = withContext(Dispatchers.IO) { read() } ?: throw PixivUnreadablePicture()
        bytes.also { picture = it }
    }

    /** The picture chosen last, searched again. */
    fun retry() {
        val bytes = picture ?: return
        searchFor { bytes }
    }

    /** Back to nothing chosen, as the sheet closes. */
    fun reset() {
        running?.cancel()
        picture = null
        mutable.value = PixivSauceState.Idle
    }

    private fun searchFor(picked: suspend () -> ByteArray) {
        running?.cancel()
        running = scope.launch {
            mutable.value = PixivSauceState.Searching
            mutable.value = try {
                val png = prepare(picked()) ?: throw PixivUnreadablePicture()
                PixivSauceState.Found(search(png))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PixivSauceState.Failed(e)
            }
        }
    }

    companion object {
        /** XTA's widest upload: a phone's photo goes up in seconds. */
        const val MAX_WIDTH = 1000

        /** Pixiv's own words for an exchange that failed; null for a picture SauceNAO could not be asked about, which it says in its own. */
        fun failureOf(error: Throwable): PixivFailure? = if (error is IOException || error is NetworkRequestException) PixivFailure.of(error) else null

        /** How alike SauceNAO found the pictures, [percent] of a hundred, as [language] writes a percentage to one decimal. */
        fun similarity(percent: Double, language: String): String =
            NumberFormat.getPercentInstance(Locale.forLanguageTag(language)).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }.format(percent / 100)
    }
}
