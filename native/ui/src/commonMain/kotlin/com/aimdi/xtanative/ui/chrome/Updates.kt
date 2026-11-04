package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** What updates an install: the APK on Android, the tarball or the Arch package on the desktop, as each prerelease carries them. */
enum class UpdateFile {
    APK,
    TARBALL,
    PACKAGE,
    ;

    /** Whether the file named [name] is this one. */
    fun names(name: String): Boolean = when (this) {
        APK -> name.startsWith("xta-native-") && name.endsWith(".apk")
        TARBALL -> name.startsWith("xta-native-desktop-") && name.endsWith("-linux-x64.tar.gz")
        PACKAGE -> name.startsWith("xta-native-") && name.endsWith(".pkg.tar.zst")
    }
}

/** A prerelease of XTA Native, `native-0.1.45`: its [version], its page on GitHub and its files' addresses by name. */
data class NativeRelease(val tag: String, val version: List<Int>, val page: String, val files: Map<String, String>) {
    /** The address of the file that updates an install of [file], or null when this release has none. */
    fun fileFor(file: UpdateFile): String? = files.entries.firstOrNull { file.names(it.key) }?.value
}

/**
 * XTA's update check (lib/utils/updates.dart) for XTA Native, whose
 * releases are this repository's `native-*` prereleases, never marked
 * latest: the newest of them that carries this install's file is offered
 * when it is newer than this build.
 */
object NativeUpdates {
    const val REPOSITORY = "Aimdi/XTA"

    /** GitHub's newest releases, XTA's own among them. */
    val RELEASES: HttpUrl = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100".toHttpUrl()

    /** Where the Arch package puts the app; the tarball goes wherever the reader unpacks it. */
    private const val PACKAGE_HOME = "/opt/xta-native/"

    private val TAG = Regex("""^native-(\d+(?:\.\d+)*)$""")

    /** `native-0.1.45` as [0, 1, 45]; null for any other tag (`native-plugins`, XTA's `aimdi186`). */
    fun versionOf(tag: String): List<Int>? = TAG.find(tag.trim())?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

    /** A version name, `0.1.45`, as numbers; null for one that is not. */
    fun numbersOf(version: String): List<Int>? = version.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

    /** The prereleases GitHub lists in [json], drafts left out. */
    fun releases(json: String): List<NativeRelease> = J.parse(json).list.mapNotNull { release ->
        val tag = release["tag_name"].string ?: return@mapNotNull null
        val version = versionOf(tag) ?: return@mapNotNull null
        if (release["draft"].boolean == true) return@mapNotNull null
        val files = release["assets"].list.mapNotNull { asset ->
            val name = asset["name"].string ?: return@mapNotNull null
            asset["browser_download_url"].string?.let { name to it }
        }.toMap()
        NativeRelease(tag, version, release["html_url"].string ?: "https://github.com/$REPOSITORY/releases/tag/$tag", files)
    }

    /** The newest of [releases] that carries [file]. */
    fun newest(releases: List<NativeRelease>, file: UpdateFile): NativeRelease? =
        releases.filter { it.fileFor(file) != null }.maxWithOrNull { a, b -> compare(a.version, b.version) }

    /**
     * Whether [latest] is newer than this build. [installedTag] is its own
     * release (`native-0.1.45`) and wins; a local build has none, and only
     * knows the version its build file names, whose last number is the run
     * that would have built it: as XTA ignores fork builds of its own
     * version, only the numbers before it are compared then.
     */
    fun isNewer(latest: NativeRelease, installedTag: String, installedVersion: String): Boolean {
        versionOf(installedTag)?.let { return compare(latest.version, it) > 0 }
        val installed = numbersOf(installedVersion) ?: return false
        return compare(latest.version.dropLast(1), installed.dropLast(1)) > 0
    }

    /** What updates a desktop install whose Java runtime is at [javaHome]: the package's own place, else the tarball. */
    fun desktopFile(javaHome: String): UpdateFile = if (javaHome.startsWith(PACKAGE_HOME)) UpdateFile.PACKAGE else UpdateFile.TARBALL

    /**
     * Reads the releases and says which one to offer this install of [file]
     * ([installedTag], [installedVersion] as [isNewer] takes them): null when
     * none is newer, or when GitHub does not answer.
     */
    suspend fun check(http: XHttp, file: UpdateFile, installedTag: String, installedVersion: String): NativeRelease? {
        val response = try {
            http.get(RELEASES, mapOf("Accept" to "application/vnd.github+json"), followRedirects = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return null
        }
        if (response.code != 200) return null
        return newest(releases(response.body), file)?.takeIf { isNewer(it, installedTag, installedVersion) }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
