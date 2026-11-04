package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings

/**
 * The feeds read three of XTA's settings from the session: replies, reposts
 * and video autoplay. The settings are the truth; the session follows them.
 */
object FeedSwitches {
    /** [session] with its switches as [settings] hold them. */
    fun mirrored(session: Session, settings: Settings): Session = session.copy(
        includeReplies = settings[PostsPrefs.includeReplies],
        includeReposts = settings[PostsPrefs.includeReposts],
        autoplay = settings[MediaPrefs.autoplay],
    )

    /**
     * Before the settings held them, the prototype's own screen kept the
     * reply and repost switches in the session: one turned off there, and
     * never set in the settings, is carried over once.
     */
    fun adopted(settings: Settings, session: Session): Settings = listOf(
        PostsPrefs.includeReplies to session.includeReplies,
        PostsPrefs.includeReposts to session.includeReposts,
    ).fold(settings) { current, (pref, value) -> if (!value && !current.isSet(pref)) current.with(pref, false) else current }
}
