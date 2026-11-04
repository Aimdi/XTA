package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** One line of a sheet of actions. */
@Composable
internal fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** A bottom sheet of the plugin, closed through [sheets], scrolling when tall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PixivSheetFrame(sheets: PixivSheets, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = sheets::close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) { content() }
    }
}

/** Links that leave the app, and the share sheet where there is one; on the desktop a shared link is copied. */
internal class PixivOutside(private val openOutside: (String) -> Unit, private val share: (String) -> Boolean, private val copy: (String) -> Unit) {
    fun open(url: String) = openOutside(url)

    fun share(url: String) {
        if (!share.invoke(url)) copy(url)
    }
}

@Composable
internal fun rememberOutside(): PixivOutside {
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    return remember(links, uri, clipboard, runtime, language) {
        PixivOutside(
            openOutside = { url -> links?.openOutside(url) ?: uri.openUri(url) },
            share = { text -> links?.share(text) == true },
            copy = { text ->
                clipboard.setText(AnnotatedString(text))
                runtime.tell(Strings.link_copied.get(language))
            },
        )
    }
}

/** A work's long-press sheet: save, the bookmark kept here, links and info, and muting its creator or the work itself. */
@Composable
internal fun WorkActionsSheet(illust: PixivIllust, sheets: PixivSheets) {
    val runtime = LocalPixiv.current
    val marks by runtime.bookmarks.state.collectAsState()
    val bookmarked = marks.orEmpty().any { it.kind == PixivKind.WORK && it.kept.id == illust.id }
    val haptics = rememberHaptics()
    val copy = rememberCopy()
    val outside = rememberOutside()
    val settings = pixivSettings()
    val language = currentLanguage()
    PixivSheetFrame(sheets) {
        val pages = illust.viewerUrls.size
        SheetRow(SavedIcons.Download, pluginString(if (pages > 1) Strings.download_all else Strings.download), { sheets.close(); sheets.saveAll(illust) })
        SheetRow(if (bookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, pluginString(if (bookmarked) Strings.unbookmark else Strings.bookmark), {
            sheets.close()
            sheets.toggleBookmark(illust, haptics)
        })
        SheetRow(Icons.Filled.Edit, pluginString(Strings.bookmark_edit), { sheets.show(PixivSheet.EditWork(illust)) })
        SheetRow(XIcons.Link, pluginString(Strings.copy_link), { sheets.close(); copy(illust.url, Strings.link_copied) })
        SheetRow(PixivIcons.ContentCopy, pluginString(Strings.copy_info), {
            sheets.close()
            copy(PixivCopyInfo.filled(PixivCopyInfo.template(settings, defaultCopyTemplate(language)), illust), Strings.info_copied)
        })
        SheetRow(Icons.Filled.Share, pluginString(Strings.share_link), { sheets.close(); outside.share(illust.url) })
        SheetRow(PostIcons.OpenInNew, pluginString(Strings.open_on_pixiv), { sheets.close(); outside.open(illust.url) })
        HorizontalDivider()
        MuteRows(sheets, workMuteChoices(illust).take(2), leave = false)
    }
}

/** The page sheet a long press on [PixivSheet.PageActions.page] opens, or the reader's menu. */
@Composable
internal fun PageActionsSheet(sheet: PixivSheet.PageActions, sheets: PixivSheets) {
    val illust = sheet.illust
    val pages = illust.viewerUrls.size
    val copy = rememberCopy()
    PixivSheetFrame(sheets) {
        if (pages > 1) SectionTitle(pluginString(Strings.current_page, sheet.page + 1, pages))
        SheetRow(SavedIcons.Download, pluginString(Strings.download_page), { sheets.close(); sheets.savePages(illust, listOf(sheet.page)) })
        if (pages > 1) {
            SheetRow(SavedIcons.Download, pluginString(Strings.download_all), { sheets.close(); sheets.saveAll(illust) })
            SheetRow(SavedIcons.Checklist, pluginString(Strings.select_pages), { sheets.close(); sheet.surface.openOverview(selecting = true) })
            SheetRow(GroupIcons.GridView, pluginString(Strings.all_pages), { sheets.close(); sheet.surface.openOverview(selecting = false) })
        }
        if (illust.isUgoira && sheet.page == 0) {
            SheetRow(SavedIcons.Folder, pluginString(Strings.ugoira_save_zip), { sheets.close(); sheets.saveZip(illust) })
        }
        SheetRow(XIcons.Link, pluginString(Strings.copy_link), { sheets.close(); copy(illust.url, Strings.link_copied) })
        SheetRow(PostIcons.MoreHoriz, pluginString(Strings.more_actions), { sheets.show(PixivSheet.WorkActions(illust)) })
    }
}

/** Something the reader can mute, worded as the action it takes. */
internal class PixivMuteChoice(val icon: ImageVector, val label: String, val mute: (PixivMuteStore) -> Unit)

/** What a work offers to mute: its creator, the work itself and each of its tags. */
@Composable
internal fun workMuteChoices(illust: PixivIllust): List<PixivMuteChoice> {
    val language = currentLanguage()
    return remember(illust, language) {
        listOf(
            PixivMuteChoice(PixivIcons.VolumeOff, Strings.mute_author.get(language)) { it.muteAuthor(illust.userId, illust.userName) },
            PixivMuteChoice(PostIcons.VisibilityOff, Strings.mute_illust.get(language)) { it.muteIllust(illust.id) },
        ) + illust.tags.map { tag -> PixivMuteChoice(PixivIcons.Label, Strings.mute_tag.get(language, tag.displayName)) { it.muteTag(tag.name) } }
    }
}

/** A row per mute choice, each asking first; with [leave], the screen goes once something is muted. */
@Composable
internal fun MuteRows(sheets: PixivSheets, choices: List<PixivMuteChoice>, leave: Boolean) {
    val runtime = LocalPixiv.current
    choices.forEach { choice ->
        SheetRow(choice.icon, choice.label, {
            sheets.close()
            sheets.ask.ask(choice.label, choice.label) {
                choice.mute(runtime.mutes)
                if (leave) sheets.onMutedHere()
            }
        })
    }
}

/** A work's Mute sheet: its creator, the work and its tags; once one is muted the work's screen leaves. */
@Composable
internal fun MuteSheet(illust: PixivIllust, sheets: PixivSheets) {
    PixivSheetFrame(sheets) { MuteRows(sheets, workMuteChoices(illust), leave = true) }
}

/** A tag long-pressed: mute it, pin it to the favourite tags or take it off, or copy it. */
@Composable
internal fun TagSheet(sheet: PixivSheet.Tag, sheets: PixivSheets) {
    val runtime = LocalPixiv.current
    val tag = sheet.tag
    val favorite = PixivFavoriteTags.contains(pixivSettings(), tag.name)
    val copy = rememberCopy()
    val language = currentLanguage()
    PixivSheetFrame(sheets) {
        Text(
            buildAnnotatedString {
                append("#" + tag.name)
                tag.translation?.let { withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("  $it") } }
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        val mute = PixivMuteChoice(PixivIcons.Label, Strings.mute_tag.get(language, tag.displayName)) { it.muteTag(tag.name) }
        MuteRows(sheets, listOf(mute), leave = sheet.leaveOnMute)
        SheetRow(if (favorite) Icons.Filled.Star else Icons.Outlined.Star, pluginString(if (favorite) Strings.search_favorite_remove else Strings.search_favorite_add), {
            sheets.close()
            runtime.settings.update { if (favorite) PixivFavoriteTags.removed(it, tag.name) else PixivFavoriteTags.added(it, tag) }
            runtime.tell((if (favorite) Strings.search_favorite_removed else Strings.search_favorite_added).get(language))
        })
        SheetRow(PixivIcons.ContentCopy, pluginString(Strings.search_tag_copy), { sheets.close(); copy(tag.name, Strings.search_tag_copied) })
    }
}

/** A novel's long-press sheet: the bookmark kept here, links, and muting its author or the novel. */
@Composable
internal fun NovelActionsSheet(novel: PixivNovel, sheets: PixivSheets) {
    val runtime = LocalPixiv.current
    val marks by runtime.bookmarks.state.collectAsState()
    val bookmarked = marks.orEmpty().any { it.kind == PixivKind.NOVEL && it.kept.id == novel.id }
    val haptics = rememberHaptics()
    val copy = rememberCopy()
    val outside = rememberOutside()
    val language = currentLanguage()
    PixivSheetFrame(sheets) {
        SheetRow(if (bookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, pluginString(if (bookmarked) Strings.unbookmark else Strings.bookmark), {
            sheets.close()
            sheets.toggleBookmark(novel, haptics)
        })
        SheetRow(Icons.Filled.Edit, pluginString(Strings.bookmark_edit), { sheets.show(PixivSheet.EditNovel(novel)) })
        SheetRow(XIcons.Link, pluginString(Strings.copy_link), { sheets.close(); copy(novel.url, Strings.link_copied) })
        SheetRow(Icons.Filled.Share, pluginString(Strings.share_link), { sheets.close(); outside.share(novel.url) })
        SheetRow(PostIcons.OpenInNew, pluginString(Strings.open_on_pixiv), { sheets.close(); outside.open(novel.url) })
        HorizontalDivider()
        val choices = listOf(
            PixivMuteChoice(PixivIcons.VolumeOff, Strings.mute_author.get(language)) { it.muteAuthor(novel.user.id, novel.user.name) },
            PixivMuteChoice(PostIcons.VisibilityOff, Strings.mute_novel.get(language)) { it.muteNovel(novel.id) },
        )
        MuteRows(sheets, choices, leave = false)
        SheetRow(Icons.AutoMirrored.Filled.KeyboardArrowRight, pluginString(Strings.novel_author_novels), {
            sheets.close()
            runtime.open(PixivRoute.User(novel.user.id, "novels"))
        })
    }
}

/** XTA's copy template, worded in [language]: title, artist and work ID. */
internal fun defaultCopyTemplate(language: String): String = Strings.copy_template_default.get(language, "{title}", "{user_name}", "{illust_id}")
