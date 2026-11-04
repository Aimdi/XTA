package com.aimdi.xtanative.ui.home

/**
 * Mastodon, Bluesky and Threads as one Home entry (alt_microblogging.dart):
 * while Settings › Plugin store's "Group microblogging services" is on, the
 * timeline picker and the Networks sheet list them under one entry, which
 * opens the one shown, else the one opened last, else the first; their
 * timelines' header switches between them. Only what is listed changes: the
 * pins, the order and every network's own data stay as they are.
 */
object AltMicroblogging {
    /** The entry's id where the networks' ids are listed. */
    const val SECTION = "alt-microblogging"

    /** Its members, by plugin id, in XTA's order. */
    val MEMBERS = listOf("mastodon", "bluesky", "threads")

    fun isMember(id: String?): Boolean = id in MEMBERS

    /** [ids] as listed: with [grouped], the members give way to one [SECTION] where the first of them stood. */
    fun listed(ids: List<String>, grouped: Boolean): List<String> {
        if (!grouped) return ids
        val listed = ArrayList<String>()
        ids.forEach { id ->
            when {
                !isMember(id) -> listed += id
                SECTION !in listed -> listed += SECTION
            }
        }
        return listed
    }

    /** The members among [ids], in their order. */
    fun members(ids: List<String>): List<String> = ids.filter(::isMember)

    /** Where the entry leads: the member shown ([selected]), else the one opened last ([remembered]), else the first of [ids]. */
    fun destination(ids: List<String>, selected: String?, remembered: String?): String? {
        val members = members(ids)
        return selected?.takeIf { it in members } ?: remembered?.takeIf { it in members } ?: members.firstOrNull()
    }

    /**
     * The Networks sheet's two sections (network_switcher.dart): those opened
     * lately ([recent], newest first), then the rest in the bar's order
     * ([networks]); with [grouped], the members as one entry, recent when one
     * of them is.
     */
    fun sections(networks: List<String>, recent: List<String>, grouped: Boolean): Pair<List<String>, List<String>> {
        val lately = recent.distinct().filter { it in networks }
        val ids = listed(lately + networks.filter { it !in lately }, grouped)
        val isRecent = { id: String -> if (id == SECTION) lately.any(::isMember) else id in lately }
        return ids.filter(isRecent) to ids.filterNot(isRecent)
    }

    /** What is kept as the one opened last when [id] opens: a member, or nothing new. */
    fun remembered(id: String, last: String): String = if (isMember(id)) id else last
}
