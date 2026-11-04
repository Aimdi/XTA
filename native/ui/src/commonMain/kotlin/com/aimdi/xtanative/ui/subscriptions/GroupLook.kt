package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.ui.common.Graphemes
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How a group's mark is chosen, as XTA stores it (`mark_style`). */
object GroupMarkStyle {
    /** The emoji when there is one, else the initial. */
    const val AUTO = 0

    /** The emoji, else the initial. */
    const val EMOJI = 1

    /** The icon picked, else the initial. */
    const val SYMBOL = 2

    /** A generated mark, which XTA draws as the initial. */
    const val GENERATED = 3

    fun coerce(raw: Int?): Int = raw?.takeIf { it in AUTO..GENERATED } ?: AUTO
}

/** What a group's mark shows. */
enum class GroupMarkKind { EMOJI, INITIAL, SYMBOL }

/** One member's face on a tile: its picture, or its initial on a colour of its own. */
data class MemberFace(val id: String, val name: String, val avatarUrl: String?)

/**
 * A group's look, as XTA draws it (group_identity.dart, group_mark_style.dart,
 * fallback_avatar.dart): its colour, made from its name until the reader picks
 * one, its mark (an emoji, an icon or its initial) and its members' faces.
 * Colours are ARGB longs, so nothing here needs Compose.
 */
object GroupLook {
    /** XTA's default icon: no icon of its own. */
    const val DEFAULT_ICON = """{"pack":"custom","key":"rss_feed"}"""

    /**
     * The icons the edit sheet offers, by XTA's keys, in XTA's order. The
     * `ac_unit_outlined` key draws Material's severe-cold icon, as in XTA.
     */
    val ICON_KEYS = listOf(
        "star", "favorite", "bookmark", "home", "work", "school", "sports_soccer", "music_note", "movie", "tv",
        "sports_esports", "palette", "brush", "camera_alt", "photo", "pets", "eco", "public", "flight", "directions_car",
        "train", "restaurant", "local_cafe", "shopping_bag", "attach_money", "code", "science", "health_and_safety", "tag", "bolt",
        "nightlight", "wb_sunny", "cloud", "groups", "person", "newspaper", "campaign", "interests", "auto_awesome", "forest",
        "trending_up", "show_chart", "account_balance", "gavel", "rocket_launch", "memory", "smart_toy", "terminal", "security", "menu_book",
        "edit_note", "mic", "headphones", "theater_comedy", "sports_basketball", "sports_tennis", "sports_motorsports", "directions_bike",
        "fitness_center", "hiking", "sailing", "terrain", "beach_access", "ac_unit", "local_florist", "local_bar", "local_pizza", "cake",
        "construction", "handyman", "architecture", "psychology", "church", "diversity_3", "volunteer_activism", "child_care",
        "celebration", "emoji_events", "casino", "extension", "toys", "watch", "checkroom", "diamond", "key", "lightbulb",
        "travel_explore", "map", "apartment", "anchor", "whatshot", "ac_unit_outlined", "water_drop", "recycling",
    )

    /**
     * The colours a group can be given: X's six accents, then the ones that
     * fill out the wheel where X leaves gaps (group_color_picker.dart).
     */
    val COLOR_CHOICES: List<Long> = listOf(
        0xFF1D9BF0, 0xFFFFD400, 0xFFF91880, 0xFF7856FF, 0xFFFF7A00, 0xFF00BA7C,
        0xFFF4212E, 0xFFFF6E40, 0xFF00BCD4, 0xFF7BC67E, 0xFFB388FF, 0xFF8D6E63, 0xFF536471, 0xFFE1E8ED,
    )

    /** The member faces' colours when they have no picture: never grey, which reads as broken. */
    val FACE_COLORS: List<Long> = listOf(
        0xFF1D9BF0, 0xFFF91880, 0xFF7856FF, 0xFFFF7A00, 0xFF00BA7C, 0xFFFFD400, 0xFFE0245E, 0xFF17BF63,
        0xFF794BC4, 0xFFFF6F00, 0xFF1B95E0, 0xFFF45D22, 0xFF2EC7C2, 0xFFE0457B, 0xFF5C6BC0, 0xFF26A69A,
    )

    /** How many members a tile shows. */
    const val FACES = 4

    private val letter = Regex("\\p{L}")
    private val letterOrDigit = Regex("[\\p{L}\\p{N}]")

    /** The colour made from [name], the same every time: XTA's hue from the name's code units. */
    fun fallbackColor(name: String): Long {
        val hash = name.fold(0L) { h, c -> h * 31 + c.code }
        return hsl(Math.floorMod(hash, 360L).toDouble(), 0.45, 0.55)
    }

    fun seedColor(group: FollowGroup): Long = group.color ?: fallbackColor(group.name)

    /** Flutter's HSLColor.toColor, opaque. */
    internal fun hsl(hue: Double, saturation: Double, lightness: Double): Long {
        val chroma = (1.0 - abs(2.0 * lightness - 1.0)) * saturation
        val secondary = chroma * (1.0 - abs(((hue / 60.0) % 2.0) - 1.0))
        val match = lightness - chroma / 2.0
        val (red, green, blue) = when {
            hue < 60 -> Triple(chroma, secondary, 0.0)
            hue < 120 -> Triple(secondary, chroma, 0.0)
            hue < 180 -> Triple(0.0, chroma, secondary)
            hue < 240 -> Triple(0.0, secondary, chroma)
            hue < 300 -> Triple(secondary, 0.0, chroma)
            else -> Triple(chroma, 0.0, secondary)
        }
        fun channel(value: Double) = ((value + match) * 255).roundToLong()
        return (0xFFL shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }

    /** Whether [argb] is dark enough for white on it, as Flutter estimates a colour's brightness. */
    fun isDark(argb: Long): Boolean {
        val luminance = luminance(argb)
        return (luminance + 0.05) * (luminance + 0.05) <= 0.15
    }

    private fun luminance(argb: Long): Double {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    /** The first grapheme of [text]: an emoji with its modifiers, a letter with its accents. */
    fun firstGrapheme(text: String): String = Graphemes.first(text.trim())

    /** A group's one letter: its first letter, else its first grapheme, else `?`. */
    fun initial(name: String): String {
        val graphemes = Graphemes.of(name.trim())
        return (graphemes.firstOrNull { letter.containsMatchIn(it) } ?: graphemes.firstOrNull() ?: "?").uppercase()
    }

    private fun hasEmoji(emoji: String?) = !emoji.isNullOrBlank()

    private fun hasIcon(icon: String?): Boolean {
        if (icon.isNullOrEmpty() || icon == DEFAULT_ICON || icon == "rss" || icon == "rss_feed") return false
        return iconKey(icon).let { it != null && it != "rss_feed" }
    }

    /** What a mark of [markStyle] shows with what the group holds. */
    fun markKind(markStyle: Int, emoji: String?, icon: String?): GroupMarkKind = when (GroupMarkStyle.coerce(markStyle)) {
        GroupMarkStyle.SYMBOL -> if (hasIcon(icon)) GroupMarkKind.SYMBOL else GroupMarkKind.INITIAL
        GroupMarkStyle.GENERATED -> GroupMarkKind.INITIAL
        else -> if (hasEmoji(emoji)) GroupMarkKind.EMOJI else GroupMarkKind.INITIAL
    }

    fun markKind(group: FollowGroup): GroupMarkKind = markKind(group.markStyle, group.emoji, group.icon)

    /** Whether the reader picked the mark, which then shows on the board in place of the members' faces. */
    fun hasChosenMark(group: FollowGroup): Boolean = when (GroupMarkStyle.coerce(group.markStyle)) {
        GroupMarkStyle.EMOJI, GroupMarkStyle.SYMBOL -> markKind(group) != GroupMarkKind.INITIAL
        else -> hasEmoji(group.emoji)
    }

    /** The icon's key in XTA's icon JSON (or a bare key); null when there is none. */
    fun iconKey(icon: String?): String? {
        val text = icon?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!text.startsWith("{")) return text
        return J.parse(text)["key"].string?.takeIf { it.isNotBlank() }
    }

    /** The icon JSON XTA stores for a picked icon. */
    fun iconJson(key: String): String = buildJsonObject {
        put("pack", "custom")
        put("key", key)
    }.toString()

    /** A face's colour when it has no picture, by its id, so a rename keeps it. */
    fun faceColor(seed: String): Long {
        val hash = seed.fold(0L) { h, c -> c.code + ((h shl 5) - h) }
        return FACE_COLORS[Math.floorMod(hash, FACE_COLORS.size.toLong()).toInt()]
    }

    /** A face's letter: the first letter or digit of [name], else of [seed], else `#`. */
    fun faceInitial(name: String, seed: String): String =
        (letterOrDigit.find(name) ?: letterOrDigit.find(seed))?.value?.uppercase() ?: "#"
}
