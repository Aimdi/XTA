package com.aimdi.xtanative.ui.saved

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.saved.SavedLibrary

/**
 * What is saved and liked on this device, followed once for every bookmark
 * and heart under a [SharedKeep] frame instead of once by each. Neither is
 * read or followed before a card asks ([wantSaved], [wantLiked]), as neither
 * was before a card drew its bookmark or its heart; until the frame follows
 * one, a card reads what the store holds now.
 */
@Stable
internal class KeptNow(val keeper: Keeper) {
    private var askedSaved = false
    private var askedLiked = false

    /** A card drew a bookmark: the frame is to follow the saved library. */
    var savedWanted by mutableStateOf(false)
        private set

    /** A card drew a heart: the frame is to follow the liked ids. */
    var likedWanted by mutableStateOf(false)
        private set

    /** The saved library, without its posts' content, as the frame follows it; null until it does. */
    var saved by mutableStateOf<State<SavedLibrary?>?>(null)

    /** The liked ids as the frame follows them; null until it does. */
    var liked by mutableStateOf<State<Set<String>>?>(null)

    private val likedIds by lazy { LikedIds.of(keeper).ids }

    fun wantSaved() {
        if (askedSaved) return
        askedSaved = true
        savedWanted = true
    }

    fun wantLiked() {
        if (askedLiked) return
        askedLiked = true
        likedWanted = true
    }

    fun isSaved(postId: String): Boolean = (saved?.value ?: keeper.saved.index.value)?.isSaved(postId) == true

    fun isLiked(postId: String): Boolean = postId in (liked?.value ?: likedIds.value)
}

/** The frame's [KeptNow]; null outside a [SharedKeep] frame, where each bookmark and heart follows the stores itself. */
private val LocalKeptNow = staticCompositionLocalOf<KeptNow?> { null }

/** [keeper]'s [KeptNow], or null where there is none or it follows another [Keeper]. */
@Composable
@ReadOnlyComposable
internal fun keptNow(keeper: Keeper): KeptNow? = LocalKeptNow.current?.takeIf { it.keeper === keeper }

/**
 * [content] with what is saved and liked here followed once for all of its
 * bookmarks and hearts ([KeptNow]), from the first one drawn.
 */
@Composable
internal fun SharedKeep(content: @Composable () -> Unit) {
    val keeper = LocalKeeper.current
    val kept = remember(keeper) { KeptNow(keeper) }
    if (kept.savedWanted) FollowSaved(kept)
    if (kept.likedWanted) FollowLiked(kept)
    CompositionLocalProvider(LocalKeptNow provides kept, content = content)
}

@Composable
private fun FollowSaved(kept: KeptNow) {
    val library = kept.keeper.saved.index.collectAsStateWithLifecycle()
    LaunchedEffect(kept) { kept.keeper.saved.index() }
    SideEffect { kept.saved = library }
}

@Composable
private fun FollowLiked(kept: KeptNow) {
    val liked = remember(kept) { LikedIds.of(kept.keeper).ids }.collectAsStateWithLifecycle()
    SideEffect { kept.liked = liked }
}
