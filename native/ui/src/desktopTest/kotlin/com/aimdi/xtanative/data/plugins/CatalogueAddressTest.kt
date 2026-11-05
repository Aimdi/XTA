package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.store.settings.PublishedDocuments
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogueAddressTest {
    private val built = "https://github.com/Aimdi/XTA/releases/download/native-plugins/plugins.json"

    @Test
    fun theAddressTheReaderStoredIsRead() {
        val settings = Settings().with(DataPrefs.pluginCatalogueUrl, " https://plugins.example/plugins.json ")
        assertEquals("https://plugins.example/plugins.json", CatalogueAddress.of(settings, built))
    }

    @Test
    fun withoutOneTheBuildsIsRead() {
        assertEquals(built, CatalogueAddress.of(null, built))
        assertEquals(built, CatalogueAddress.of(Settings(), built))
        assertEquals(built, CatalogueAddress.of(Settings().with(DataPrefs.pluginCatalogueUrl, ""), built))
    }

    @Test
    fun xtasOwnDefaultsAreNotTheReadersChoice() {
        val current = Settings().with(DataPrefs.pluginCatalogueUrl, PublishedDocuments.PLUGINS)
        val former = Settings().with(DataPrefs.pluginCatalogueUrl, PublishedDocuments.PLUGINS.replace("/HEAD/", "/main/"))
        assertEquals(built, CatalogueAddress.of(current, built))
        assertEquals(built, CatalogueAddress.of(former, built))
    }
}
