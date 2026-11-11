package com.aimdi.xtanative.plugins.immich

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.networks.immich.FakeImmich
import com.aimdi.xtanative.networks.immich.ImmichClient
import com.aimdi.xtanative.networks.immich.ImmichProblem
import com.aimdi.xtanative.networks.immich.ImmichUploadReport
import com.aimdi.xtanative.networks.immich.ImmichUploadStatus
import com.aimdi.xtanative.networks.immich.ImmichUploader
import com.aimdi.xtanative.networks.immich.fixture
import com.aimdi.xtanative.networks.immich.json
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.SavedMedia
import com.aimdi.xtanative.store.settings.ImmichPrefs
import com.aimdi.xtanative.store.settings.SecretPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.ConnectionStatus
import com.aimdi.xtanative.ui.plugins.PluginFiledPost
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** A post filed in a folder set to upload, sent to Immich as XTA's folder_picker.dart sends it, and Test connection. */
class ImmichSavesTest {
    private val photo = SavedMedia("m1", XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/AbC123.jpg")
    private val video = SavedMedia(
        "m2",
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/T.jpg",
        listOf(XVideoVariant("https://video.twimg.com/x/1280x720/high.mp4", 2_176_000, "video/mp4")),
    )
    private val filed = PluginFiledPost("1", "Art", listOf(photo, video))
    private val configured = Settings().with(ImmichPrefs.serverUrl, "http://immich.local:2283").with(ImmichPrefs.apiKey, "key")

    private fun tempStore() = StoreDir(Files.createTempDirectory("immich-plugin-test").toFile())

    /** An Immich that creates each asset and keeps the albums of the fixture, "Art" among them. */
    private fun immich() = FakeImmich { sent ->
        when {
            sent.url.encodedPath == "/api/assets" -> json(fixture("asset_created.json"), 201)
            sent.method == "GET" -> json(fixture("albums.json"))
            else -> json(fixture("album_assets.json"))
        }
    }

    private fun upload(http: FakeImmich, settings: Settings = configured, store: StoreDir = tempStore()) = runBlocking {
        ImmichSaves.upload(ImmichUploader(http, ImmichDeviceLog(store.file("uploads.json"))), settings, filed)
    }

    @Test
    fun aFiledPostsMediaGoUpIntoTheAlbumNamedForItsFolder() {
        val http = immich()
        assertEquals(ImmichUploadStatus.DONE, upload(http).status)
        assertEquals(listOf("https://pbs.twimg.com/media/AbC123.jpg:orig", "https://video.twimg.com/x/1280x720/high.mp4"), http.downloaded.map { it.toString() })
        assertEquals("/api/albums/6a1b2c3d-0000-4000-8000-000000000003/assets", http.sent.last().url.encodedPath, "the album Art, as the folder is named")
    }

    @Test
    fun withoutAnAlbumPerFolderTheyGoIntoTheLibraryAlone() {
        val http = immich()
        upload(http, configured.with(ImmichPrefs.albumPerFolder, false))
        assertEquals(listOf("/api/assets", "/api/assets"), http.paths)
    }

    @Test
    fun videosStayBehindWhileOnlyPhotosAreWanted() {
        val http = immich()
        upload(http, configured.with(ImmichPrefs.includeVideos, false))
        assertEquals(listOf("https://pbs.twimg.com/media/AbC123.jpg:orig"), http.downloaded.map { it.toString() })
    }

    @Test
    fun whatWasSentIsNotSentAgainWhenThePostIsFiledAgain() {
        val store = tempStore()
        upload(immich(), store = store)
        val again = immich()
        assertEquals(ImmichUploadStatus.NOTHING_TO_DO, upload(again, store = store).status)
        assertTrue(again.downloaded.isEmpty())
    }

    @Test
    fun withoutAnAddressOrAKeyNothingIsFetchedOrSent() {
        val http = immich()
        assertEquals(ImmichUploadStatus.NOT_CONFIGURED, upload(http, Settings()).status)
        assertTrue(http.sent.isEmpty() && http.downloaded.isEmpty())
    }

    @Test
    fun eachEndSaysWhatXtaSays() {
        assertNull(ImmichSaves.notice(ImmichUploadReport(ImmichUploadStatus.NOTHING_TO_DO)), "nothing worth interrupting the reader for")
        assertEquals(Strings.not_configured, ImmichSaves.notice(ImmichUploadReport(ImmichUploadStatus.NOT_CONFIGURED)))
        assertEquals(Strings.upload_done, ImmichSaves.notice(ImmichUploadReport(ImmichUploadStatus.DONE, uploaded = 1)))
        assertEquals(Strings.upload_failed, ImmichSaves.notice(ImmichUploadReport(ImmichUploadStatus.FAILED, failures = 1)))
    }

    @Test
    fun aFailedUploadSaysWhatTheServerSaidAsTestConnectionWould() {
        fun failed(problem: ImmichProblem) = ImmichSaves.notice(ImmichUploadReport(ImmichUploadStatus.FAILED, failures = 2, problem = problem))
        assertEquals(Strings.error_unauthorized, failed(ImmichProblem.UNAUTHORIZED))
        assertEquals(Strings.error_server_url, failed(ImmichProblem.BAD_SERVER))
        assertEquals(Strings.error_network, failed(ImmichProblem.NETWORK))
        assertEquals(Strings.error_generic, failed(ImmichProblem.SERVER))
    }

    @Test
    fun testConnectionSaysConnectedOrWhyNot(): Unit = runBlocking {
        suspend fun test(http: FakeImmich, address: String = "http://immich.local:2283") = ImmichSaves.test(ImmichClient(http), address, "key")
        assertEquals(ConnectionStatus.OK to Strings.test_ok, test(FakeImmich { json(fixture("users_me.json")) }))
        assertEquals(ConnectionStatus.FAILED to Strings.not_configured, test(FakeImmich(), address = ""))
        assertEquals(ConnectionStatus.FAILED to Strings.error_unauthorized, test(FakeImmich { json("{}", 401) }))
        assertEquals(ConnectionStatus.FAILED to Strings.error_server_url, test(FakeImmich { XResponse(200, "<html>router</html>") }))
        assertEquals(ConnectionStatus.FAILED to Strings.error_generic, test(FakeImmich { json("""{"message":"down"}""", 500) }))
        assertEquals(ConnectionStatus.FAILED to Strings.error_network, test(FakeImmich { throw IOException("no route to host") }))
    }

    @Test
    fun theKeyIsTheReadersOwnSecretUnderThePluginsKeys() {
        val prefix = PluginSettings.prefixOf("immich")
        val prefs = listOf(ImmichPrefs.serverUrl, ImmichPrefs.apiKey, ImmichPrefs.albumPerFolder, ImmichPrefs.includeVideos)
        assertTrue(prefs.all { it.key.startsWith(prefix) }, "uninstalling erases what is under the prefix")
        assertTrue(SecretPrefs.isSecret(ImmichPrefs.apiKey.key))
        val exported = configured.exported()!!
        assertFalse(ImmichPrefs.apiKey.key in exported, "no export or backup carries the key")
        assertTrue(ImmichPrefs.serverUrl.key in exported && ImmichPrefs.albumPerFolder.key in exported, "the address and the options travel, as in XTA's backup")
    }
}
