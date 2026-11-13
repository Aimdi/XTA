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

/** Where one post's translation stands: its [status], and Meta's words in [target] once they came. */
internal data class ThreadsTranslated(val status: TranslationStatus, val target: String, val text: String? = null) {
    /** The words to draw in place of the post's own: the translation while it is shown. */
    val shown: String? get() = text.takeIf { status == TranslationStatus.TRANSLATED }
}

/**
 * The posts the reader asked Threads to translate, shared by every screen
 * so a card and its opened post agree: a translation is asked once per
 * post and language, and going back and forth between it and the original
 * asks nothing more. Once Meta answers no path at all, translation is
 * [available] no more this run, rather than offered to fail again. [ask] is
 * the client's read ([com.aimdi.xtanative.networks.threads.ThreadsDirectClient.translation]).
 */
internal class ThreadsTranslations(
    private val scope: CoroutineScope,
    private val ask: suspend (post: ThreadsPost, target: String) -> String?,
) {
    private val states = MutableStateFlow<Map<String, ThreadsTranslated>>(emptyMap())
    private val offered = MutableStateFlow(true)

    /** Each post's translation by [key]; a post never asked is not in it. */
    val state: StateFlow<Map<String, ThreadsTranslated>> = states.asStateFlow()

    /** Whether Meta may still translate: false once no path answered. */
    val available: StateFlow<Boolean> = offered.asStateFlow()

    /** Shows [post] in [target]: the kept translation at once, else Meta is asked. Null when nothing is asked. */
    fun translate(post: ThreadsPost, target: String): Job? {
        val key = key(post)
        val known = states.value[key]
        if (known?.status == TranslationStatus.TRANSLATING && known.target == target) return null
        if (known?.text != null && known.target == target) {
            put(key, known.copy(status = TranslationStatus.TRANSLATED))
            return null
        }
        put(key, ThreadsTranslated(TranslationStatus.TRANSLATING, target))
        return scope.launch { put(key, answer(post, target)) }
    }

    /** Shows [post]'s own words again, keeping the translation for the next time. */
    fun showOriginal(post: ThreadsPost) {
        val key = key(post)
        states.value[key]?.let { put(key, it.copy(status = TranslationStatus.ORIGINAL)) }
    }

    private suspend fun answer(post: ThreadsPost, target: String): ThreadsTranslated = try {
        val text = ask(post, target)?.trim()?.ifEmpty { null }
        ThreadsTranslated(if (text == null) TranslationStatus.FAILED else TranslationStatus.TRANSLATED, target, text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ThreadsException) {
        if (e.failure == ThreadsFailure.NO_SUCH_FEED) offered.value = false
        ThreadsTranslated(if (e.failure == ThreadsFailure.NOT_CONFIGURED) TranslationStatus.ORIGINAL else TranslationStatus.FAILED, target)
    } catch (_: Exception) {
        ThreadsTranslated(TranslationStatus.FAILED, target)
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

/** When a post offers translation, decided apart from its drawing. */
internal object ThreadsTranslationRules {
    /**
     * Whether [post] offers Translate to a reader reading in [target]:
     * signed in (Meta translates for a session only) while translation is
     * [available], a post with words and no spoiler (a translation could
     * not keep a spoiler covered), whose media id is known, that Meta does
     * not mark as untranslatable, in a language not the reader's own (as
     * the app's X posts decide it; a post whose language Meta did not name
     * keeps the offer).
     */
    fun offers(post: ThreadsPost, target: String, signedIn: Boolean, available: Boolean): Boolean =
        signedIn && available && post.text.isNotBlank() && !post.hasSpoiler && post.facts.canTranslate != false &&
            ThreadsPeople.mediaId(post) != null && XTranslator.offers(post.facts.language, target)
}
