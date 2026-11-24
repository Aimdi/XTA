package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataSavingTest {
    @Test
    fun theSwitchIsOffUntilTheReaderTurnsItOn() {
        assertFalse(Settings()[MediaPrefs.saveDataOnMobile])
        assertTrue(Settings().with(MediaPrefs.saveDataOnMobile, true)[MediaPrefs.saveDataOnMobile])
    }

    @Test
    fun theSwitchSavesDataOnlyOnAMeteredNetwork() {
        assertTrue(DataSaving.onMeteredNetwork(setting = true, metered = true))
        assertFalse(DataSaving.onMeteredNetwork(setting = true, metered = false), "on Wi-Fi nothing changes")
        assertFalse(DataSaving.onMeteredNetwork(setting = false, metered = true), "off, mobile data is as any network")
        assertFalse(DataSaving.onMeteredNetwork(setting = false, metered = false))
    }

    @Test
    fun videosWaitForATapUnderDataSaverOrTheSwitchOnAMeteredNetwork() {
        assertFalse(DataSaving.savesData(systemDataSaver = false, onMeteredNetwork = false))
        assertTrue(DataSaving.savesData(systemDataSaver = true, onMeteredNetwork = false), "Android's Data Saver, as before")
        assertTrue(DataSaving.savesData(systemDataSaver = false, onMeteredNetwork = true))
        assertTrue(DataSaving.savesData(systemDataSaver = true, onMeteredNetwork = true))
        fun autoplays(setting: Boolean, metered: Boolean) =
            VideoRules.autoplays(setting = true, reducedMotion = false, dataSaver = DataSaving.savesData(false, DataSaving.onMeteredNetwork(setting, metered)))
        assertFalse(autoplays(setting = true, metered = true))
        assertTrue(autoplays(setting = true, metered = false))
        assertTrue(autoplays(setting = false, metered = true), "with the switch off autoplay is what it was")
    }

    @Test
    fun picturesLoadNoLargerThanTheCapWhileDataIsSaved() {
        assertEquals(PhotoSize.SMALL, DataSaving.PHOTO_CAP)
        assertEquals(PhotoSize.SMALL, DataSaving.photoSize(PhotoSize.LARGE, onMeteredNetwork = true))
        assertEquals(PhotoSize.SMALL, DataSaving.photoSize(PhotoSize.MEDIUM, onMeteredNetwork = true))
        assertEquals(PhotoSize.SMALL, DataSaving.photoSize(PhotoSize.SMALL, onMeteredNetwork = true))
        assertEquals(PhotoSize.THUMB, DataSaving.photoSize(PhotoSize.THUMB, onMeteredNetwork = true), "a reader's lower choice is kept")
    }

    @Test
    fun picturesKeepTheReadersQualityWhenNoDataIsSaved() {
        MediaQuality.entries.map(MediaFiles::viewerSize).forEach { size ->
            assertEquals(size, DataSaving.photoSize(size, onMeteredNetwork = false))
        }
    }
}
