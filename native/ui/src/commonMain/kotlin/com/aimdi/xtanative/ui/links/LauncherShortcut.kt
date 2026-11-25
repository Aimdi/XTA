package com.aimdi.xtanative.ui.links

/**
 * The shortcuts a long press on the app's icon offers on Android
 * (app/src/main/res/xml/shortcuts.xml). Each starts the app with an action
 * of its own, [PREFIX] and its name.
 */
enum class LauncherShortcut {
    SEARCH,
    SAVED,
    SUBSCRIPTIONS,

    /** Not under the icon: where a tap on the downloads' notification leads. */
    DOWNLOADS,
    ;

    companion object {
        /** What every shortcut's action starts with; the rest names the shortcut. */
        const val PREFIX = "com.aimdi.xtanative.shortcut."

        /** Whether [action], an intent's, is a shortcut's: one this version has, or one a later version added. */
        fun isOne(action: String?): Boolean = action != null && action.startsWith(PREFIX)

        /** The shortcut of [action]; null for any other action, and for a shortcut this version does not have. */
        fun of(action: String?): LauncherShortcut? {
            val name = action?.takeIf(::isOne)?.removePrefix(PREFIX)
            return LauncherShortcut.entries.firstOrNull { it.name == name }
        }
    }
}
