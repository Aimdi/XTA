package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.store.settings.PublishedDocuments
import com.aimdi.xtanative.store.settings.Settings

/**
 * Where the plugin store reads its catalogue, as XTA's plugin_catalogue.dart
 * reads it from the settings: the address the reader stored there, else
 * [built], the one this build names. XTA's own default (and the defaults
 * earlier releases saved) is XTA's catalogue, not this app's, so it counts
 * as not set.
 */
object CatalogueAddress {
    fun of(settings: Settings?, built: String): String {
        val stored = settings?.get(DataPrefs.pluginCatalogueUrl)?.trim().orEmpty()
        val isDefault = stored == PublishedDocuments.PLUGINS || PublishedDocuments.isFormerDefault(stored, PublishedDocuments.PLUGINS)
        return if (stored.isEmpty() || isDefault) built else stored
    }
}
