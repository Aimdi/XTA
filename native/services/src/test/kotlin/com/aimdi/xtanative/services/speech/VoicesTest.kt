package com.aimdi.xtanative.services.speech

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class VoicesTest {
    private val temp: File = Files.createTempDirectory("xta-voices").toFile()
    private val voices = File(temp, "voices")
    private val staging = File(temp, "staging")
    private val engineRoot = File(temp, "voice-engine")

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private val german = OfflineVoices.byId("de-thorsten-emotional")!!
    private val libritts = OfflineVoices.byId("en-libritts-r")!!
    private val kokoro = OfflineVoice(
        id = "en-kokoro", name = "Kokoro", locale = "en_US",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2",
        archiveBytes = 103_248_205, sha256 = "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd", licence = "Apache-2.0",
        kind = VoiceModelKind.KOKORO, archiveRoot = "kokoro-int8-en-v0_19", model = "model.int8.onnx", voices = "voices.bin", preference = 1,
    )

    // ---- the catalog ----

    @Test
    fun voiceIdsAreUniqueAndUsableAsFolderNames() {
        val ids = OfflineVoices.CATALOG.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(Regex("^[a-z0-9-]+$").matches(it), it) }
    }

    @Test
    fun everyVoiceIsPinnedToK2FsaOverHttpsWithAChecksumAndASize() {
        OfflineVoices.CATALOG.forEach { voice ->
            assertEquals("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${voice.archiveRoot}.tar.bz2", voice.url)
            assertTrue(Regex("^[0-9a-f]{64}$").matches(voice.sha256!!), voice.id)
            assertTrue(voice.archiveBytes > 1 shl 20, voice.id)
            assertTrue(voice.model.endsWith(".onnx"))
            assertTrue("tokens.txt" in voice.requiredFiles && "espeak-ng-data/phontab" in voice.requiredFiles)
            assertTrue(voice.licence.isNotEmpty())
            assertEquals(voice.kind == VoiceModelKind.KOKORO, voice.voices != null, "only Kokoro has speaker embeddings")
        }
        assertTrue(OfflineVoices.CATALOG.map { it.language }.containsAll(listOf("de", "en")))
        assertEquals(4, german.speakerId, "the neutral Thorsten")
    }

    @Test
    fun aVoiceIsPickedByLanguageWhateverTheRegionAndTheBetterOneWins() {
        assertEquals(german, OfflineVoices.pick(listOf(german, libritts), "de"))
        assertEquals(libritts, OfflineVoices.pick(listOf(german, libritts), "en-GB"))
        assertEquals(german, OfflineVoices.pick(listOf(german, libritts), "de_AT"))
        assertNull(OfflineVoices.pick(listOf(german, libritts), "fr"))
        assertNull(OfflineVoices.pick(listOf(german), null))
        assertNull(OfflineVoices.pick(emptyList(), "de"))
        assertEquals(kokoro, OfflineVoices.pick(listOf(libritts, kokoro), "en"))
        assertEquals(kokoro, OfflineVoices.pick(listOf(kokoro, libritts), "en"))
        assertEquals(listOf("model.int8.onnx", "tokens.txt", "espeak-ng-data/phontab", "voices.bin"), kokoro.requiredFiles)
    }

    @Test
    fun vitsVoicesLoadWithTheirPhonemeDataAndKokoroWithItsSpeakers() {
        val vits = SherpaConfig.of(InstalledVoice(german, File("/v/de")))
        assertEquals(VoiceModelKind.VITS, vits.kind)
        assertEquals("/v/de/de_DE-thorsten_emotional-medium.onnx", vits.model)
        assertEquals("/v/de/tokens.txt", vits.tokens)
        assertEquals("/v/de/espeak-ng-data", vits.dataDir)
        assertNull(vits.voices)
        assertEquals(4, vits.speakerId)
        assertEquals(2, vits.threads)

        val kokoroConfig = SherpaConfig.of(InstalledVoice(kokoro, File("/v/k")))
        assertEquals("/v/k/model.int8.onnx", kokoroConfig.model)
        assertEquals("/v/k/voices.bin", kokoroConfig.voices)
        assertEquals("/v/k/espeak-ng-data", kokoroConfig.dataDir)
    }

    @Test
    fun archiveEntriesStayInsideTheirFolder() {
        assertEquals("tokens.txt", VoiceArchives.entryPath("root/tokens.txt", "root"))
        assertEquals("espeak/phontab", VoiceArchives.entryPath("root/espeak/phontab", "root"))
        assertEquals("", VoiceArchives.entryPath("root/", "root"))
        assertEquals("a.onnx", VoiceArchives.entryPath("./root/a.onnx", "root"))
        assertEquals("a.onnx", VoiceArchives.entryPath("root\\a.onnx", "root"))
        listOf("/etc/passwd", "root/../../evil", "other/tokens.txt", "rootkit/x").forEach { assertNull(VoiceArchives.entryPath(it, "root"), it) }
    }

    // ---- the engine ----

    @Test
    fun anEngineIsPublishedPerProcessorButNotFor32BitX86() {
        assertEquals("arm64-v8a", VoiceEngines.forAbi("arm64-v8a")?.abi)
        assertEquals("armeabi-v7a", VoiceEngines.forAbi("armeabi-v7a")?.abi)
        assertEquals("x86_64", VoiceEngines.forAbi("x86_64")?.abi)
        assertNull(VoiceEngines.forAbi("x86"))
        VoiceEngines.PACKAGES.forEach { engine ->
            assertEquals(listOf("libonnxruntime.so", "libsherpa-onnx-c-api.so"), engine.libraries.map { it.name })
            engine.libraries.forEach { assertTrue(Regex("^[0-9a-f]{64}$").matches(it.sha256) && it.bytes > 1 shl 20) }
        }
    }

    @Test
    fun aReleaseBuildFetchesItsOwnReleasesArchiveThenTheLatest() {
        val arm64 = VoiceEngines.forAbi("arm64-v8a")!!
        assertEquals(
            listOf(
                "https://github.com/Aimdi/XTA/releases/download/aimdi189/xta-voice-engine-sherpa-onnx-1.13.8-arm64-v8a.tar.xz",
                "https://github.com/Aimdi/XTA/releases/latest/download/xta-voice-engine-sherpa-onnx-1.13.8-arm64-v8a.tar.xz",
            ),
            VoiceEngines.urls(arm64, "aimdi189"),
        )
        assertEquals(listOf("https://github.com/Aimdi/XTA/releases/latest/download/xta-voice-engine-sherpa-onnx-1.13.8-arm64-v8a.tar.xz"), VoiceEngines.urls(arm64, ""))
        assertEquals(File(engineRoot, "sherpa-onnx-1.13.8/arm64-v8a"), VoiceEngines.directory(engineRoot, arm64))
    }

    // ---- installing ----

    private fun archiveFile(bytes: ByteArray): File = File(temp, "archive-${bytes.size}-${System.nanoTime()}").apply { writeBytes(bytes) }

    @Test
    fun aSoundArchiveIsUnpackedIntoPlaceWithNothingLeftOver() {
        val bytes = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, soundVoiceFiles))
        val voice = testVoice(bytes)
        VoiceArchives.install(archiveFile(bytes), voice, voices, FakeTar.reader)
        val folder = File(voices, "xx-test")
        assertEquals("phonemes", File(folder, "espeak-ng-data/phontab").readText())
        assertTrue(VoiceArchives.filesPresent(folder, voice))
        assertEquals(listOf("xx-test"), voices.list()!!.toList(), "no staging folder left")
        assertTrue(VoiceArchives.directoryBytes(folder) > 0)
    }

    @Test
    fun anArchiveThatDoesNotMatchItsChecksumIsRefusedBeforeItIsOpened() {
        val bytes = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, soundVoiceFiles))
        assertFailsWith<VoiceChecksumMismatch> { VoiceArchives.install(archiveFile(bytes), testVoice(bytes, "0".repeat(64)), voices, FakeTar.reader) }
        assertFalse(File(voices, "xx-test").exists())
    }

    @Test
    fun anArchiveMissingAFileReachingOutOrHoldingALinkIsRefused() {
        val missing = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, mapOf("voice.onnx" to "model")))
        assertFailsWith<VoiceArchiveInvalid> { VoiceArchives.install(archiveFile(missing), testVoice(missing), voices, FakeTar.reader) }

        val escaping = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, soundVoiceFiles) + FakeTar.Entry("$TEST_VOICE_ROOT/../../escaped.txt", 'f', "gotcha"))
        assertFailsWith<VoiceArchiveInvalid> { VoiceArchives.install(archiveFile(escaping), testVoice(escaping), voices, FakeTar.reader) }
        assertFalse(File(temp, "escaped.txt").exists())

        val linked = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, soundVoiceFiles) + FakeTar.Entry("$TEST_VOICE_ROOT/link", 'l'))
        assertFailsWith<VoiceArchiveInvalid> { VoiceArchives.install(archiveFile(linked), testVoice(linked), voices, FakeTar.reader) }
        assertTrue(voices.list().orEmpty().isEmpty())
    }

    @Test
    fun aVoiceWithoutAKnownChecksumInstallsFromASoundArchiveOnly() {
        val bytes = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, soundVoiceFiles))
        val unchecked = testVoice(bytes, checksum = null).copy(id = "xx-unchecked")
        VoiceArchives.install(archiveFile(bytes), unchecked, voices, FakeTar.reader)
        assertTrue(VoiceArchives.filesPresent(File(voices, "xx-unchecked"), unchecked))
        assertFailsWith<VoiceArchiveInvalid> { VoiceArchives.install(archiveFile(bytes.copyOfRange(0, bytes.size - 20)), unchecked, voices, FakeTar.reader) }
    }

    @Test
    fun theEnginesLibrariesArePutInPlaceOnlyWhenTheyMatchTheirPins() {
        val engine = fakeEngine()
        VoiceArchives.installEngine(archiveFile(engineArchive()), engine, engineRoot, FakeTar.reader)
        fakeEngineFiles.forEach { (name, content) -> assertEquals(content, File(VoiceEngines.directory(engineRoot, engine), name).readText()) }
        assertTrue(VoiceArchives.engineFilesPresent(engineRoot, engine))

        File(VoiceEngines.directory(engineRoot, engine), "libonnxruntime.so").writeText("x")
        assertFalse(VoiceArchives.engineFilesPresent(engineRoot, engine), "an installed library of the wrong size does not count")
    }

    @Test
    fun aLibraryThatDoesNotMatchOrIsMissingInstallsNothing() {
        val engine = fakeEngine()
        val tampered = engineArchive(fakeEngineFiles + ("libsherpa-onnx-c-api.so" to "something else entirely"))
        assertFailsWith<VoiceChecksumMismatch> { VoiceArchives.installEngine(archiveFile(tampered), engine, engineRoot, FakeTar.reader) }
        val partial = engineArchive(mapOf("libonnxruntime.so" to fakeEngineFiles.getValue("libonnxruntime.so")))
        assertFailsWith<VoiceArchiveInvalid> { VoiceArchives.installEngine(archiveFile(partial), engine, engineRoot, FakeTar.reader) }
        assertFailsWith<VoiceArchiveInvalid> { VoiceArchives.installEngine(archiveFile(ByteArray(512) { it.toByte() }), engine, engineRoot, FakeTar.reader) }
        assertFalse(engineRoot.walkTopDown().any { it.name.endsWith(".so") })
    }

    // ---- downloading ----

    private val soundArchive = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, soundVoiceFiles))
    private val voice = testVoice(soundArchive)
    private val own = "https://github.com/Aimdi/XTA/releases/download/aimdi189/engine.tar.xz"
    private val latest = "https://github.com/Aimdi/XTA/releases/latest/download/engine.tar.xz"

    private fun bundledEngine() = VoiceEngineStore(null, bundled = true, engineRoot, FakeTransfer(emptyMap(), staging), FakeTar.reader) { emptyList() }

    private fun androidEngine(transfer: FakeTransfer, urls: List<String> = listOf(own, latest)) =
        VoiceEngineStore(fakeEngine(), bundled = false, engineRoot, transfer, FakeTar.reader) { urls }

    private fun downloads(transfer: FakeTransfer, engine: VoiceEngineStore = bundledEngine()) =
        VoiceDownloadStore(engine, voices, transfer, FakeTar.reader, catalog = listOf(voice))

    @Test
    fun nothingIsDownloadedUntilAsked() = runBlocking {
        val transfer = FakeTransfer(mapOf(voice.url to soundArchive), staging)
        val store = downloads(transfer)
        store.ensureLoaded()
        assertEquals(VoiceInstall.Absent, store.installOf("xx-test"))
        assertNull(store.installedFor("de"))
        assertTrue(transfer.requested.isEmpty())
    }

    @Test
    fun aDownloadReportsProgressThenIsCheckedAndUnpackedIntoPlace() = runBlocking {
        val transfer = FakeTransfer(mapOf(voice.url to soundArchive), staging)
        val store = downloads(transfer)
        val seen = ArrayList<VoiceInstall>()
        val watcher = launch(Dispatchers.Unconfined) { store.state.collect { state -> state["xx-test"]?.let { seen += it } } }
        store.download(voice)
        watcher.cancel()
        assertEquals(listOf(voice.url), transfer.requested)
        val progress = seen.filterIsInstance<VoiceInstall.Downloading>()
        assertEquals(0, progress.first().received)
        assertEquals(soundArchive.size.toLong(), progress.last().received)
        assertEquals(1.0, progress.last().fraction)
        assertTrue(seen.any { it is VoiceInstall.Installing })
        val ready = assertIs<VoiceInstall.Ready>(store.installOf("xx-test"))
        assertEquals(File(voices, "xx-test"), ready.path)
        assertEquals(voice, store.installedFor("de-DE")?.voice)
        assertNull(store.installedFor("en"))
        assertTrue(staging.list().orEmpty().isEmpty(), "the staged archive is removed")
    }

    @Test
    fun aMismatchedOrBrokenDownloadFailsAndLeavesNothing() = runBlocking {
        val mismatched = testVoice(soundArchive, "0".repeat(64))
        val store = VoiceDownloadStore(bundledEngine(), voices, FakeTransfer(mapOf(mismatched.url to soundArchive), staging), FakeTar.reader, listOf(mismatched))
        store.download(mismatched)
        assertEquals(VoiceInstall.Failed(VoiceFailure.CHECKSUM), store.installOf("xx-test"))
        assertTrue(voices.list().orEmpty().isEmpty())
        assertTrue(staging.list().orEmpty().isEmpty(), "a mismatched file is never resumed")

        val broken = FakeTar.bytes(FakeTar.files(TEST_VOICE_ROOT, mapOf("voice.onnx" to "model")))
        val brokenVoice = testVoice(broken)
        val second = VoiceDownloadStore(bundledEngine(), voices, FakeTransfer(mapOf(brokenVoice.url to broken), staging), FakeTar.reader, listOf(brokenVoice))
        second.download(brokenVoice)
        assertEquals(VoiceInstall.Failed(VoiceFailure.ARCHIVE), second.installOf("xx-test"))
    }

    @Test
    fun cancellingStopsTheDownloadAndCleansUp() = runBlocking {
        lateinit var store: VoiceDownloadStore
        val transfer = FakeTransfer(mapOf(voice.url to soundArchive), staging) { store.cancel("xx-test") }
        store = downloads(transfer)
        store.download(voice)
        assertEquals(VoiceInstall.Absent, store.installOf("xx-test"))
        assertFalse(File(voices, "xx-test").exists())
        assertTrue(staging.list().orEmpty().isEmpty())
    }

    @Test
    fun aDeletedVoiceIsGoneAndAnInstalledOneIsFoundNextTime() = runBlocking {
        downloads(FakeTransfer(mapOf(voice.url to soundArchive), staging)).download(voice)
        val later = downloads(FakeTransfer(emptyMap(), staging))
        assertEquals(voice, later.installedFor("de")?.voice)
        assertIs<VoiceInstall.Ready>(later.installOf("xx-test"))
        later.delete(voice)
        assertEquals(VoiceInstall.Absent, later.installOf("xx-test"))
        assertFalse(File(voices, "xx-test").exists())
        assertNull(later.installedFor("de"))
    }

    @Test
    fun aFailedDownloadCanBeTriedAgain() = runBlocking {
        val answers = HashMap<String, Any>(mapOf(voice.url to 503))
        val store = downloads(FakeTransfer(answers, staging))
        store.download(voice)
        assertEquals(VoiceInstall.Failed(VoiceFailure.NETWORK), store.installOf("xx-test"))
        answers[voice.url] = soundArchive
        store.download(voice)
        assertIs<VoiceInstall.Ready>(store.installOf("xx-test"))
    }

    @Test
    fun theEngineThatCameWithTheAppNeedsNothingDownloaded() = runBlocking {
        val bundled = bundledEngine()
        assertTrue(bundled.usable)
        assertTrue(bundled.download())
        assertNull(bundled.loadFrom)
    }

    @Test
    fun onAndroidAVoiceCannotReadUntilTheEngineIsDownloaded() = runBlocking {
        val transfer = FakeTransfer(mapOf(own to engineArchive()), staging)
        val engine = androidEngine(transfer)
        engine.ensureLoaded()
        assertFalse(engine.usable)
        assertTrue(transfer.requested.isEmpty(), "nothing is fetched without a tap")
        assertTrue(engine.download())
        assertEquals(listOf(own), transfer.requested)
        assertEquals(fakeEngine().installedBytes, assertIs<VoiceInstall.Ready>(engine.state.value).bytes)
        assertEquals(VoiceEngines.directory(engineRoot, fakeEngine()), engine.loadFrom)
    }

    @Test
    fun aReleaseThatDoesNotAnswerOrServesAWrongFileFallsBackToTheLatest() = runBlocking {
        val missing = FakeTransfer(mapOf(own to 404, latest to engineArchive()), staging)
        assertTrue(androidEngine(missing).download())
        assertEquals(listOf(own, latest), missing.requested)
        engineRoot.deleteRecursively()

        val tampered = FakeTransfer(mapOf(own to engineArchive(fakeEngineFiles + ("libonnxruntime.so" to "tampered")), latest to engineArchive()), staging)
        assertTrue(androidEngine(tampered).download())
        assertEquals(listOf(own, latest), tampered.requested)
    }

    @Test
    fun whenNoSourceAnswersTheEngineDownloadFails() = runBlocking {
        val engine = androidEngine(FakeTransfer(mapOf(own to 404, latest to 404), staging))
        assertFalse(engine.download())
        assertEquals(VoiceInstall.Failed(VoiceFailure.NETWORK), engine.state.value)
    }

    @Test
    fun anEngineAlreadyOnTheDeviceIsFoundAndCanBeDeleted() = runBlocking {
        assertTrue(androidEngine(FakeTransfer(mapOf(own to engineArchive()), staging)).download())
        val later = androidEngine(FakeTransfer(emptyMap(), staging))
        later.ensureLoaded()
        assertTrue(later.usable)
        later.delete()
        assertEquals(VoiceInstall.Absent, later.state.value)
        assertFalse(VoiceArchives.engineFilesPresent(engineRoot, fakeEngine()))
    }

    @Test
    fun aProcessorWithNoPublishedEngineCannotDownloadOne() = runBlocking {
        val none = VoiceEngineStore(null, bundled = false, engineRoot, FakeTransfer(emptyMap(), staging), FakeTar.reader) { emptyList() }
        assertFalse(none.available)
        assertFalse(none.download())
    }

    @Test
    fun aVoiceBringsTheEngineFirstWhenItIsMissing() = runBlocking {
        val transfer = FakeTransfer(mapOf(own to engineArchive(), voice.url to soundArchive), staging)
        val store = downloads(transfer, androidEngine(transfer))
        store.download(voice)
        assertEquals(listOf(own, voice.url), transfer.requested)
        assertIs<VoiceInstall.Ready>(store.engine.state.value)
        assertEquals(voice, store.installedFor("de")?.voice)
    }

    @Test
    fun aVoiceIsNotFetchedWhenTheEngineCannotBeAndFailsAsTheEngineDid() = runBlocking {
        val transfer = FakeTransfer(mapOf(own to 404, latest to 404, voice.url to soundArchive), staging)
        val store = downloads(transfer, androidEngine(transfer))
        store.download(voice)
        assertFalse(voice.url in transfer.requested)
        assertEquals(VoiceInstall.Failed(VoiceFailure.NETWORK), store.installOf(voice.id))
    }

    @Test
    fun anInstalledVoiceDoesNotReadUntilTheEngineIsThere() = runBlocking {
        downloads(FakeTransfer(mapOf(voice.url to soundArchive), staging)).download(voice)
        val store = downloads(FakeTransfer(mapOf(own to engineArchive()), staging), androidEngine(FakeTransfer(mapOf(own to engineArchive()), staging)))
        store.ensureLoaded()
        assertIs<VoiceInstall.Ready>(store.installOf(voice.id))
        assertNull(store.installedFor("de"))
        store.engine.download()
        assertEquals(voice, store.installedFor("de")?.voice)
    }

    @Test
    fun cancellingAVoiceWhileTheEngineDownloadsCancelsTheEngineToo() = runBlocking {
        lateinit var store: VoiceDownloadStore
        val transfer = FakeTransfer(mapOf(own to engineArchive(), voice.url to soundArchive), staging) { store.cancel(voice.id) }
        store = downloads(transfer, androidEngine(transfer, listOf(own)))
        store.download(voice)
        assertEquals(listOf(own), transfer.requested)
        assertEquals(VoiceInstall.Absent, store.engine.state.value)
        assertEquals(VoiceInstall.Absent, store.installOf(voice.id))
    }
}
