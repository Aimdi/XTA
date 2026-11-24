package com.aimdi.xtanative.ui.common

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The desktop picture cache: in the user's own cache folder, and the old one in /tmp cleared only when it is the user's. */
class PictureCacheFolderTest {
    @Test
    fun theFolderIsUnderTheUsersCacheNeverTmp() {
        assertEquals(File("/c/xta-native/images"), PictureCacheFolder.folder("/c", "/home/u"))
        assertEquals(File("/home/u/.cache/xta-native/images"), PictureCacheFolder.folder(null, "/home/u"))
        assertEquals(File("/home/u/.cache/xta-native/images"), PictureCacheFolder.folder("", "/home/u"))
        assertEquals(File("/home/u/.cache/xta-native/images"), PictureCacheFolder.folder("relative/cache", "/home/u"), "a relative XDG_CACHE_HOME is ignored")
    }

    @Test
    fun theFolderIsMadeForItsOwnerAlone() {
        val root = Files.createTempDirectory("picture-cache").toFile()
        try {
            val folder = PictureCacheFolder.prepare(File(root, "xta-native/images"))
            assertTrue(folder.isDirectory)
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.toPath())))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun onlyTheUsersOwnRealOldFolderIsCleared() {
        val root = Files.createTempDirectory("picture-cache").toFile()
        try {
            val me = System.getProperty("user.name")
            val old = File(root, "coil3_disk_cache").apply { mkdirs() }
            File(old, "journal").writeText("x")
            File(old, "sub").mkdirs()
            File(old, "sub/a.1").writeText("x")
            val outside = File(root, "outside").apply { mkdirs() }
            File(outside, "keep").writeText("x")
            Files.createSymbolicLink(File(old, "link").toPath(), outside.toPath())
            val linked = File(root, "linked")
            Files.createSymbolicLink(linked.toPath(), outside.toPath())

            assertFalse(PictureCacheFolder.ownedFolder(old, "$me-someone-else"), "another user's is left alone")
            assertFalse(PictureCacheFolder.ownedFolder(linked, me), "a link is never followed")
            assertFalse(PictureCacheFolder.ownedFolder(File(root, "missing"), me))
            assertTrue(PictureCacheFolder.ownedFolder(old, me))

            PictureCacheFolder.delete(old.toPath())
            assertFalse(old.exists())
            assertTrue(File(outside, "keep").isFile, "what a link inside pointed at stays")
        } finally {
            root.deleteRecursively()
        }
    }
}
