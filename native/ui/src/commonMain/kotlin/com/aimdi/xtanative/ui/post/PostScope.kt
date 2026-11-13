package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.common.LocalGraph

/**
 * Whether the sensitive media of one screen is hidden, as XTA's
 * TweetContextState: "Show" on one gate opens every gate of the screen,
 * and "Always show" also saves the choice.
 */
@Stable
class SensitiveReveal(hidden: Boolean) {
    var hidden by mutableStateOf(hidden)
}

/**
 * A screen's "translate everything": a long press on any post's translate
 * button asks every post shown to translate itself, as XTA's
 * TranslationBroadcast does in a conversation.
 */
@Stable
class TranslateAll {
    var requests by mutableIntStateOf(0)
        private set

    fun request() {
        requests++
    }
}

/**
 * What the post cards of one screen share: the reader's settings, its
 * sensitive gate, its translate-everything, the opened post's id (drawn as
 * X draws an opened post), the profile on screen (whose posts keep their
 * author in non-confirmation-bias mode), and zen mode's hidden replies.
 */
@Stable
class PostScope(
    val prefs: PostPrefs,
    val sensitive: SensitiveReveal,
    val translateAll: TranslateAll? = null,
    val focalId: String? = null,
    val profileScreenName: String? = null,
    /** Zen mode hides an opened post's replies until a long press on its reply count shows them. */
    val revealReplies: (() -> Unit)? = null,
)

/** The screen's post scope; null outside [PostScopeProvider], where each card keeps its own. */
val LocalPostScope = compositionLocalOf<PostScope?> { null }

/** The reader's settings for the post screens, read and then followed as they change. */
@Composable
fun rememberPostPrefs(): PostPrefs {
    val store = LocalGraph.current.settings
    val settings by store.state.collectAsStateWithLifecycle()
    LaunchedEffect(store) { store.load() }
    return remember(settings) { settings?.let(PostPrefs::of) ?: PostPrefs() }
}

/** [content]'s posts in one scope, with the reader's settings. */
@Composable
fun PostScopeProvider(
    focalId: String? = null,
    profileScreenName: String? = null,
    translateAll: Boolean = false,
    revealReplies: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val prefs = rememberPostPrefs()
    // Settings read after the first frame may start the gates the other way.
    val sensitive = remember(prefs.sensitiveHidden) { SensitiveReveal(prefs.sensitiveHidden) }
    val all = remember { if (translateAll) TranslateAll() else null }
    val scope = remember(prefs, focalId, profileScreenName, revealReplies) {
        PostScope(prefs, sensitive, all, focalId, profileScreenName, revealReplies)
    }
    CompositionLocalProvider(LocalPostScope provides scope, content = content)
}

/** The scope of the post being drawn: the screen's, else one of its own with the reader's settings. */
@Composable
internal fun postScope(): PostScope {
    LocalPostScope.current?.let { return it }
    val prefs = rememberPostPrefs()
    val sensitive = remember(prefs.sensitiveHidden) { SensitiveReveal(prefs.sensitiveHidden) }
    return remember(prefs, sensitive) { PostScope(prefs, sensitive) }
}

/** "Always show sensitive media": saved, and every gate of the screen opens. */
internal suspend fun alwaysShowSensitive(graph: com.aimdi.xtanative.data.AppGraph, reveal: SensitiveReveal) {
    reveal.hidden = false
    graph.settings.set(PostsPrefs.alwaysShowSensitiveMedia, true)
}
