package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.services.ai.XTranslator
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.currentLocale
import kotlinx.coroutines.CancellationException

/**
 * One post's translate control (tweet.dart): its original, the translation
 * once X gave it (kept, so going back and forth asks X once), and why X gave
 * none when it failed.
 */
@Stable
class PostTranslation internal constructor(private val post: XPost, private val translator: XTranslator, private val target: String) {
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
            translated = translator.translate(post.id, post.lang, target).applyTo(post)
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
 * A screen's [TranslateAll] request translates it too.
 */
@Composable
internal fun rememberPostTranslation(post: XPost, scope: PostScope): PostTranslation? {
    val locale = currentLocale()
    val target = remember(scope.prefs.locale, locale) { scope.prefs.translationTarget(locale.toString()) }
    if (!PostView.offersTranslation(post, target)) return null
    val client = LocalGraph.current.client
    val translation = remember(post.id, target) {
        PostTranslation(post, XTranslator({ id, language -> client.translate(id, language) }, TranslationMemory.shared), target)
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
