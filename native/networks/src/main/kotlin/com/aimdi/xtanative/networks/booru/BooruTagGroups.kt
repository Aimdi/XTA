package com.aimdi.xtanative.networks.booru

/** How a post's tags are ordered within their groups: by name, or most used first. */
enum class BooruTagSort { NAME, COUNT }

/** A post's tags of one kind; a null [category] is one group for a host that said no kinds. */
data class BooruTagGroup(val category: BooruTagCategory?, val tags: List<String>)

/** A post's tags grouped by kind, the way the hosts list them under a post. */
object BooruTagGroups {
    /** The order the hosts list kinds in. */
    val ORDER: List<BooruTagCategory> = listOf(
        BooruTagCategory.ARTIST,
        BooruTagCategory.COPYRIGHT,
        BooruTagCategory.CHARACTER,
        BooruTagCategory.SPECIES,
        BooruTagCategory.GENERAL,
        BooruTagCategory.META,
    )

    /** Artist tags that name no artist. */
    private val NOT_AN_ARTIST = setOf(
        "anonymous_artist", "artist_request", "avoid_posting", "banned_artist", "conditional_dnp", "sound_warning",
        "third-party_edit", "unknown_artist", "unknown_artist_signature",
    )

    /**
     * [tags] under their kinds from [info] ([BooruClient.tagInfo]), each group sorted by [sort]. With
     * no kinds known they stay one group; a tag whose kind is missing counts as general. By count,
     * the tags the host gave no count for go last.
     */
    fun of(tags: List<String>, info: Map<String, BooruTagInfo>, sort: BooruTagSort = BooruTagSort.NAME): List<BooruTagGroup> {
        if (tags.isEmpty()) return emptyList()
        val sorted = when (sort) {
            BooruTagSort.NAME -> tags.sorted()
            BooruTagSort.COUNT -> tags.sortedWith(compareByDescending<String> { info[it]?.postCount ?: -1 }.thenBy { it })
        }
        if (tags.none { info[it]?.category != null }) return listOf(BooruTagGroup(null, sorted))
        val byKind = sorted.groupBy { info[it]?.category ?: BooruTagCategory.GENERAL }
        return ORDER.mapNotNull { kind -> byKind[kind]?.let { BooruTagGroup(kind, it) } }
    }

    /** The post's first real artist tag, for "More from". */
    fun artistTag(tags: List<String>, info: Map<String, BooruTagInfo>): String? =
        tags.firstOrNull { info[it]?.category == BooruTagCategory.ARTIST && it !in NOT_AN_ARTIST }
}
