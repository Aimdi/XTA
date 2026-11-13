package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextAttachment
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.post.PostPrefs
import java.time.Duration
import java.time.Instant

/**
 * One post's translate control as a card draws it: whether it is [offered],
 * where its translation stands ([state], in the reader's language), and the
 * two ways to go between the post's words and Meta's translation of them.
 */
internal class ThreadsTranslationView(
    val offered: Boolean,
    val state: ThreadsTranslated?,
    val translate: () -> Unit,
    val showOriginal: () -> Unit,
) {
    /** The translated words while they are shown. */
    val shown: String? get() = state?.shown

    /** [post] as its card draws it: Meta's translation in place of its words while it is shown. */
    fun applied(post: ThreadsPost): ThreadsPost = shown?.let { post.copy(text = it, fragments = emptyList()) } ?: post
}

/** [post]'s translate control, in the language the app translates into (its setting, else the reader's). */
@Composable
internal fun rememberThreadsTranslation(app: ThreadsApp, post: ThreadsPost): ThreadsTranslationView {
    val settings by app.prefs.collectAsState()
    val states by app.translations.state.collectAsState()
    val available by app.translations.available.collectAsState()
    val target = PostPrefs.of(settings).translationTarget(currentLanguage())
    val signedIn = ThreadsPeopleView.signedIn(ThreadsSessions.of(settings))
    val offered = ThreadsTranslationRules.offers(post, target, signedIn, available)
    val state = states[ThreadsTranslations.key(post)]?.takeIf { it.target == target }
    return ThreadsTranslationView(offered, state, { app.translations.translate(post, target) }, { app.translations.showOriginal(post) })
}

/**
 * Under the words: "Translate" on the opened post (and on a card once its
 * translation was shown and put away), "Translating…" while Meta is asked,
 * "Translated by Threads" with Show original, or why there is none with
 * Retry. A card offers Translate first from its sheet.
 */
@Composable
internal fun TranslationLine(view: ThreadsTranslationView, focal: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (view.state?.status) {
        TranslationStatus.TRANSLATING -> Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.translating), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        TranslationStatus.TRANSLATED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(PostIcons.Translate, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.translated_by_threads), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = view.showOriginal, modifier = Modifier.heightIn(min = 48.dp)) { Text(pluginString(Strings.show_original)) }
        }
        TranslationStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pluginString(Strings.translation_failed), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f, fill = false))
            if (view.offered) TextButton(onClick = view.translate, modifier = Modifier.heightIn(min = 48.dp)) { Text(pluginString(Strings.retry)) }
        }
        TranslationStatus.ORIGINAL, null -> if (view.offered && (focal || view.state != null)) {
            TextButton(onClick = view.translate, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(PostIcons.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.translate), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * The long text attached to a post, boxed under its words with "Attached
 * text" over it: cut short with Show more in a list, whole on the opened
 * post (where it can be selected), Show less folding it again either way.
 */
@Composable
internal fun TextAttachmentBlock(app: ThreadsApp, post: ThreadsPost, attachment: ThreadsTextAttachment, focal: Boolean, modifier: Modifier) {
    var expanded by rememberSaveable(post.id, focal) { mutableStateOf(focal) }
    val long = ThreadsContentRules.attachmentFolds(attachment.text)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape).padding(start = 12.dp, end = 12.dp, top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(PostIcons.TextSnippet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.attached_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        attachment.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
        ThreadsRichText(
            app,
            attachment.text,
            emptyList(),
            modifier = Modifier.padding(top = 6.dp, bottom = if (long) 0.dp else 10.dp),
            maxLines = if (long && !expanded) ThreadsContentRules.ATTACHMENT_LINES else Int.MAX_VALUE,
            selectable = focal,
        )
        attachment.linkUrl?.let { ThreadsRichText(app, it, emptyList(), modifier = Modifier.padding(bottom = 10.dp), maxLines = 1) }
        if (long) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(pluginString(if (expanded) Strings.show_less else Strings.show_more))
            }
        }
    }
}

/** "Ghost post · disappears in 3 hours" over a post Threads takes down after a day; "Ghost post" once its time is not known or past. */
@Composable
internal fun GhostLine(post: ThreadsPost, modifier: Modifier) {
    val ghost = post.facts.ghost ?: return
    val text = when (val left = ThreadsContentRules.ghostLeft(ghost.left(Instant.now()))) {
        is ThreadsGhostLeft.Hours -> pluginPlural(Strings.ghost_post_hours, left.hours, left.hours)
        is ThreadsGhostLeft.Minutes -> pluginPlural(Strings.ghost_post_minutes, left.minutes, left.minutes)
        ThreadsGhostLeft.Unknown -> pluginString(Strings.ghost_post)
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(PostIcons.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = muted)
    }
}

/** The trends a post is part of, each a chip opening its search, as threads.com marks a post in a trend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrendChips(app: ThreadsApp, post: ThreadsPost, modifier: Modifier) {
    val trends = post.facts.trends.takeIf { it.isNotEmpty() } ?: return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        trends.forEach { trend ->
            val said = pluginString(Strings.post_trend, trend.title)
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = said) { app.openSearch(trend.terms) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PostIcons.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(said, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** How long a ghost post has left, as its line words it. */
internal sealed interface ThreadsGhostLeft {
    data class Hours(val hours: Int) : ThreadsGhostLeft

    data class Minutes(val minutes: Int) : ThreadsGhostLeft

    data object Unknown : ThreadsGhostLeft
}

/** The rules of a post's attached text and ghost mark that need no screen. */
internal object ThreadsContentRules {
    /** The lines attached text shows in a list before Show more. */
    const val ATTACHMENT_LINES = 6

    private const val ATTACHMENT_CHARS = 320

    /** Whether attached [text] is folded in a list: past [ATTACHMENT_CHARS] characters, or more than [ATTACHMENT_LINES] lines. */
    fun attachmentFolds(text: String): Boolean = text.length > ATTACHMENT_CHARS || text.count { it == '\n' } >= ATTACHMENT_LINES

    /**
     * [left] as a ghost post's line says it: whole hours (rounded) from an
     * hour on, minutes (rounded up, at least one) under it, and nothing
     * once it is past or not known.
     */
    fun ghostLeft(left: Duration?): ThreadsGhostLeft {
        val seconds = left?.seconds?.takeIf { it > 0 } ?: return ThreadsGhostLeft.Unknown
        return when {
            seconds >= 3600 -> ThreadsGhostLeft.Hours(((seconds + 1800) / 3600).toInt())
            else -> ThreadsGhostLeft.Minutes(((seconds + 59) / 60).toInt().coerceAtLeast(1))
        }
    }
}
