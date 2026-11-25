package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.data.LocalTranslation
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.services.ai.XTranslator
import com.aimdi.xtanative.services.translation.TranslationConfig
import com.aimdi.xtanative.services.translation.TranslationException
import com.aimdi.xtanative.services.translation.TranslationFailure
import com.aimdi.xtanative.ui.common.LocalXClient
import com.aimdi.xtanative.ui.common.currentLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One translation asked for: where it is, what it gave, why it failed. Kept
 * by [TranslationRuns] past the card that asked, so a card scrolled away
 * while X translates neither cancels it nor comes back untranslated.
 */
@Stable
internal class TranslationRun {
    var status by mutableStateOf(TranslationStatus.ORIGINAL)
    var problem by mutableStateOf<TranslationProblem?>(null)
    var translated by mutableStateOf<XPost?>(null)
}

/**
 * The translations asked for while the app runs, by post, target and
 * service; the [max] most recently asked are kept. They run in [scope], not
 * in the card's, which leaves with the card.
 */
internal class TranslationRuns(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val max: Int = 300,
) {
    private val kept = object : LinkedHashMap<String, TranslationRun>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationRun>?): Boolean = size > max
    }

    /** The run of [key], new where none is kept. */
    fun of(key: String): TranslationRun = synchronized(kept) { kept.getOrPut(key) { TranslationRun() } }

    /**
     * Translates [run] with [ask], unless it is under way: a translation
     * already given is shown again without asking X twice.
     */
    fun start(run: TranslationRun, ask: suspend () -> XPost) {
        if (run.status == TranslationStatus.TRANSLATING) return
        if (run.translated != null) {
            run.status = TranslationStatus.TRANSLATED
            return
        }
        run.status = TranslationStatus.TRANSLATING
        run.problem = null
        scope.launch {
            run.status = try {
                run.translated = ask()
                TranslationStatus.TRANSLATED
            } catch (error: CancellationException) {
                run.status = TranslationStatus.ORIGINAL
                throw error
            } catch (error: Throwable) {
                run.problem = TranslationProblem.of(error)
                TranslationStatus.FAILED
            }
        }
    }

    companion object {
        /** The one the screens share. */
        val shared = TranslationRuns()

        /** The key of [postId] translated into [target] by [service] (null for X's own translation). */
        fun key(postId: String, target: String, service: TranslationConfig?): String = "$postId|$target|${service?.hashCode() ?: 0}"
    }
}

/**
 * One post's translate control (tweet.dart): its original, the translation
 * once X gave it (kept, so going back and forth asks X once), and why X gave
 * none when it failed. Its [run] outlives the card ([TranslationRuns]).
 */
@Stable
class PostTranslation internal constructor(
    private val post: XPost,
    private val run: TranslationRun,
    private val runs: TranslationRuns,
    private val ask: suspend () -> XPost,
) {
    val status: TranslationStatus get() = run.status
    val problem: TranslationProblem? get() = run.problem

    /** What the card draws: the translation while it is shown, else the post. */
    val shown: XPost get() = run.translated?.takeIf { status == TranslationStatus.TRANSLATED } ?: post

    /** Translates, or shows the translation already given; it goes on when the card leaves. */
    fun translate() = runs.start(run, ask)

    fun showOriginal() {
        run.status = TranslationStatus.ORIGINAL
    }
}

/**
 * The translate control of [post], or null when it offers none: a post in
 * the reader's own language, one X marks as having no words, an article.
 * A screen's [TranslateAll] request translates it too. X translates, unless
 * the reader chose a translation service of their own (Settings ›
 * Translation), which then translates X's posts too, as XTA's tweet.dart
 * does, into its own language when one is set.
 */
@Composable
internal fun rememberPostTranslation(post: XPost, scope: PostScope): PostTranslation? {
    val locale = currentLocale()
    val translations = LocalTranslation.current
    // The settings as the app's frame follows them for every post; else as the store keeps them current, followed here.
    val shared = sharedSettings(translations.settingsNow)
    val now = if (shared != null) shared.value else translations.settingsNow.collectAsState().value
    val service = remember(now) { translations.configFor(now) }
    val appTarget = remember(scope.prefs.locale, locale) { scope.prefs.translationTarget(locale.toString()) }
    val target = if (service.enabled && service.target.isNotEmpty()) service.target.replace('_', '-') else appTarget
    if (!PostView.offersTranslation(post, target)) return null
    val client = LocalXClient.current
    val translation = remember(post, target, service) {
        val runs = TranslationRuns.shared
        val run = runs.of(TranslationRuns.key(post.id, target, service.takeIf { it.enabled }))
        if (service.enabled) {
            PostTranslation(post, run, runs) {
                val translated = translations.translate(ServiceTranslation.text(post), target, service)
                    ?: throw TranslationException(TranslationFailure.NOT_CONFIGURED)
                ServiceTranslation.applied(post, translated.text)
            }
        } else {
            val translator = XTranslator({ id, language -> client.translate(id, language) }, TranslationMemory.shared)
            PostTranslation(post, run, runs) { translator.translate(post.id, post.lang, target).applyTo(post) }
        }
    }
    val all = scope.translateAll
    if (all != null) {
        // A request made before this post was drawn is not its to answer.
        var answered by remember(translation) { mutableIntStateOf(all.requests) }
        LaunchedEffect(all.requests) {
            if (all.requests > answered) {
                answered = all.requests
                if (translation.status == TranslationStatus.ORIGINAL) translation.translate()
            }
        }
    }
    return translation
}
