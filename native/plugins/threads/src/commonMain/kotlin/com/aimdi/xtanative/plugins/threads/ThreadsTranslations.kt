package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsPeople
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.services.ai.XTranslator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where one post's translation stands: its [status], and the words in
 * [target] once they came; [by] names the reader's translation service that
 * gave them (LibreTranslate, DeepL, their AI), null where Threads did.
 */
internal data class ThreadsTranslated(val status: TranslationStatus, val target: String, val text: String? = null, val by: String? = null) {
    /** The words to draw in place of the post's own: the translation while it is shown. */
    val shown: String? get() = text.takeIf { status == TranslationStatus.TRANSLATED }
}

/** A translation's words, and the service that gave them ([ThreadsTranslated.by]). */
internal data class ThreadsWords(val text: String, val by: String? = null)

/**
 * The posts the reader asked to translate, shared by every screen so a card
 * and its opened post agree: a translation is asked once per post and
 * language, and going back and forth between it and the original asks
 * nothing more. Once Meta answers no path at all, Threads' own translation
 * is [available] no more this run, rather than offered to fail again. [ask]
 * is the client's read
 * ([com.aimdi.xtanative.networks.threads.ThreadsDirectClient.translation]),
 * or with `viaService` the reader's translation service
 * ([com.aimdi.xtanative.ui.plugins.PluginHost.translate]).
 */
internal class ThreadsTranslations(
    private val scope: CoroutineScope,
    private val ask: suspend (post: ThreadsPost, target: String, viaService: Boolean) -> ThreadsWords?,
) {
    private val states = MutableStateFlow<Map<String, ThreadsTranslated>>(emptyMap())
    private val offered = MutableStateFlow(true)

    /** Each post's translation by [key]; a post never asked is not in it. */
    val state: StateFlow<Map<String, ThreadsTranslated>> = states.asStateFlow()

    /** Whether Meta may still translate: false once no path answered. */
    val available: StateFlow<Boolean> = offered.asStateFlow()

    /**
     * Shows [post] in [target]: the kept translation at once, else Meta is
     * asked, or with [viaService] the reader's service ([by], for the line
     * its failure shows). Null when nothing is asked.
     */
    fun translate(post: ThreadsPost, target: String, viaService: Boolean = false, by: String? = null): Job? {
        val key = key(post)
        val known = states.value[key]
        if (known?.status == TranslationStatus.TRANSLATING && known.target == target) return null
        if (known?.text != null && known.target == target) {
            put(key, known.copy(status = TranslationStatus.TRANSLATED))
            return null
        }
        put(key, ThreadsTranslated(TranslationStatus.TRANSLATING, target, by = by.takeIf { viaService }))
        return scope.launch { put(key, answer(post, target, viaService, by)) }
    }

    /** Shows [post]'s own words again, keeping the translation for the next time. */
    fun showOriginal(post: ThreadsPost) {
        val key = key(post)
        states.value[key]?.let { put(key, it.copy(status = TranslationStatus.ORIGINAL)) }
    }

    private suspend fun answer(post: ThreadsPost, target: String, viaService: Boolean, by: String?): ThreadsTranslated = try {
        val words = ask(post, target, viaService)
        val text = words?.text?.trim()?.ifEmpty { null }
        ThreadsTranslated(if (text == null) TranslationStatus.FAILED else TranslationStatus.TRANSLATED, target, text, words?.by ?: by.takeIf { viaService })
    } catch (e: CancellationException) {
        throw e
    } catch (e: ThreadsException) {
        if (e.failure == ThreadsFailure.NO_SUCH_FEED) offered.value = false
        ThreadsTranslated(if (e.failure == ThreadsFailure.NOT_CONFIGURED) TranslationStatus.ORIGINAL else TranslationStatus.FAILED, target)
    } catch (_: Exception) {
        ThreadsTranslated(TranslationStatus.FAILED, target, by = by.takeIf { viaService })
    }

    private fun put(key: String, value: ThreadsTranslated) = states.update { kept ->
        val next = LinkedHashMap(kept).apply { remove(key); put(key, value) }
        if (next.size <= MAX) next else next.entries.drop(next.size - MAX).associate { it.key to it.value }
    }

    companion object {
        /** How many posts' translations are kept this run. */
        const val MAX = 200

        /** One post however it is shown: a repost and the post it shares are one. */
        fun key(post: ThreadsPost): String = post.shortcode ?: post.id
    }
}

/** Who translates a post: Threads itself, or the reader's translation service. */
internal enum class ThreadsTranslator { THREADS, SERVICE }

/** When a post offers translation, decided apart from its drawing. */
internal object ThreadsTranslationRules {
    /**
     * Whether Meta is known to answer a translation where the client asks.
     * CI's live probe (13 Nov 2026) found all three REST guesses a 404 even
     * for a guest, where real session paths send a guest to sign in;
     * threads.com translates through BarcelonaPostTranslationQuery, whose doc
     * id is not known. Until one answers, Translate is not offered.
     */
    const val PATH_KNOWN = false

    /**
     * Whether [post] offers Translate to a reader reading in [target]:
     * signed in (Meta translates for a session only) while translation is
     * [available], a post with words and no spoiler (a translation could
     * not keep a spoiler covered), whose media id is known, that Meta does
     * not mark as untranslatable, in a language not the reader's own (as
     * the app's X posts decide it; a post whose language Meta did not name
     * keeps the offer).
     */
    fun offers(post: ThreadsPost, target: String, signedIn: Boolean, available: Boolean, pathKnown: Boolean = PATH_KNOWN): Boolean =
        pathKnown && signedIn && available && post.text.isNotBlank() && !post.hasSpoiler && post.facts.canTranslate != false &&
            ThreadsPeople.mediaId(post) != null && XTranslator.offers(post.facts.language, target)

    /**
     * Whether [post] offers Translate through the reader's translation
     * service ([com.aimdi.xtanative.ui.plugins.PluginHost.translator]),
     * translating into [serviceTarget]: a service is set, and the post has
     * words, no spoiler (a translation could not keep it covered), and a
     * language not [serviceTarget] (one Meta did not name keeps the offer).
     * Meta's own `can_translate` is Meta's: the service reads the words
     * whatever Meta offers.
     */
    fun serviceOffers(post: ThreadsPost, serviceTarget: String?): Boolean =
        serviceTarget != null && post.text.isNotBlank() && !post.hasSpoiler && XTranslator.offers(post.facts.language, serviceTarget)

    /**
     * Who translates [post], or null when it offers no Translate: Threads
     * first where its own translation is known to answer and the reader is
     * signed in ([offers]), else the reader's service ([serviceOffers]).
     */
    fun translator(
        post: ThreadsPost,
        target: String,
        signedIn: Boolean,
        available: Boolean,
        serviceTarget: String?,
        pathKnown: Boolean = PATH_KNOWN,
    ): ThreadsTranslator? = when {
        offers(post, target, signedIn, available, pathKnown) -> ThreadsTranslator.THREADS
        serviceOffers(post, serviceTarget) -> ThreadsTranslator.SERVICE
        else -> null
    }
}
