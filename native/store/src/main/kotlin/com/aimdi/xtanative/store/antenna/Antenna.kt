package com.aimdi.xtanative.store.antenna

/** Whose posts an antenna listens to. */
enum class AntennaScope(val stored: String) {
    /** All of X's search. */
    SEARCH("search"),

    /** Only the accounts the reader follows. */
    FOLLOWING("following"),
    ;

    companion object {
        /** Anything but `following` is a search antenna, as XTA reads it. */
        fun of(stored: String?): AntennaScope = if (stored == FOLLOWING.stored) FOLLOWING else SEARCH
    }
}

/**
 * An antenna (Misskey's word): a saved keyword listener that reads X's
 * newest posts with any of [includeTerms] and none of [excludeTerms].
 */
data class Antenna(
    val id: String,
    val name: String,
    val includeTerms: List<String>,
    val excludeTerms: List<String> = emptyList(),
    val scope: AntennaScope = AntennaScope.SEARCH,
    /** Epoch milliseconds; antennas are listed newest first. */
    val createdAt: Long,
) {
    companion object {
        /** What was typed in the editor, split as XTA splits it: at commas and line breaks, trimmed, empties dropped. */
        fun terms(typed: String?): List<String> = typed.orEmpty().split(',', '\n').map(String::trim).filter(String::isNotEmpty)

        /** Terms as the editor shows them again. */
        fun typed(terms: List<String>): String = terms.joinToString(", ")

        /** The editor's OK: a name, and at least one term to listen for. */
        fun canSave(name: String, includeTerms: List<String>): Boolean = name.isNotBlank() && includeTerms.isNotEmpty()
    }
}
