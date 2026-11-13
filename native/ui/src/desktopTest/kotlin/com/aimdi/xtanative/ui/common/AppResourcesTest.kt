package com.aimdi.xtanative.ui.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppResourcesTest {
    @Test
    fun aFolderNamedByTheEnvironmentComesFirst() {
        assertEquals(File("/tmp/engine"), AppResources.folder("video-engine", "/tmp/engine", resources = "/opt/app/lib/app/resources", launcher = "/opt/app/bin/xta-native"))
    }

    @Test
    fun elseComposesResourcesThenBesideTheLauncher() {
        assertEquals(File("/opt/app/lib/app/resources/video-engine"), AppResources.folder("video-engine", "", resources = "/opt/app/lib/app/resources", launcher = null))
        assertEquals(File("/opt/app/lib/app/resources/voice-engine"), AppResources.folder("voice-engine", null, resources = null, launcher = "/opt/app/bin/xta-native"))
    }

    @Test
    fun startedNeitherWayThereIsNone() {
        assertNull(AppResources.folder("video-engine", null, resources = null, launcher = null))
    }
}
