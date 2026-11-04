package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.tiktok.TikTokClient
import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.networks.tiktok.TikTokVideoSource
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.problemOf

/** XTA's words for why a read of TikTok brought nothing (tiktok_errors.dart). */
internal object TikTokText {
    fun error(error: Throwable): PluginText = problem(problemOf(error))

    /** A rate limit, an account or video TikTok does not have, an answer that did not read; anything else reads as no connection, as in XTA. */
    fun problem(problem: Problem): PluginText = when (problem) {
        Problem.RateLimited -> Strings.error_rate_limited
        is Problem.Refused -> if (problem.code == TikTokClient.NOT_FOUND) Strings.error_not_found else Strings.error_response
        else -> Strings.error_network
    }
}

/** The files a video plays from, as XTA's player offers them: its renditions, the download copy only when there is nothing else. */
internal fun renditionsOf(post: TikTokPost): List<TikTokVideoSource> =
    post.sources.filter { it.label != DOWNLOAD }.ifEmpty { post.sources.take(1) }

private const val DOWNLOAD = "download"
