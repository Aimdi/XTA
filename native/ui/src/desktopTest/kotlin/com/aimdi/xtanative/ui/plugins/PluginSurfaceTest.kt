package com.aimdi.xtanative.ui.plugins

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the plugins use of the app beyond its plugin API (`ui.plugins`), in
 * `ui.*` and `data.*`: these
 * are part of the contract with installed plugins as much as the API is, since
 * a plugin is compiled against their names. A plugin that starts to use
 * another of the app's parts fails here, so the contract grows on purpose (add
 * the name, and think twice), never by accident; one no plugin uses any more
 * can be taken off. `tool/plugin_abi.py` checks the published plugins still
 * link; this keeps the surface they may link against from spreading.
 */
class PluginSurfaceTest {
    private val outsideTheApi = setOf(
        "com.aimdi.xtanative.data.Problem",
        "com.aimdi.xtanative.ui.common.AltBadge",
        "com.aimdi.xtanative.ui.common.AltTextDialog",
        "com.aimdi.xtanative.ui.common.Avatar",
        "com.aimdi.xtanative.ui.common.FeedSkeleton",
        "com.aimdi.xtanative.ui.common.Graphemes",
        "com.aimdi.xtanative.ui.common.GridSkeleton",
        "com.aimdi.xtanative.ui.common.Hairline",
        "com.aimdi.xtanative.ui.common.IconTip",
        "com.aimdi.xtanative.ui.common.LoadingRow",
        "com.aimdi.xtanative.ui.common.LocalReducedMotion",
        "com.aimdi.xtanative.ui.common.XIcons",
        "com.aimdi.xtanative.ui.common.agoText",
        "com.aimdi.xtanative.ui.common.countText",
        "com.aimdi.xtanative.ui.common.fullTimeText",
        "com.aimdi.xtanative.ui.common.longPressOver",
        "com.aimdi.xtanative.ui.common.monthText",
        "com.aimdi.xtanative.ui.common.rememberCopyContract",
        "com.aimdi.xtanative.ui.common.rememberCopyText",
        "com.aimdi.xtanative.ui.common.rememberSwitchedHaptics",
        "com.aimdi.xtanative.ui.common.scrollToTop",
        "com.aimdi.xtanative.ui.common.swipeTabs",
        "com.aimdi.xtanative.ui.downloads.PluginDownloads",
        "com.aimdi.xtanative.ui.links.LinkFrom",
        "com.aimdi.xtanative.ui.links.LocalLinks",
        "com.aimdi.xtanative.ui.media.AudioSessionBinding",
        "com.aimdi.xtanative.ui.media.AudioSessions",
        "com.aimdi.xtanative.ui.media.ContinuityPlayer",
        "com.aimdi.xtanative.ui.media.LocalInlinePlayer",
        "com.aimdi.xtanative.ui.media.PlaybackRestore",
        "com.aimdi.xtanative.ui.media.PodcastCheckpoint",
        "com.aimdi.xtanative.ui.media.PodcastPlaces",
        "com.aimdi.xtanative.ui.media.VideoQuality",
        "com.aimdi.xtanative.ui.media.VideoRules",
        "com.aimdi.xtanative.ui.media.rememberAudioSessions",
        "com.aimdi.xtanative.ui.media.rememberContinuityPlayers",
        "com.aimdi.xtanative.ui.media.rememberInlinePlayer",
        "com.aimdi.xtanative.ui.offline.OfflineArticleAction",
        "com.aimdi.xtanative.ui.post.AuthorLine",
        "com.aimdi.xtanative.ui.post.CardView",
        "com.aimdi.xtanative.ui.post.PostActions",
        "com.aimdi.xtanative.ui.post.PostIcons",
        "com.aimdi.xtanative.ui.post.PostPrefs",
        "com.aimdi.xtanative.ui.post.PostScopeProvider",
        "com.aimdi.xtanative.ui.post.ThreadRows",
        "com.aimdi.xtanative.ui.saved.SavedIcons",
        "com.aimdi.xtanative.ui.search.DiscoverIcons",
        "com.aimdi.xtanative.ui.speech.LocalSpeech",
        "com.aimdi.xtanative.ui.speech.ReadAloudButton",
        "com.aimdi.xtanative.ui.speech.VoiceSettingsSheet",
        "com.aimdi.xtanative.ui.subscriptions.GroupIcons",
        "com.aimdi.xtanative.ui.subscriptions.rememberRowDrag",
    )

    private val pluginSources: List<File> = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "plugins") }
        .firstOrNull { File(it, "reddit").isDirectory }
        ?.listFiles { dir -> dir.isDirectory }
        ?.flatMap { plugin -> File(plugin, "src").walkTopDown().filter { it.extension == "kt" && "Test" !in it.path }.toList() }
        .orEmpty()

    // Every package :ui holds: its screens (ui.*) and the app's own data (data.*), but not ui.plugins, the API itself.
    private val imported = Regex("""^import (com\.aimdi\.xtanative\.(?:ui\.(?!plugins\.|generated\.)[a-z]+|data(?:\.[a-z]+)?)\.[A-Z][A-Za-z]*|com\.aimdi\.xtanative\.(?:ui\.(?!plugins\.|generated\.)[a-z]+|data(?:\.[a-z]+)?)\.[a-z][A-Za-z]*)$""", RegexOption.MULTILINE)

    @Test
    fun pluginsUseOnlyTheAppsPartsTheContractNames() {
        assertTrue(pluginSources.isNotEmpty(), "the plugins' sources are beside the app's")
        val used = pluginSources.flatMap { file -> imported.findAll(file.readText()).map { it.groupValues[1] to file.name } }
        val outside = used.filter { it.first !in outsideTheApi }.groupBy({ it.first }, { it.second })
        assertTrue(
            outside.isEmpty(),
            "plugins use parts of the app outside the plugin API and this list: " +
                outside.entries.joinToString { (name, files) -> name + " (" + files.distinct().joinToString() + ")" } +
                ". Move what they need into ui.plugins, or add it here on purpose.",
        )
    }

    @Test
    fun pluginsDrawTheirSheetsAsTheAppsOwn() {
        val material = pluginSources.filter { Regex("""^import androidx\.compose\.material3\.ModalBottomSheet$""", RegexOption.MULTILINE).containsMatchIn(it.readText()) }
        assertTrue(
            material.isEmpty(),
            "these plugins draw Material's ModalBottomSheet, which the desktop's keys do not reach: use PluginSheet. " + material.joinToString { it.name },
        )
    }

    /**
     * A plugin's rows light under the pointer as the app's do because a plain
     * `clickable` reads the theme's indication (the desktop's RowIndication).
     * Switching it off, providing another, or a MaterialTheme of its own (which
     * brings Material's ripple back) leaves the plugin's rows unlit or lit
     * otherwise than the app's.
     */
    @Test
    fun pluginsKeepTheAppsLightingOfRows() {
        val own = Regex("""indication\s*=\s*null|LocalIndication\s+provides|\bMaterialTheme\(""")
        val unlit = pluginSources.filter { own.containsMatchIn(it.readText()) }
        assertTrue(
            unlit.isEmpty(),
            "these plugins replace the app's lighting of rows under the pointer: keep the theme's indication. " + unlit.joinToString { it.name },
        )
    }

    @Test
    fun pluginPlatformKnowsTheDesktop() {
        assertTrue(PluginPlatform.isDesktop, "the desktop tests run on the desktop")
    }

    @Test
    fun theListNamesOnlyWhatIsStillUsed() {
        val used = pluginSources.flatMap { file -> imported.findAll(file.readText()).map { it.groupValues[1] } }.toSet()
        val stale = outsideTheApi - used
        assertTrue(stale.isEmpty(), "no plugin uses these any more; take them off the list: " + stale.joinToString())
    }
}
