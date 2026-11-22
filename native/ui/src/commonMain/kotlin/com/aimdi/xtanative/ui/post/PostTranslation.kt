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
import com.aimdi.xtanative.services.translation.TranslationException
import com.aimdi.xtanative.services.translation.TranslationFailure
import com.aimdi.xtanative.ui.common.LocalXClient
import com.aimdi.xtanative.ui.common.currentLocale
import kotlinx.coroutines.CancellationException

/**
 * One post's translate control (tweet.dart): its original, the translation
 * once X gave it (kept, so going back and forth asks X once), and why X gave
 * none when it failed.
 */
@Stable
class PostTranslation internal constructor(private val post: XPost, private val ask: suspend () -> XPost) {
    var status by mutableStateOf(TranslationStatus.ORIGINAL)
        private set
    var problem by mutableStateOf<TranslationProblem?>(null)
        private set
    private var translated by mutableStateOf<XPost?>(null)

    /** What the card draws: the translation while it is shown, else the post. */
    val shown: XPost get() = translated?.takeIf { status == TranslationStatus.TRANSLATED } ?: post

    suspend fun translate() {
        if (status == TranslationStatus.TRANSLATING) return
        if (translated != null) {
            status = TranslationStatus.TRANSLATED
            return
        }
        status = TranslationStatus.TRANSLATING
        problem = null
        status = try {
            translated = ask()
            TranslationStatus.TRANSLATED
        } catch (error: CancellationException) {
            status = TranslationStatus.ORIGINAL
            throw error
        } catch (error: Throwable) {
            problem = TranslationProblem.of(error)
            TranslationStatus.FAILED
        }
    }

    fun showOriginal() {
        status = TranslationStatus.ORIGINAL
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
    val translation = remember(post.id, target, service) {
        if (service.enabled) {
            PostTranslation(post) {
                val translated = translations.translate(ServiceTranslation.text(post), target, service)
                    ?: throw TranslationException(TranslationFailure.NOT_CONFIGURED)
                ServiceTranslation.applied(post, translated.text)
            }
        } else {
            val translator = XTranslator({ id, language -> client.translate(id, language) }, TranslationMemory.shared)
            PostTranslation(post) { translator.translate(post.id, post.lang, target).applyTo(post) }
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
