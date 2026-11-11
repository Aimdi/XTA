package com.aimdi.xtanative.ui.plugins

/**
 * Whether a plugin's own tabs fit where they are drawn, as XTA's plugin
 * chrome decides it (plugin_home_chrome.dart). Widths are in dp.
 */
object PluginTabFit {
    /** The least a tab is wide: a touch target. */
    const val TARGET = 48f

    /** What a labelled tab adds to its label: its icon, the gap after it and its padding. */
    const val LABEL_CHROME = 52f

    /** The strip's own padding at its ends. */
    const val STRIP_PADDING = 8f

    /** Under this width a folded strip shows its section's icon alone. */
    const val ICON_ONLY_BELOW = 104f

    /** Whether tabs with these [labels]' widths fit [width] as icon and label buttons; they fold into the section picker when not (_tabsFit). */
    fun labelled(labels: List<Float>, width: Float): Boolean = STRIP_PADDING + labels.sumOf { maxOf(TARGET, it + LABEL_CHROME).toDouble() } <= width

    /** Whether [count] tabs fit [width] as icons alone, each a touch target (PluginCompactTabs). */
    fun icons(count: Int, width: Float): Boolean = width >= count * TARGET

    /** Whether a folded strip in [width] names its section, or shows its icon alone. */
    fun namesSection(width: Float): Boolean = width >= ICON_ONLY_BELOW
}
