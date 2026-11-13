package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedPost
import kotlin.coroutines.cancellation.CancellationException

/** What checking one saved post against X found (cleanup_dialog.dart). */
enum class CleanupCheck {
    /** Still on X, or not an X post. */
    OK,

    /** X answered without it: deleted. */
    BROKEN,

    /** The check failed for a passing reason; the post is kept. */
    UNREACHABLE,

    /** X is rate limiting: the scan stops, and what it did not check is kept. */
    RATE_LIMITED,
}

/**
 * Find deleted posts (saved_cleanup.dart): each saved X post is read again,
 * and one X answers without is listed to remove. Posts of other networks are
 * kept without asking X about them.
 */
object SavedCleanup {
    /** The time between two checks, so the scan reads X gently. */
    const val PAUSE_MS = 200L

    suspend fun check(post: SavedPost, read: suspend (id: String) -> XConversation): CleanupCheck {
        if (!isXPost(post)) return CleanupCheck.OK
        return try {
            if (read(post.id).focal?.id == post.id) CleanupCheck.OK else CleanupCheck.BROKEN
        } catch (e: CancellationException) {
            throw e
        } catch (_: XRateLimitedException) {
            CleanupCheck.RATE_LIMITED
        } catch (_: Exception) {
            CleanupCheck.UNREACHABLE
        }
    }

    /** X's ids are numbers; other networks' are prefixed (`reddit:`, `mastodon:`…). */
    fun isXPost(post: SavedPost): Boolean = post.id.all { it.isDigit() } && post.about.kind.let { it == SavedContent.Kind.X || it == SavedContent.Kind.NONE }

    /** How the list names a deleted post: its subreddit and title, or its author and the start of its text, or its id. */
    fun label(post: SavedPost): String {
        val content = post.about
        val json = content.post
        if (content.kind == SavedContent.Kind.REDDIT) return "r/${json["subreddit"].string.orEmpty()}: ${json["title"].string.orEmpty()}"
        val screenName = json["user"]["screen_name"].string
        val text = json["full_text"].string ?: json["text"].string ?: ""
        val snippet = if (text.length > 50) text.take(50) + "…" else text
        return when {
            screenName != null && snippet.isEmpty() -> "@$screenName"
            screenName != null -> "@$screenName: $snippet"
            snippet.isNotEmpty() -> snippet
            else -> post.id
        }
    }
}

/** Where a scan of [total] posts stands: how many were checked, those found deleted, and those that could not be. */
data class CleanupScan(
    val total: Int,
    val checked: Int = 0,
    val broken: List<SavedPost> = emptyList(),
    val unreachable: Int = 0,
    val done: Boolean = false,
) {
    val fraction: Float get() = if (total == 0) 0f else checked.toFloat() / total

    /** After [post] was checked; a rate limit ends the scan with the rest kept. */
    fun after(post: SavedPost, check: CleanupCheck): CleanupScan = when (check) {
        CleanupCheck.OK -> copy(checked = checked + 1)
        CleanupCheck.BROKEN -> copy(checked = checked + 1, broken = broken + post)
        CleanupCheck.UNREACHABLE -> copy(checked = checked + 1, unreachable = unreachable + 1)
        CleanupCheck.RATE_LIMITED -> finished()
    }

    /** The scan over: what it did not reach counts as kept unchecked. */
    fun finished(): CleanupScan = copy(unreachable = unreachable + (total - checked), done = true)
}
