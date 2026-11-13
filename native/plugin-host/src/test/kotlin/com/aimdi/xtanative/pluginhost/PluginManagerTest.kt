package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.store.StoreDir
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl

class PluginManagerTest {
    private val dir = tempDir("manager")
    private val plugins = File(dir, "plugins")
    private val store = StoreDir(File(dir, "store"))
    private val erased = CopyOnWriteArrayList<String>()
    private val now = 1_700_000_000_000L

    private val reddit = pluginJar("reddit", "1.0.0")
    private val stocks = pluginJar("stocks", "2.0.0")
    private val served = mutableMapOf(
        "https://plugins.example/xta-plugin-reddit-1.0.0.jar" to reddit,
        "https://plugins.example/xta-plugin-stocks-2.0.0.jar" to stocks,
    )

    @Volatile
    private var catalogue = catalogueJson(entryJson("reddit", "1.0.0", reddit, category = "communities"), entryJson("stocks", "2.0.0", stocks, category = "markets"))

    @Volatile
    private var answer: () -> XResponse = { XResponse(200, catalogue) }

    private val http = object : XHttp {
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse = answer()
    }

    private fun manager(
        artifacts: ArtifactSource = FakeArtifacts(served),
        verifier: PluginVerifier = PluginVerifier { Verdict.Trusted },
        host: HostPlatform = HostPlatform(PluginPlatform.DESKTOP, verifier, JarClassLoaderFactory, testPluginsHidden),
        erase: suspend (String) -> Unit = { erased += it },
    ) = PluginManager(
        host = host,
        api = PluginApi(1),
        directory = plugins,
        store = store,
        erase = erase,
        catalogueUrl = "https://plugins.example/plugins.json",
        http = http,
        artifacts = artifacts,
        now = { now },
    )

    private fun PluginManager.status(id: String) = state.value[id]?.status

    private suspend fun PluginManager.awaitStatus(id: String, status: PluginStatus) = withTimeout(10_000) { state.first { it[id]?.status == status } }

    private fun part(id: String, version: String) = File(plugins, ".downloads/$id/$version.jar.part")

    @Test
    fun aRefreshReadsTheCatalogueAndKeepsItForWhenItCannotBeReached() = runBlocking {
        val first = manager()
        assertTrue(first.refresh())
        assertEquals(CatalogueStatus(fetchedAt = now), first.state.value.catalogue)
        assertEquals(listOf("reddit", "stocks"), first.state.value.plugins.map { it.id })

        answer = { throw IOException("offline") }
        val offline = manager()
        assertFalse(offline.refresh())
        assertEquals(CatalogueStatus(unreachable = true, fetchedAt = now), offline.state.value.catalogue)
        assertEquals(listOf("reddit", "stocks"), offline.state.value.plugins.map { it.id }, "the copy read last")

        listOf(XResponse(503, ""), XResponse(200, "<html>rate limited</html>")).forEach { response ->
            answer = { response }
            assertFalse(offline.refresh())
            assertEquals(listOf("reddit", "stocks"), offline.state.value.plugins.map { it.id }, "an answer that is no catalogue replaces nothing")
        }
    }

    @Test
    fun anInstallFetchesChecksAndKeepsThePluginReadOnly() = runBlocking {
        val manager = manager()
        manager.refresh()
        assertTrue(manager.install("reddit"))
        assertEquals(PluginStatus.Installed("1.0.0"), manager.status("reddit"))
        val file = File(plugins, "reddit/1.0.0/xta-plugin-reddit-1.0.0.jar")
        assertContentEquals(reddit, file.readBytes())
        assertTrue(isReadOnly(file))
        assertFalse(part("reddit", "1.0.0").exists())
        assertEquals(listOf("reddit"), manager.state.value.installed.map { it.id })
        assertTrue(manager.install("reddit"), "what is installed is installed already")

        val nextLaunch = manager()
        assertEquals(listOf("reddit"), nextLaunch.installed().map { it.id })
        assertEquals(PluginStatus.Installed("1.0.0"), nextLaunch.status("reddit"))
        assertEquals(PluginStatus.Available, nextLaunch.status("stocks"), "the catalogue kept from the last read")
    }

    @Test
    fun theStoreShowsTheDownloadThenTheChecksAsTheyHappen() = runBlocking {
        val artifacts = FakeArtifacts(served, holdAt = 100)
        val checking = CountDownLatch(1)
        val manager = manager(artifacts, verifier = { checking.await(10, TimeUnit.SECONDS); Verdict.Trusted })
        manager.refresh()
        val install = async(Dispatchers.Default) { manager.install("reddit") }

        manager.awaitStatus("reddit", PluginStatus.Downloading(100, reddit.size.toLong()))
        assertEquals(0.0f, PluginStatus.Downloading(0, 0).fraction)
        artifacts.release.countDown()
        manager.awaitStatus("reddit", PluginStatus.Installing)
        checking.countDown()
        assertTrue(install.await())
        assertEquals(PluginStatus.Installed("1.0.0"), manager.status("reddit"))
    }

    @Test
    fun aFileThatIsNotTheCataloguesIsDeletedAndNeverInstalled() = runBlocking {
        catalogue = catalogueJson(entryJson("reddit", "1.0.0", reddit, sha256 = sha256(stocks)))
        val manager = manager()
        manager.refresh()
        assertFalse(manager.install("reddit"))
        assertEquals(PluginStatus.Failed(PluginFailure.WrongChecksum, null), manager.status("reddit"))
        assertFalse(part("reddit", "1.0.0").exists())
        assertFalse(File(plugins, "reddit").exists())
        assertEquals(emptyList(), manager.installed())
    }

    @Test
    fun aResumedFileThatFailsItsChecksIsFetchedWholeOnceMore() = runBlocking {
        part("reddit", "1.0.0").apply { parentFile.mkdirs(); writeBytes(ByteArray(100)) }
        val artifacts = FakeArtifacts(served)
        val manager = manager(artifacts)
        manager.refresh()
        assertTrue(manager.install("reddit"))
        assertEquals(listOf(100L, 0L), artifacts.asked)
    }

    @Test
    fun aFileNotSignedWithTheAppsKeyIsDeletedAndNeverInstalled() = runBlocking {
        val manager = manager(verifier = { Verdict.Refused("signed by someone else") })
        manager.refresh()
        assertFalse(manager.install("reddit"))
        assertEquals(PluginStatus.Failed(PluginFailure.BadSignature("signed by someone else"), null), manager.status("reddit"))
        assertFalse(part("reddit", "1.0.0").exists())
        assertEquals(emptyList(), manager.installed())
    }

    @Test
    fun aFileThatSaysItIsAnotherVersionOrNeedsANewerAppIsRefused() = runBlocking {
        served["https://plugins.example/xta-plugin-reddit-1.0.0.jar"] = pluginJar("reddit", "0.9.0")
        served["https://plugins.example/xta-plugin-stocks-2.0.0.jar"] = pluginJar("stocks", "2.0.0", apiVersion = 2)
        catalogue = catalogueJson(
            entryJson("reddit", "1.0.0", served.getValue("https://plugins.example/xta-plugin-reddit-1.0.0.jar")),
            entryJson("stocks", "2.0.0", served.getValue("https://plugins.example/xta-plugin-stocks-2.0.0.jar")),
        )
        val manager = manager()
        manager.refresh()
        assertFalse(manager.install("reddit"))
        assertEquals(PluginFailure.BadManifest("the file is reddit 0.9.0, the catalogue's reddit 1.0.0"), (manager.status("reddit") as PluginStatus.Failed).reason)
        assertFalse(manager.install("stocks"))
        assertEquals(PluginStatus.Failed(PluginFailure.NeedsNewerApp(2), null), manager.status("stocks"))
        assertEquals(emptyList(), manager.installed())
    }

    @Test
    fun aPluginForANewerApiIsShownAsNeedingANewerAppAndNotDownloaded() = runBlocking {
        catalogue = catalogueJson(entryJson("reddit", "1.0.0", reddit, apiVersion = 2))
        val artifacts = FakeArtifacts(served)
        val manager = manager(artifacts)
        manager.refresh()
        assertEquals(PluginStatus.NeedsNewerApp(2), manager.status("reddit"))
        assertFalse(manager.install("reddit"))
        assertEquals(emptyList(), artifacts.asked)
    }

    @Test
    fun onlyWhatTheCatalogueOffersCanBeInstalled() = runBlocking {
        catalogue = catalogueJson(
            entryJson("reddit", "1.0.0", reddit, available = false),
            entryJson("stocks", "2.0.0", stocks, private = true),
            entryJson("pixiv", "1.0.0", null),
        )
        val artifacts = FakeArtifacts(served)
        val manager = manager(artifacts)
        manager.refresh()
        listOf("reddit", "stocks", "pixiv", "nothing").forEach { assertFalse(manager.install(it), it) }
        assertEquals(emptyList(), artifacts.asked)

        manager.showPrivate(true)
        assertTrue(manager.install("stocks"), "a private plugin, while private plugins are shown")
    }

    @Test
    fun anUpdateReplacesTheVersionBeforeIt() = runBlocking {
        val manager = manager()
        manager.refresh()
        manager.install("reddit")
        val before = assertIs<PluginLoad.Loaded>(manager.load("reddit"))

        val newer = pluginJar("reddit", "1.1.0")
        served["https://plugins.example/xta-plugin-reddit-1.1.0.jar"] = newer
        catalogue = catalogueJson(entryJson("reddit", "1.1.0", newer))
        manager.refresh()
        assertEquals(PluginStatus.UpdateAvailable("1.0.0", "1.1.0"), manager.status("reddit"))
        assertTrue(manager.update("reddit"))
        assertEquals(PluginStatus.Installed("1.1.0"), manager.status("reddit"))
        assertEquals(listOf("1.1.0"), File(plugins, "reddit").list()?.toList())

        val after = assertIs<PluginLoad.Loaded>(manager.load("reddit"))
        assertEquals("1.1.0", after.installed.version)
        assertNotSame(before.plugin, after.plugin)
        assertFalse(manager.update("stocks"), "nothing installed to update")
    }

    @Test
    fun anUpdateThatFailedSaysWhyKeepsTheVersionBeforeAndIsOfferedAgain() = runBlocking {
        val manager = manager()
        manager.refresh()
        manager.install("reddit")
        val newer = pluginJar("reddit", "1.1.0")
        catalogue = catalogueJson(entryJson("reddit", "1.1.0", newer))
        manager.refresh()

        assertFalse(manager.update("reddit"))
        val failed = assertIs<PluginStatus.Failed>(manager.status("reddit"))
        assertEquals(PluginFailure.DownloadFailed(404, null), failed.reason, "the server's answer, for the store to say")
        assertEquals("1.0.0", failed.installed)
        assertEquals("1.1.0", failed.update, "the update is still on offer")
        assertEquals(listOf("1.0.0"), File(plugins, "reddit").list()?.toList())

        served["https://plugins.example/xta-plugin-reddit-1.1.0.jar"] = newer
        assertTrue(manager.update("reddit"))
        assertEquals(PluginStatus.Installed("1.1.0"), manager.status("reddit"))
    }

    @Test
    fun theCatalogueNeverTakesAwayWhatIsInstalled(): Unit = runBlocking {
        val manager = manager()
        manager.refresh()
        manager.install("reddit")
        val withdrawn = listOf(catalogueJson(entryJson("reddit", "2.0.0", reddit, available = false)), catalogueJson(), catalogueJson(entryJson("stocks", "2.0.0", stocks)))
        withdrawn.forEach { body ->
            catalogue = body
            assertTrue(manager.refresh())
            assertEquals(PluginStatus.Installed("1.0.0"), manager.status("reddit"), body)
        }
        answer = { throw IOException("offline") }
        assertFalse(manager.refresh())
        assertEquals(PluginStatus.Installed("1.0.0"), manager.status("reddit"))
        assertTrue(File(plugins, "reddit/1.0.0/xta-plugin-reddit-1.0.0.jar").exists())
        assertIs<PluginLoad.Loaded>(manager.load("reddit"))
    }

    @Test
    fun anUninstallErasesWhatThePluginKeptThenDeletesIt() = runBlocking {
        val manager = manager()
        manager.refresh()
        manager.install("reddit")
        manager.load("reddit")
        assertTrue(manager.uninstall("reddit"))
        assertEquals(listOf("reddit"), erased)
        assertFalse(File(plugins, "reddit").exists())
        assertEquals(emptyList(), manager.installed())
        assertEquals(PluginStatus.Available, manager.status("reddit"))
        assertEquals(PluginLoad.Refused(PluginFailure.NotInstalled), manager.load("reddit"))
        assertEquals(0, manager.footprint("reddit"))
    }

    @Test
    fun whenThePluginsDataCannotBeErasedNothingIsDeletedAndTheStoreSaysWhy() = runBlocking {
        val manager = manager(erase = { throw IOException("database locked") })
        manager.refresh()
        manager.install("reddit")
        assertFalse(manager.uninstall("reddit"))
        assertEquals(PluginStatus.Failed(PluginFailure.StorageFailed("IOException: database locked"), "1.0.0"), manager.status("reddit"))
        assertTrue(File(plugins, "reddit/1.0.0/xta-plugin-reddit-1.0.0.jar").exists())
        assertEquals(listOf("reddit"), manager.installed().map { it.id })
        assertEquals(reddit.size.toLong(), manager.footprint("reddit"))
    }

    @Test
    fun aCancelledInstallKeepsWhatArrivedAndTheNextOneResumesFromThere() = runBlocking {
        val artifacts = FakeArtifacts(served, holdAt = 100)
        val manager = manager(artifacts)
        manager.refresh()
        val install = async(Dispatchers.Default) { manager.install("reddit") }
        manager.awaitStatus("reddit", PluginStatus.Downloading(100, reddit.size.toLong()))
        manager.cancel("reddit")
        assertFalse(install.await())
        assertEquals(PluginStatus.Available, manager.status("reddit"))
        assertEquals(100, part("reddit", "1.0.0").length())

        artifacts.release.countDown()
        assertTrue(manager.install("reddit"))
        assertEquals(listOf(0L, 100L), artifacts.asked)
    }

    @Test
    fun twoInstallsAtOnceShareOneDownload() = runBlocking {
        val artifacts = FakeArtifacts(served, holdAt = 100)
        val manager = manager(artifacts)
        manager.refresh()
        val first = async(Dispatchers.Default) { manager.install("reddit") }
        manager.awaitStatus("reddit", PluginStatus.Downloading(100, reddit.size.toLong()))
        val second = async(Dispatchers.Default) { manager.install("reddit") }
        artifacts.release.countDown()
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(listOf(0L), artifacts.asked)
    }

    @Test
    fun aPluginChangedSinceItWasInstalledIsNotLoadedAndTheStoreSaysSo() = runBlocking {
        val manager = manager()
        manager.refresh()
        manager.install("reddit")
        File(plugins, "reddit/1.0.0/xta-plugin-reddit-1.0.0.jar").apply { setWritable(true); appendBytes(byteArrayOf(0)) }
        assertEquals(PluginLoad.Refused(PluginFailure.Damaged), manager.load("reddit"))
        assertEquals(PluginStatus.Failed(PluginFailure.Damaged, "1.0.0"), manager.status("reddit"))
    }

    @Test
    fun aJarSignedWithTheAppsKeyInstallsAndLoadsOnTheDesktopAndAnotherIsRefused() = runBlocking {
        val signed = TestKeys.sign(pluginJar("hello", "1.0.0"), TestKeys.app)
        val forged = TestKeys.sign(pluginJar("forged", "1.0.0"), TestKeys.other)
        served["https://plugins.example/xta-plugin-hello-1.0.0.jar"] = signed
        served["https://plugins.example/xta-plugin-forged-1.0.0.jar"] = forged
        catalogue = catalogueJson(entryJson("hello", "1.0.0", signed), entryJson("forged", "1.0.0", forged))
        val manager = manager(host = HostPlatform.desktop(TestKeys.fingerprint(TestKeys.app), testPluginsHidden))
        manager.refresh()

        assertTrue(manager.install("hello"))
        val loaded = assertIs<PluginLoad.Loaded>(manager.load("hello"))
        assertEquals("hello from a plugin", (loaded.plugin as Supplier<*>).get())
        assertSame(loaded, manager.load("hello"), "loaded once")

        assertFalse(manager.install("forged"))
        assertIs<PluginFailure.BadSignature>((manager.status("forged") as PluginStatus.Failed).reason)
        assertNull(manager.installed().firstOrNull { it.id == "forged" })
        assertFalse(File(plugins, "forged").exists())
    }
}
