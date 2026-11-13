package com.aimdi.xtanative.ui.settings

/** One control the settings search finds: its section, the preference key it focuses, and its text as shown. */
data class SettingsControl(val section: SettingsSection, val key: String, val title: String, val description: String)

/** One entry of Settings' own list (a section, or a screen it opens): its text and what it opens. */
data class SettingsEntry(val target: String, val title: String, val description: String)

/** The search field at the top of Settings (settings.dart, settings_search_index.dart). */
object SettingsSearch {
    /** The query's words, lower-case. */
    fun words(query: String): List<String> = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /**
     * The controls holding every word of [query] in their title or
     * description; those whose title starts with the first word come first,
     * then by title.
     */
    fun controls(controls: List<SettingsControl>, query: String): List<SettingsControl> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val first = words.first()
        return controls
            .filter { control -> "${control.title} ${control.description}".lowercase().let { text -> words.all(text::contains) } }
            .sortedWith(compareBy<SettingsControl> { !it.title.lowercase().startsWith(first) }.thenBy { it.title })
    }

    /** The section entries whose title or description holds [query] as typed, as XTA matches them. */
    fun entries(entries: List<SettingsEntry>, query: String): List<SettingsEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return entries.filter { it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle) }
    }
}
