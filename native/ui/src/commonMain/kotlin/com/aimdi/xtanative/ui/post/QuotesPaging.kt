package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.ReadRetries

/** One list of the screen, page after page. */
data class Paged<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = true,
    val problem: Problem? = null,
    val cursor: String? = null,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
) {
    /**
     * The failure this list may retry on its own (at X's reset for a rate
     * limit, on a short backoff for a passing one), its first page's or the
     * next one's; null while it is reading or has none.
     */
    val retryable: Problem? get() = if (loading || loadingMore) null else ReadRetries.recoverable(problem ?: moreProblem)
}
