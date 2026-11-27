package com.aimdi.xtanative.ui.settings

/**
 * Settings in a wide desktop window: two panes, the list of its sections at
 * the start (the settings home's rows) and the section picked beside it, as
 * a desktop's settings window, where a phone pushes each section's screen.
 */
object SettingsPanes {
    /** From this width of the screens' place (dp, beside the frame's navigation) Settings stands in two panes. */
    const val TWO_PANES_FROM = 1100f

    /** The list's width, in dp. */
    const val LIST_WIDTH = 320

    /** The list's row for App info, which is no section of its own: the settings home's last rows, as a page. */
    const val ABOUT = "about"

    /** The list's row for the plugin store, shown beside the list as the sections are. */
    const val PLUGIN_STORE = "plugin-store"

    /** Whether Settings stands in two panes: on the desktop, in a place [widthDp] wide. */
    fun twoPanes(desktop: Boolean, widthDp: Float): Boolean = desktop && widthDp >= TWO_PANES_FROM

    /** The list's rows in their order: the settings home's sections, the plugin store among them, then App info. */
    val rows: List<String> = SettingsSection.everyday.map { it.id } + SettingsSection.ACCOUNT.id + PLUGIN_STORE + SettingsSection.DATA.id +
        SettingsSection.advanced.map { it.id } + ABOUT

    /** The row ↑ ([by] -1) or ↓ (1) from [from]; null at either end, or from no row of the list. */
    fun step(from: String?, by: Int): String? {
        val index = rows.indexOf(from)
        if (index < 0) return null
        return rows.getOrNull(index + by)
    }

    /**
     * The row marked while page [shown] stands beside the list: its own, or,
     * for a page one of the list's opens (Sync, from Data), that one's.
     */
    fun marked(shown: String): String = when (shown) {
        SettingsSection.SYNC.id -> SettingsSection.DATA.id
        else -> shown.takeIf { it in rows } ?: SettingsSection.GENERAL.id
    }

    /** What is shown beside the list at first, or after [saved]: a page it can show, else General. */
    fun shown(saved: String?): String = saved?.takeIf { SettingsSection.of(it) != null || it == ABOUT || it == PLUGIN_STORE } ?: SettingsSection.GENERAL.id

    /**
     * The page last shown beside the list while the app runs: Settings opens
     * on it again however it is opened (the frame's row, Ctrl+comma, a menu),
     * as a desktop's settings window keeps its place.
     */
    var last: String? = null

    /** The page and control a route opens beside the list ([SettingsRoutes.section], Read aloud's); null for a screen of its own. */
    fun target(route: String): PaneTarget? {
        if (route == SettingsRoutes.SPEECH) return PaneTarget(SettingsSection.SPEECH.id, null)
        if (!route.startsWith("settings/")) return null
        val id = route.removePrefix("settings/").substringBefore('?')
        if (SettingsSection.of(id) == null) return null
        val focus = route.substringAfter("?focus=", "").ifEmpty { null }
        return PaneTarget(id, focus)
    }
}

/** A page shown beside Settings' list ([SettingsPanes]), by its id, and the control the search opened it for. */
data class PaneTarget(val id: String, val focus: String?)
