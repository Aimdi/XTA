package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.aimdi.xtanative.data.plugins.RunningPlugin
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins

/**
 * Keeps the full backup in step with the plugins that run ([PluginBackups]):
 * their sections, and what a restore kept for them, handed over as each
 * plugin starts.
 */
@Composable
fun AttachPluginBackups() {
    val graph = LocalGraph.current
    val plugins = LocalPlugins.current
    LaunchedEffect(graph, plugins) {
        plugins.running.collect { running -> graph.pluginBackups.attach(running.flatMap(::sectionsOf)) }
    }
}

/** What [running] keeps in the backup; nothing when its code cannot say. */
private fun sectionsOf(running: RunningPlugin): List<PluginSection> = try {
    running.plugin.backupSections.map { PluginSection(it.name, it.export, it.import) }
} catch (e: Exception) {
    emptyList()
} catch (e: LinkageError) {
    emptyList()
}
