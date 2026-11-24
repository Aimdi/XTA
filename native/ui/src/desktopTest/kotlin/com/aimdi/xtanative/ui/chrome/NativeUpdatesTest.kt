package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's update check (updates_test.dart) for XTA Native's `v*` releases and `native-*` prereleases. */
class NativeUpdatesTest {
    private val releases = """
        [
          {"tag_name": "aimdi186", "draft": false, "prerelease": false, "html_url": "https://github.com/Aimdi/XTA/releases/tag/aimdi186",
           "assets": [{"name": "xta-aimdi186_arm64-v8a.apk", "browser_download_url": "https://example.invalid/xta.apk"}]},
          {"tag_name": "native-plugins", "draft": false, "prerelease": true, "html_url": "https://github.com/Aimdi/XTA/releases/tag/native-plugins",
           "assets": [{"name": "xta-plugin-bluesky-1.0.0.12.apk", "browser_download_url": "https://example.invalid/bluesky.apk"}]},
          {"tag_name": "native-0.1.47", "draft": false, "prerelease": true, "html_url": "https://github.com/Aimdi/XTA/releases/tag/native-0.1.47",
           "assets": [
             {"name": "xta-native-0.1.47.apk", "browser_download_url": "https://github.com/Aimdi/XTA/releases/download/native-0.1.47/xta-native-0.1.47.apk"},
             {"name": "xta-native-desktop-0.1.47-linux-x64.tar.gz", "browser_download_url": "https://github.com/Aimdi/XTA/releases/download/native-0.1.47/xta-native-desktop-0.1.47-linux-x64.tar.gz"},
             {"name": "xta-native-0.1.47-1-x86_64.pkg.tar.zst", "browser_download_url": "https://github.com/Aimdi/XTA/releases/download/native-0.1.47/xta-native-0.1.47-1-x86_64.pkg.tar.zst"},
             {"name": "PKGBUILD", "browser_download_url": "https://github.com/Aimdi/XTA/releases/download/native-0.1.47/PKGBUILD"}
           ]},
          {"tag_name": "native-0.1.48", "draft": true, "prerelease": true, "html_url": "https://github.com/Aimdi/XTA/releases/tag/native-0.1.48",
           "assets": [{"name": "xta-native-0.1.48.apk", "browser_download_url": "https://example.invalid/draft.apk"}]},
          {"tag_name": "native-0.1.9", "draft": false, "prerelease": true, "html_url": "https://github.com/Aimdi/XTA/releases/tag/native-0.1.9",
           "assets": [{"name": "xta-native-0.1.9.apk", "browser_download_url": "https://example.invalid/old.apk"}]}
        ]
    """.trimIndent()

    private val newest get() = NativeUpdates.newest(NativeUpdates.releases(releases), UpdateFile.APK)!!

    @Test
    fun onlyXtaNativesPrereleasesAreVersionsDraftsAndThePluginsLeftOut() {
        assertEquals(listOf(0, 1, 45), NativeUpdates.versionOf("native-0.1.45"))
        assertNull(NativeUpdates.versionOf("native-plugins"))
        assertNull(NativeUpdates.versionOf("aimdi186"))
        assertNull(NativeUpdates.versionOf("v4.12.0-aimdi44"))
        assertEquals(listOf("native-0.1.47", "native-0.1.9"), NativeUpdates.releases(releases).map { it.tag })
    }

    @Test
    fun theNewestIsOrderedByNumberNotByText() {
        assertEquals("native-0.1.47", newest.tag)
        assertEquals("https://github.com/Aimdi/XTA/releases/tag/native-0.1.47", newest.page)
    }

    @Test
    fun eachInstallIsOfferedItsOwnFile() {
        assertEquals("https://github.com/Aimdi/XTA/releases/download/native-0.1.47/xta-native-0.1.47.apk", newest.fileFor(UpdateFile.APK))
        assertEquals(
            "https://github.com/Aimdi/XTA/releases/download/native-0.1.47/xta-native-desktop-0.1.47-linux-x64.tar.gz",
            newest.fileFor(UpdateFile.TARBALL),
        )
        assertEquals(
            "https://github.com/Aimdi/XTA/releases/download/native-0.1.47/xta-native-0.1.47-1-x86_64.pkg.tar.zst",
            newest.fileFor(UpdateFile.PACKAGE),
        )
    }

    @Test
    fun aReleaseWithoutTheInstallsFileIsNotOffered() {
        assertEquals("native-0.1.47", NativeUpdates.newest(NativeUpdates.releases(releases), UpdateFile.PACKAGE)?.tag)
        val apkOnly = NativeUpdates.releases(releases).filter { it.tag == "native-0.1.9" }
        assertNull(NativeUpdates.newest(apkOnly, UpdateFile.TARBALL))
    }

    @Test
    fun theInstalledBuildIsNotAnUpdateToItself() {
        assertFalse(NativeUpdates.isNewer(newest, installedTag = "native-0.1.47", installedVersion = "0.1.47"))
        assertTrue(NativeUpdates.isNewer(newest, installedTag = "native-0.1.45", installedVersion = "0.1.45"))
        assertFalse(NativeUpdates.isNewer(newest, installedTag = "native-0.1.50", installedVersion = "0.1.50"))
    }

    @Test
    fun aLocalBuildIgnoresTheRunsOfItsOwnVersionButSeesARealBump() {
        assertFalse(NativeUpdates.isNewer(newest, installedTag = "", installedVersion = "0.1.0"))
        assertTrue(NativeUpdates.isNewer(newest.copy(version = listOf(0, 2, 3)), installedTag = "", installedVersion = "0.1.0"))
        assertFalse(NativeUpdates.isNewer(newest, installedTag = "", installedVersion = "dev"))
    }

    private val withARelease = """
        [
          {"tag_name": "native-1.0.0.90", "draft": false, "prerelease": true, "html_url": "https://github.com/Aimdi/XTA/releases/tag/native-1.0.0.90",
           "assets": [{"name": "xta-native-1.0.0.90.apk", "browser_download_url": "https://example.invalid/pre.apk"}]},
          {"tag_name": "v1.0.0", "draft": false, "prerelease": false, "html_url": "https://github.com/Aimdi/XTA/releases/tag/v1.0.0",
           "assets": [{"name": "xta-native-1.0.0.apk", "browser_download_url": "https://example.invalid/release.apk"}]},
          {"tag_name": "v4.12.0-fix33", "draft": false, "prerelease": false, "html_url": "https://github.com/Aimdi/XTA/releases/tag/v4.12.0-fix33",
           "assets": [{"name": "xta-native-9.9.9.apk", "browser_download_url": "https://example.invalid/old-fork.apk"}]},
          {"tag_name": "native-0.1.82", "draft": false, "prerelease": true, "html_url": "https://github.com/Aimdi/XTA/releases/tag/native-0.1.82",
           "assets": [{"name": "xta-native-0.1.82.apk", "browser_download_url": "https://example.invalid/old.apk"}]}
        ]
    """.trimIndent()

    @Test
    fun aReleaseIsAVersionAndKnowsItIsNoPrerelease() {
        assertEquals(listOf(1, 0, 0), NativeUpdates.versionOf("v1.0.0"))
        assertNull(NativeUpdates.versionOf("v4.12.0-fix33"))
        val listed = NativeUpdates.releases(withARelease).associate { it.tag to it.prerelease }
        assertEquals(mapOf("native-1.0.0.90" to true, "v1.0.0" to false, "native-0.1.82" to true), listed)
        assertTrue(NativeUpdates.isReleaseTag("v1.0.0"))
        assertFalse(NativeUpdates.isReleaseTag("native-0.1.82"))
        assertFalse(NativeUpdates.isReleaseTag(""))
    }

    @Test
    fun aPrereleaseIsOfferedEitherAndAReleaseOnlyANewerRelease(): Unit = runBlocking {
        val answers = XHttp { _, _, _ -> XResponse(200, withARelease) }
        assertEquals("native-1.0.0.90", NativeUpdates.check(answers, UpdateFile.APK, "native-0.1.82", "0.1.82")?.tag)
        val onlyTheRelease = XHttp { _, _, _ -> XResponse(200, withARelease.replace("native-1.0.0.90", "native-0.1.80")) }
        assertEquals("v1.0.0", NativeUpdates.check(onlyTheRelease, UpdateFile.APK, "native-0.1.82", "0.1.82")?.tag)
        assertNull(NativeUpdates.check(answers, UpdateFile.APK, "v1.0.0", "1.0.0"))
        val newer = XHttp { _, _, _ -> XResponse(200, withARelease.replace("v1.0.0", "v1.0.1").replace("xta-native-1.0.0.apk", "xta-native-1.0.1.apk")) }
        assertEquals("v1.0.1", NativeUpdates.check(newer, UpdateFile.APK, "v1.0.0", "1.0.0")?.tag)
    }

    @Test
    fun theDesktopsPackageIsTheOneUnderOptThatPacmanLists() {
        val pacman = listOf("glibc-2.42-1", "xta-native-1.0.14.30-1", "zstd-1.5.7-2")
        assertEquals(UpdateFile.PACKAGE, NativeUpdates.desktopFile("/opt/xta-native/lib/runtime", pacman))
        assertEquals(UpdateFile.TARBALL, NativeUpdates.desktopFile("/home/reader/xta-native-desktop-0.1.47-linux-x64/lib/runtime", pacman))
        assertEquals(UpdateFile.TARBALL, NativeUpdates.desktopFile("/opt/xta-native/lib/runtime", emptyList()), "copied to /opt by hand, or no pacman")
        assertEquals(UpdateFile.TARBALL, NativeUpdates.desktopFile("/opt/xta-native/lib/runtime", listOf("xta-native-bin-1.0-1", "xta-native-git-r12.abc-1")), "another package of a like name")
    }

    @Test
    fun theOfferSaysHowToInstallWhatItDownloads() {
        assertEquals("sudo pacman -U ./xta-native-1.0.15-1-x86_64.pkg.tar.zst", NativeUpdates.installCommand(UpdateFile.PACKAGE, "xta-native-1.0.15-1-x86_64.pkg.tar.zst"))
        assertEquals(
            "tar -xzf xta-native-desktop-1.0.15-linux-x64.tar.gz && ./xta-native-desktop-1.0.15-linux-x64/install.sh",
            NativeUpdates.installCommand(UpdateFile.TARBALL, "xta-native-desktop-1.0.15-linux-x64.tar.gz"),
        )
        assertNull(NativeUpdates.installCommand(UpdateFile.APK, "xta-native-1.0.15.apk"))
        assertNull(NativeUpdates.installCommand(UpdateFile.PACKAGE, "xta-native-1;rm -rf ~.pkg.tar.zst"), "no shell in a name")
        assertNull(NativeUpdates.installCommand(UpdateFile.PACKAGE, "xta-native-desktop-1.0.15-linux-x64.tar.gz"), "not the package")
        val release = NativeRelease("v1.0.15", listOf(1, 0, 15), "p", mapOf("xta-native-1.0.15-1-x86_64.pkg.tar.zst" to "u1", "xta-native-desktop-1.0.15-linux-x64.tar.gz" to "u2"))
        assertEquals("xta-native-1.0.15-1-x86_64.pkg.tar.zst", release.fileNameFor(UpdateFile.PACKAGE))
        assertEquals("xta-native-desktop-1.0.15-linux-x64.tar.gz", release.fileNameFor(UpdateFile.TARBALL))
        assertNull(release.fileNameFor(UpdateFile.APK))
    }

    @Test
    fun aSkippedVersionIsNotOfferedAgainButANewerOneIs() {
        val release = NativeRelease("v1.0.15", listOf(1, 0, 15), "p", emptyMap())
        assertEquals(true, NativeUpdates.offered(release, ""))
        assertEquals(false, NativeUpdates.offered(release, "v1.0.15"))
        assertEquals(true, NativeUpdates.offered(release.copy(tag = "v1.0.16", version = listOf(1, 0, 16)), "v1.0.15"))
    }

    @Test
    fun theCheckOffersTheNewestNewerReleaseAndNothingWhenGitHubDoesNotAnswer(): Unit = runBlocking {
        val asked = mutableListOf<String>()
        val answers = XHttp { url, headers, _ ->
            asked += url.toString()
            assertEquals("application/vnd.github+json", headers["Accept"])
            XResponse(200, releases)
        }
        assertEquals("native-0.1.47", NativeUpdates.check(answers, UpdateFile.APK, "native-0.1.45", "0.1.45")?.tag)
        assertEquals(listOf("https://api.github.com/repos/Aimdi/XTA/releases?per_page=100"), asked)
        assertNull(NativeUpdates.check(answers, UpdateFile.APK, "native-0.1.47", "0.1.47"))
        assertNull(NativeUpdates.check({ _, _, _ -> XResponse(403, "rate limited") }, UpdateFile.APK, "native-0.1.1", "0.1.1"))
        assertNull(NativeUpdates.check({ _, _, _ -> throw IOException("offline") }, UpdateFile.APK, "native-0.1.1", "0.1.1"))
    }
}
