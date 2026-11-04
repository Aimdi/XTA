package com.aimdi.xtanative.plugins.booru

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruErrorKind
import com.aimdi.xtanative.networks.booru.BooruPopularScale
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruQueryToken
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.BooruTagOperator
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import java.util.Locale

/** The reader's locale, for numbers and dates. */
@Composable
internal fun readerLocale(): Locale = Locale.forLanguageTag(currentLanguage())

/** What went wrong with a booru read, as XTA says it (booru_errors.dart). */
@Composable
internal fun errorText(error: Throwable?): String = errorMessage(error, currentLanguage())

/** [errorText] in [language], for what is said after the screen has moved on. */
internal fun errorMessage(error: Throwable?, language: String): String =
    when (if (error == null) BooruErrorKind.NETWORK else BooruErrorKind.of(error)) {
        BooruErrorKind.NETWORK -> Strings.error_network
        BooruErrorKind.UNAUTHORIZED -> Strings.error_unauthorized
        BooruErrorKind.FORBIDDEN -> Strings.error_forbidden
        BooruErrorKind.RATE_LIMITED -> Strings.error_rate_limited
        BooruErrorKind.UNAVAILABLE -> Strings.error_unavailable
        BooruErrorKind.NEEDS_CREDENTIALS -> Strings.error_needs_key
        BooruErrorKind.TOO_DEEP -> Strings.end_of_results
        BooruErrorKind.TAG_LIMIT -> Strings.error_tag_limit
        BooruErrorKind.TIMED_OUT -> Strings.error_timed_out
        BooruErrorKind.NOT_FOUND -> Strings.error_not_found
        BooruErrorKind.BAD_RESPONSE -> Strings.error_response
    }.get(language)

@Composable
internal fun engineLabel(engine: BooruEngine): String = pluginString(
    when (engine) {
        BooruEngine.DANBOORU -> Strings.engine_danbooru
        BooruEngine.MOEBOORU -> Strings.engine_moebooru
        BooruEngine.GELBOORU_V2 -> Strings.engine_gelbooru
        BooruEngine.E621 -> Strings.engine_e621
    },
)

@Composable
internal fun ratingLabel(rating: BooruRating): String = pluginString(
    when (rating) {
        BooruRating.GENERAL -> Strings.rating_general
        BooruRating.SENSITIVE -> Strings.rating_sensitive
        BooruRating.QUESTIONABLE -> Strings.rating_questionable
        BooruRating.EXPLICIT -> Strings.rating_explicit
    },
)

/** A group of tags' heading; null is a host that said no kinds. */
@Composable
internal fun kindLabel(category: BooruTagCategory?): String = pluginString(
    when (category) {
        BooruTagCategory.ARTIST -> Strings.tag_artist
        BooruTagCategory.COPYRIGHT -> Strings.tag_copyright
        BooruTagCategory.CHARACTER -> Strings.tag_character
        BooruTagCategory.SPECIES -> Strings.tag_species
        BooruTagCategory.GENERAL -> Strings.general
        BooruTagCategory.META -> Strings.tag_meta
        null -> Strings.tags
    },
)

@Composable
internal fun scaleLabel(scale: BooruPopularScale): String = pluginString(
    when (scale) {
        BooruPopularScale.DAY -> Strings.popular_day
        BooruPopularScale.WEEK -> Strings.popular_week
        BooruPopularScale.MONTH -> Strings.popular_month
    },
)

/** How a thumbnail is read aloud: a post or a video, and its first tags. */
@Composable
internal fun tileLabel(post: BooruPost): String {
    val tags = BooruFormats.labelTags(post).joinToString(", ", transform = BooruFormats::tagName)
    return pluginString(if (post.isVideo) Strings.tile_video_label else Strings.tile_label, tags)
}

/** Whether the screens are drawn dark: dim or true black. */
@Composable
internal fun darkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Text colour for a tag kind, after the usual booru colours, readable on light, dim and black surfaces; null keeps the body colour. */
@Composable
internal fun tagColor(category: BooruTagCategory?): Color? {
    val dark = darkTheme()
    return when (category) {
        BooruTagCategory.ARTIST -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        BooruTagCategory.COPYRIGHT -> if (dark) Color(0xFFD7A6F5) else Color(0xFF7B1FA2)
        BooruTagCategory.CHARACTER -> if (dark) Color(0xFF7ED68A) else Color(0xFF2E7D32)
        BooruTagCategory.SPECIES -> if (dark) Color(0xFFFFB74D) else Color(0xFFB34700)
        BooruTagCategory.META -> if (dark) Color(0xFFFFD54F) else Color(0xFF7A5C00)
        BooruTagCategory.GENERAL -> if (dark) Color(0xFF6CB8F5) else Color(0xFF0B5FBF)
        null -> null
    }
}

/**
 * A query token as text (booru_tag_style.dart): the operator and the metatag's
 * name set apart from the value, so `-` and `rating:` read at a glance. The
 * token is isolated left to right, so `-tag` never shows as `tag-` in a
 * right-to-left language.
 */
@Composable
internal fun tokenText(token: String, category: BooruTagCategory? = null): AnnotatedString {
    val colors = TokenColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onSurfaceVariant, tagColor(category))
    return buildAnnotatedString { appendToken(token, colors) }
}

/** A whole query, token by token. */
@Composable
internal fun queryText(query: String): AnnotatedString {
    val colors = TokenColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onSurfaceVariant, null)
    return buildAnnotatedString {
        BooruQuery.tokens(query).forEachIndexed { index, token ->
            if (index > 0) append("  ")
            appendToken(token, colors)
        }
    }
}

internal class TokenColors(val exclude: Color, val either: Color, val metatag: Color, val value: Color?)

private fun AnnotatedString.Builder.appendToken(token: String, colors: TokenColors) {
    val parsed = BooruQueryToken.parse(token)
    append('⁦')
    when (parsed.operator) {
        BooruTagOperator.EXCLUDE -> withStyle(SpanStyle(color = colors.exclude, fontWeight = FontWeight.Bold)) { append("− ") }
        BooruTagOperator.EITHER -> withStyle(SpanStyle(color = colors.either, fontWeight = FontWeight.Bold)) { append("~ ") }
        BooruTagOperator.NONE -> Unit
    }
    parsed.metatag?.let { name -> withStyle(SpanStyle(color = colors.metatag)) { append("$name:") } }
    withStyle(SpanStyle(color = colors.value ?: Color.Unspecified)) { append(parsed.value) }
    append('⁩')
}
