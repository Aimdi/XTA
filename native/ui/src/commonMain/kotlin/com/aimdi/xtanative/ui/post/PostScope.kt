package com.aimdi.xtanative.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.common.LocalSessions
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.common.rememberLoadsManually
import com.aimdi.xtanative.ui.saved.SharedKeep
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow

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

/** The reader's post settings read once for the whole app ([SharedPostPrefs]); null outside it. */
private val LocalPostPrefs = compositionLocalOf<PostPrefs?> { null }

/**
 * The settings and the session as [SharedPostPrefs] follows them, once for
 * every card under it: a card reads the part it draws from these rather than
 * following either by itself. Each is given only to who asks for the store
 * followed here, so a part drawn over another store follows its own.
 */
@Stable
internal class SharedReads(
    private val settingsFlow: StateFlow<Settings?>,
    private val settings: State<Settings?>,
    private val sessionStore: SessionStore,
    private val session: State<Session?>,
) {
    fun settingsOf(flow: StateFlow<Settings?>): State<Settings?>? = settings.takeIf { flow === settingsFlow }

    fun sessionOf(store: SessionStore): State<Session?>? = session.takeIf { store === sessionStore }
}

/** What [SharedPostPrefs] follows for the cards under it; null outside it. */
private val LocalSharedReads = staticCompositionLocalOf<SharedReads?> { null }

/**
 * [content] with what its post cards read of the app followed once for all
 * of them, rather than by every card scrolled past: the reader's post
 * settings (a card outside a [PostScopeProvider], as in Home's feeds and
 * search, reads them from here), the settings and the session themselves
 * ([sharedSettings], [sharedSession]) and what is saved and liked on the
 * device ([SharedKeep]).
 */
@Composable
internal fun SharedPostPrefs(content: @Composable () -> Unit) {
    val store = LocalSettingsStore.current
    val settings = store.state.collectAsStateWithLifecycle()
    LaunchedEffect(store) { store.loadUnlessUnreadable() }
    val now = settings.value
    val prefs = remember(now) { now?.let(PostPrefs::of) ?: PostPrefs() }
    val sessions = LocalSessions.current
    val session = sessions.state.collectAsStateWithLifecycle()
    val reads = remember(store, settings, sessions, session) { SharedReads(store.state, settings, sessions, session) }
    val mutes = remember(now?.get(PostsPrefs.muteList)) { Mutes.of(now) }
    CompositionLocalProvider(LocalPostPrefs provides prefs, LocalSharedReads provides reads, LocalMuteList provides mutes) { SharedKeep(content) }
}

/**
 * [flow]'s settings as [SharedPostPrefs] follows them, or null where it does
 * not (another store's settings, or a part drawn outside the app's frame):
 * the caller then follows them itself, as it always did.
 */
@Composable
@ReadOnlyComposable
internal fun sharedSettings(flow: StateFlow<Settings?>): State<Settings?>? = LocalSharedReads.current?.settingsOf(flow)

/** [store]'s session as [SharedPostPrefs] follows it, or null where the caller is to follow it itself. */
@Composable
@ReadOnlyComposable
internal fun sharedSession(store: SessionStore): State<Session?>? = LocalSharedReads.current?.sessionOf(store)

/** The post settings [SharedPostPrefs] made of [store]'s, or null where it follows another store or none. */
@Composable
@ReadOnlyComposable
internal fun sharedPostPrefs(store: SettingsStore): PostPrefs? = LocalPostPrefs.current?.takeIf { sharedSettings(store.state) != null }

/** [store]'s settings as they change: read from [SharedPostPrefs] where it follows them, else followed here. */
@Composable
internal fun settingsNow(store: SettingsStore): Settings? {
    val shared = sharedSettings(store.state)
    if (shared != null) return shared.value
    val settings by store.state.collectAsStateWithLifecycle()
    return settings
}

/**
 * Whether media waits for a tap before it loads ([rememberLoadsManually]):
 * read from the settings [SharedPostPrefs] follows where it does, so a tile
 * follows nothing to learn so, else followed here.
 */
@Composable
internal fun loadsManually(): Boolean {
    val shared = sharedSettings(LocalSettingsStore.current.state)
    return if (shared != null) shared.value?.get(MediaPrefs.loadManually) == true else rememberLoadsManually()
}

/** Reads the settings; ones that cannot be read leave the defaults, while the start-failure screen says why. */
private suspend fun SettingsStore.loadUnlessUnreadable() {
    try {
        load()
    } catch (_: IOException) {
    }
}

/** The reader's settings for the post screens, read and then followed as they change. */
@Composable
fun rememberPostPrefs(): PostPrefs {
    val store = LocalSettingsStore.current
    val settings by store.state.collectAsStateWithLifecycle()
    LaunchedEffect(store) { store.loadUnlessUnreadable() }
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
    // Each card keeps its own sensitive gate, as a feed's do; the settings are read once for the app.
    val prefs = LocalPostPrefs.current ?: rememberPostPrefs()
    val sensitive = remember(prefs.sensitiveHidden) { SensitiveReveal(prefs.sensitiveHidden) }
    return remember(prefs, sensitive) { PostScope(prefs, sensitive) }
}

/** "Always show sensitive media": saved, and every gate of the screen opens. */
internal suspend fun alwaysShowSensitive(settings: SettingsStore, reveal: SensitiveReveal) {
    reveal.hidden = false
    settings.set(PostsPrefs.alwaysShowSensitiveMedia, true)
}
