package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * The settings whose words name a gesture, in this platform's: a click, a
 * right-click or a drag with the desktop's mouse, where a phone's say a
 * tap, a long press or a swipe. Both the pages and the settings search
 * read them, so a search finds the words the page shows.
 */
internal object GestureWording {
    val avatarChange = pick(Res.string.settings_avatar_change, Res.string.settings_avatar_change_desktop)
    val zenModeDescription = pick(Res.string.settings_zen_mode_description, Res.string.settings_zen_mode_description_desktop)
    val loadManuallyDescription = pick(Res.string.settings_load_manually_description, Res.string.settings_load_manually_description_desktop)
    val doubleTapLike = pick(Res.string.settings_double_tap_like, Res.string.settings_double_tap_like_desktop)
    val doubleTapLikeDescription = pick(Res.string.settings_double_tap_like_description, Res.string.settings_double_tap_like_description_desktop)
    val swipeBackAnywhere = pick(Res.string.settings_swipe_back_anywhere, Res.string.settings_swipe_back_anywhere_desktop)
    val swipeBackAnywhereDescription = pick(Res.string.settings_swipe_back_anywhere_description, Res.string.settings_swipe_back_anywhere_description_desktop)
    val swipeBackTilt = pick(Res.string.settings_swipe_back_tilt, Res.string.settings_swipe_back_tilt_desktop)
    val swipeBackTiltDescription = pick(Res.string.settings_swipe_back_tilt_description, Res.string.settings_swipe_back_tilt_description_desktop)
    val translationPrivacy = pick(Res.string.translation_privacy, Res.string.translation_privacy_desktop)

    private fun pick(touch: StringResource, desktop: StringResource) = if (isDesktop) desktop else touch
}
