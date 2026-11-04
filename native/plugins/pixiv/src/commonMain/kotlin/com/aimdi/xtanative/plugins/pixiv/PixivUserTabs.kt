package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivUserListKind
import com.aimdi.xtanative.networks.pixiv.PixivUserPreview
import com.aimdi.xtanative.networks.pixiv.PixivUserProfile
import com.aimdi.xtanative.networks.pixiv.PixivWorkType
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.saved.SavedIcons
import java.text.NumberFormat
import java.time.MonthDay
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/** One tab of a profile, offered when [offeredFor] says so. */
internal class ProfileTab(val id: String, val label: PluginText, val offeredFor: (PixivProfileScope) -> Boolean = { true }, val content: @Composable (PixivProfileScope) -> Unit)

internal val profileTabs = listOf(
    ProfileTab("works", Strings.profile_works) { ProfileWorks(it.profile) },
    ProfileTab("novels", Strings.profile_novels, { it.own || it.profile.totalNovels > 0 }) { ProfileNovels(it.profile.id) },
    ProfileTab("bookmarks", Strings.tab_bookmarks) { ProfileBookmarks(it.profile.id) },
    ProfileTab("following", Strings.profile_following) { ProfileFollowing(it.profile.id) },
    ProfileTab("info", Strings.profile_info) { ProfileInfo(it.profile) },
)

/** A creator's own list, under the reader's mutes but never hiding the creator themselves. */
@Composable
private fun <T : Any> rememberCreatorList(key: String, creatorId: Long, id: (T) -> Any, visible: (PixivMutes, List<T>) -> List<T>, read: suspend (String?) -> Page<T>): PixivList<T> {
    val runtime = LocalPixiv.current
    return rememberKept(key) { scope -> PixivList(scope, id, read) { visible(runtime.mutes.now.showing(creatorId), it) } }
}

@Composable
private fun CreatorWorks(key: String, creatorId: Long, empty: PluginText, read: suspend (String?) -> Page<PixivIllust>) {
    val list = rememberCreatorList<PixivIllust>(key, creatorId, { it.id }, { mutes, works -> mutes.filter(works) }, read)
    PixivWorksGrid(list, pluginString(empty), mutes = { it.showing(creatorId) })
}

@Composable
private fun CreatorNovels(key: String, creatorId: Long, empty: PluginText, read: suspend (String?) -> Page<PixivNovel>) {
    val list = rememberCreatorList<PixivNovel>(key, creatorId, { it.id }, { mutes, novels -> mutes.filterNovels(novels) }, read)
    PixivNovelFeed(list, pluginString(empty), mutes = { it.showing(creatorId) })
}

/** Illustrations or manga, with a switch when they have both. */
@Composable
private fun ProfileWorks(profile: PixivUserProfile) {
    val runtime = LocalPixiv.current
    var type by rememberSaveable { mutableStateOf(profile.defaultWorkType) }
    Column(Modifier.fillMaxSize()) {
        if (profile.hasBothWorkTypes) {
            PixivSwitch(PixivWorkType.entries, type, { pluginString(if (it == PixivWorkType.MANGA) Strings.profile_manga else Strings.profile_illusts) }) { type = it }
        }
        Box(Modifier.weight(1f)) {
            CreatorWorks("pixiv-user-${profile.id}-${type.api}", profile.id, Strings.profile_works_empty) { next -> runtime.social.userWorks(profile.id, type, next) }
        }
    }
}

/** Their novels, or the novels they bookmarked. */
@Composable
private fun ProfileNovels(userId: Long) {
    val runtime = LocalPixiv.current
    var bookmarks by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PixivSwitch(listOf(false, true), bookmarks, { pluginString(if (it) Strings.tab_bookmarks else Strings.profile_works) }) { bookmarks = it }
        Box(Modifier.weight(1f)) {
            if (bookmarks) NovelBookmarks(userId) else CreatorNovels("pixiv-user-$userId-novels", userId, Strings.profile_novels_empty) { next -> runtime.novels.userNovels(userId, next) }
        }
    }
}

@Composable
private fun NovelBookmarks(userId: Long) {
    val runtime = LocalPixiv.current
    CreatorNovels("pixiv-user-$userId-novel-bookmarks", userId, Strings.profile_novel_bookmarks_empty) { next -> runtime.novels.bookmarks(userId, "public", next) }
}

/** The works or the novels they bookmarked publicly. */
@Composable
private fun ProfileBookmarks(userId: Long) {
    val runtime = LocalPixiv.current
    var novels by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PixivSwitch(listOf(false, true), novels, { pluginString(if (it) Strings.profile_novels else Strings.profile_illusts) }) { novels = it }
        Box(Modifier.weight(1f)) {
            if (novels) {
                NovelBookmarks(userId)
            } else {
                CreatorWorks("pixiv-user-$userId-bookmarks", userId, Strings.profile_bookmarks_empty) { next -> runtime.social.userBookmarks(userId, "public", null, next) }
            }
        }
    }
}

@Composable
private fun ProfileFollowing(userId: Long) {
    val runtime = LocalPixiv.current
    val list = rememberKept("pixiv-user-$userId-following") { scope -> PixivList<PixivUserPreview>(scope, { it.user.id }, { next -> runtime.social.userFollowing(userId, "public", next) }) }
    PixivUsersFeed(list, pluginString(Strings.profile_following_empty))
}

/** What a row of Info does when tapped. */
private sealed interface InfoAction {
    data class Copy(val text: String) : InfoAction

    data class Link(val url: String) : InfoAction

    data class List(val kind: PixivUserListKind) : InfoAction
}

private class InfoRow(val label: PluginText, val value: String, val action: InfoAction? = null)

/** Every profile field Pixiv shares, in XTA's order: names and numbers, then the personal ones and their links. */
private fun infoRows(profile: PixivUserProfile, language: String): List<InfoRow> {
    val number = NumberFormat.getIntegerInstance(Locale.forLanguageTag(language))
    val count = { n: Long -> number.format(n) }
    val text = { label: PluginText, value: String -> value.takeIf { it.isNotEmpty() }?.let { InfoRow(label, it) } }
    return listOfNotNull(
        InfoRow(Strings.profile_nickname, profile.user.name),
        InfoRow(Strings.profile_user_id, "${profile.id}", InfoAction.Copy("${profile.id}")),
        InfoRow(Strings.profile_following, count(profile.user.followingCount), InfoAction.List(PixivUserListKind.FOLLOWING)),
        InfoRow(Strings.profile_followers, "", InfoAction.List(PixivUserListKind.FOLLOWERS)),
        InfoRow(Strings.profile_mypixiv, count(profile.user.mypixivCount)),
        InfoRow(Strings.profile_illusts, count(profile.totalIllusts)),
        InfoRow(Strings.profile_manga, count(profile.totalManga)),
        if (profile.totalNovels > 0) InfoRow(Strings.profile_novels, count(profile.totalNovels)) else null,
        InfoRow(Strings.profile_public_bookmarks, count(profile.publicBookmarks)),
        text(Strings.profile_gender, profile.gender),
        text(Strings.profile_region, profile.region),
        text(Strings.profile_birthday, birthday(profile, language)),
        text(Strings.profile_job, profile.job),
    ) + links(profile)
}

private fun links(profile: PixivUserProfile): List<InfoRow> = listOfNotNull(
    profile.webpage.takeIf { it.isNotEmpty() }?.let { InfoRow(Strings.profile_website, it, InfoAction.Link(it)) },
    profile.twitterLink.takeIf { it.isNotEmpty() }?.let { link ->
        val account = profile.twitterAccount.removePrefix("@")
        InfoRow(Strings.profile_twitter, if (account.isEmpty()) link else "@$account", InfoAction.Link(link))
    },
    profile.pawooUrl.takeIf { it.isNotEmpty() }?.let { InfoRow(Strings.profile_pawoo, it, InfoAction.Link(it)) },
)

/** `MM-DD` and the year, as [language] writes a date; the year alone when the day is unknown. */
internal fun birthday(profile: PixivUserProfile, language: String): String {
    val year = profile.birthYear
    val parts = Regex("^(\\d{1,2})-(\\d{1,2})$").find(profile.birthDay)?.groupValues
    val day = parts?.let { runCatching { MonthDay.of(it[1].toInt(), it[2].toInt()) }.getOrNull() } ?: return year?.toString().orEmpty()
    val locale = Locale.forLanguageTag(language)
    if (year == null) return day.format(DateTimeFormatter.ofPattern(monthDayPattern(locale), locale))
    return runCatching { day.atYear(year).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)) }.getOrDefault("$year")
}

/** The month and day of [locale]'s long date, without its year. */
private fun monthDayPattern(locale: Locale): String {
    val long = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.LONG, null, IsoChronology.INSTANCE, locale)
    return long.replace(Regex("[^dM]*y+[^dM]*"), " ").trim().ifEmpty { "MMMM d" }
}

@Composable
private fun ProfileInfo(profile: PixivUserProfile) {
    val runtime = LocalPixiv.current
    val copy = rememberCopy()
    val rows = infoRows(profile, currentLanguage())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
        items(rows.size) { index ->
            val row = rows[index]
            InfoLine(row) { action ->
                when (action) {
                    is InfoAction.Copy -> copy(action.text, Strings.profile_id_copied)
                    is InfoAction.Link -> runtime.openHref(action.url)
                    is InfoAction.List -> runtime.open(PixivRoute.Users(action.kind, profile.id))
                }
            }
            if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun InfoLine(row: InfoRow, onAction: (InfoAction) -> Unit) {
    val action = row.action
    val base = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    Row((if (action != null) base.clickable { onAction(action) } else base).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(pluginString(row.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2f))
        Text(row.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(3f))
        Box(Modifier.size(20.dp)) {
            when (action) {
                is InfoAction.Copy -> Icon(PixivIcons.ContentCopy, null, Modifier.size(20.dp))
                is InfoAction.Link -> Icon(SavedIcons.OpenInNew, null, Modifier.size(20.dp))
                is InfoAction.List -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                null -> Unit
            }
        }
    }
}
