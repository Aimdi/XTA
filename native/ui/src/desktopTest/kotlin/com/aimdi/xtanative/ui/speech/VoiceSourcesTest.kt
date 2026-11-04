package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.OfflineVoices
import com.aimdi.xtanative.services.speech.VoiceEngines
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceSourcesTest {
    private val engine = VoiceEngines.forAbi("x86_64")!!

    @Test
    fun aReleaseBuildTakesTheEngineFromItsOwnReleaseThenXtasLatest() {
        val sources = VoiceSources(server = null, releaseTag = "native-0.1.300")
        assertEquals(
            listOf(
                "https://github.com/Aimdi/XTA/releases/download/native-0.1.300/xta-voice-engine-sherpa-onnx-1.13.8-x86_64.tar.xz",
                "https://github.com/Aimdi/XTA/releases/latest/download/xta-voice-engine-sherpa-onnx-1.13.8-x86_64.tar.xz",
            ),
            sources.engineUrls(engine),
        )
        assertEquals(OfflineVoices.CATALOG, sources.catalog)
        assertEquals(listOf(VoiceEngines.urls(engine, "").single()), VoiceSources(null, "").engineUrls(engine))
    }

    @Test
    fun ciServesTheEngineAndEveryVoiceByTheirFileNames() {
        val sources = VoiceSources(server = "http://10.0.2.2:8789", releaseTag = "native-0.1.300")
        assertEquals(listOf("http://10.0.2.2:8789/xta-voice-engine-sherpa-onnx-1.13.8-x86_64.tar.xz"), sources.engineUrls(engine))
        assertEquals(
            OfflineVoices.CATALOG.map { "http://10.0.2.2:8789/${it.archiveRoot}.tar.bz2" },
            sources.catalog.map { it.url },
        )
        assertEquals(OfflineVoices.CATALOG.map { it.sha256 }, sources.catalog.map { it.sha256 })
    }

    @Test
    fun theShellsPropertiesNameTheServerAndTheRelease() {
        try {
            System.setProperty(VoiceSources.SERVER_PROPERTY, "http://127.0.0.1:8789/")
            System.setProperty(VoiceSources.RELEASE_TAG_PROPERTY, "native-0.1.7")
            assertEquals(listOf("http://127.0.0.1:8789/${engine.archiveName}"), VoiceSources.fromProperties().engineUrls(engine))
            System.clearProperty(VoiceSources.SERVER_PROPERTY)
            assertEquals(VoiceEngines.urls(engine, "native-0.1.7"), VoiceSources.fromProperties().engineUrls(engine))
        } finally {
            System.clearProperty(VoiceSources.SERVER_PROPERTY)
            System.clearProperty(VoiceSources.RELEASE_TAG_PROPERTY)
        }
    }
}
