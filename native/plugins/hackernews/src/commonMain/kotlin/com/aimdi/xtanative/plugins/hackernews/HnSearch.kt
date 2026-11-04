package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedList
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

/** XTA's Hacker News search (hn_search_sheet.dart): stories matching what was typed, most relevant first. */
internal class HnSearch(private val app: HnApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) {
        val pages = rememberKept("hn-search:$query") { scope -> PagedItems<HnStory>(scope, key = { "${it.id}" }) { cursor -> app.client.search(query, cursor) } }
        PagedList(pages, key = { "${it.id}" }, network = app.network, empty = pluginString(Strings.search_empty), contentPadding = frame.padding, toTop = frame.toTop) { story ->
            HnStoryRow(app, story)
        }
    }
}

/** XTA's Hacker News settings (hn_settings.dart): its mark, and that nothing reaches Hacker News but reading. */
@Composable
internal fun HnSettingsBody(frame: PluginFrame) {
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        item(key = "blurb") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.Start) {
                Icon(PostIcons.Forum, contentDescription = null, tint = HnBrand, modifier = Modifier.size(40.dp))
                Text(pluginString(Strings.settings_blurb), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
