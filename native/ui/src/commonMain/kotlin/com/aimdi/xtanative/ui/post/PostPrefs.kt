package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.MediaGridLayout
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.ProfileTab
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.services.ai.XTranslator

/**
 * What the post and profile screens read of the reader's settings, as XTA's
 * tweet.dart, tweet_footer.dart, status.dart and profile.dart read them. The
 * defaults are XTA's, for a screen drawn before the settings are read.
 */
data class PostPrefs(
    val absoluteTimestamps: Boolean = false,
    val hideSensitive: Boolean = true,
    val alwaysShowSensitive: Boolean = false,
    /** An opened post is not clipped; a feed's always is. */
    val fullPostContents: Boolean = true,
    /** XTA's "improve non-confirmation bias": posts show no author outside their own profile. */
    val hideAuthors: Boolean = false,
    /** The + on the avatar of an author not followed. */
    val followBadge: Boolean = true,
    val threadedReplies: Boolean = true,
    val collapseReposts: Boolean = false,
    val zen: Boolean = false,
    val calm: Boolean = false,
    /** `system`, or the app's language such as `pt_BR`. */
    val locale: String = "system",
    /** Where shared links point; empty means x.com. */
    val shareBaseUrl: String = "",
    val cleanLinks: Boolean = true,
    val defaultProfileTab: ProfileTab = ProfileTab.POSTS,
    val gridColumns: Int = 3,
    val gridLayout: MediaGridLayout = MediaGridLayout.MASONRY,
    /** Settings › Accessibility's "Double-tap to like": off, as XTA has it, since a double-tap makes each single tap wait. */
    val doubleTapLike: Boolean = false,
) {
    /** Engagement counts are hidden in zen and calm mode. */
    val hideCounts: Boolean get() = zen || calm

    /** Sensitive media starts behind its gate: unless the reader chose always to show it. */
    val sensitiveHidden: Boolean get() = !alwaysShowSensitive && hideSensitive

    /** The address posts are shared as. */
    val shareBase: String get() = shareBaseUrl.trim().ifEmpty { "https://x.com" }

    /** The language posts are translated into, as a tag; [systemLocale] is the platform's (`de_DE`). */
    fun translationTarget(systemLocale: String): String = XTranslator.target(locale, systemLocale)

    companion object {
        fun of(settings: Settings): PostPrefs = PostPrefs(
            absoluteTimestamps = settings[PostsPrefs.absoluteTimestamps],
            hideSensitive = settings[PostsPrefs.hideSensitive],
            alwaysShowSensitive = settings[PostsPrefs.alwaysShowSensitiveMedia],
            fullPostContents = settings[PostsPrefs.fullPostContents],
            hideAuthors = settings[PostsPrefs.nonConfirmationBias],
            followBadge = settings[PostsPrefs.subscribeBadge],
            threadedReplies = settings[PostsPrefs.threadedReplies],
            collapseReposts = settings[PostsPrefs.collapseReposts],
            zen = settings[PostsPrefs.zenMode],
            calm = settings[PostsPrefs.calmMode],
            locale = settings[GeneralPrefs.locale],
            shareBaseUrl = settings[GeneralPrefs.shareBaseUrl],
            cleanLinks = settings[GeneralPrefs.cleanLinks],
            defaultProfileTab = settings[GeneralPrefs.defaultProfileTab],
            gridColumns = settings[MediaPrefs.gridColumns].coerceIn(1, 5),
            gridLayout = settings[MediaPrefs.gridLayout],
            doubleTapLike = settings[AccessibilityPrefs.doubleTapLike],
        )
    }
}
