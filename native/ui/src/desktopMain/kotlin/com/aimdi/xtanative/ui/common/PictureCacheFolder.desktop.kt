package com.aimdi.xtanative.ui.common

import coil3.disk.DiskCache
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import okio.Path.Companion.toOkioPath

/** The user's own cache folder, readable by the user alone; the cache Coil first kept in `/tmp` cleared away beside it. */
internal actual fun platformPictureDiskCache(): (() -> DiskCache)? = {
    PictureCacheFolder.clearLegacy()
    // Coil's own size limits (2% of the disk, 10 to 250 MB), as before: only the folder moves.
    DiskCache.Builder().directory(PictureCacheFolder.prepare(PictureCacheFolder.folder()).toOkioPath()).build()
}

/** The desktop's picture cache folder, `$XDG_CACHE_HOME/xta-native/images`, and the one Coil kept before it. */
internal object PictureCacheFolder {
    /** The folder for a [cacheHome] (`$XDG_CACHE_HOME`, ignored unless absolute, as the XDG spec has it), else `~/.cache` under [home]. */
    fun folder(cacheHome: String? = System.getenv("XDG_CACHE_HOME"), home: String = System.getProperty("user.home")): File {
        val cache = cacheHome?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isAbsolute } ?: File(home, ".cache")
        return File(File(cache, "xta-native"), "images")
    }

    /** [folder] made, and readable, writable and listable by its owner alone. */
    fun prepare(folder: File): File = folder.also {
        runCatching {
            Files.createDirectories(it.toPath())
            Files.setPosixFilePermissions(it.toPath(), PosixFilePermissions.fromString("rwx------"))
        }
    }

    /** Where Coil kept the cache before: its folder in the system's temporary folder. */
    fun legacy(): File = File(System.getProperty("java.io.tmpdir"), "coil3_disk_cache")

    /**
     * [old] and everything in it deleted, in the background, if it is a real
     * folder (not a link) owned by [user]: another user's cache, or one put
     * there in this user's name, is left alone.
     */
    fun clearLegacy(old: File = legacy(), user: String = System.getProperty("user.name")) {
        if (!ownedFolder(old, user)) return
        Thread({ delete(old.toPath()) }, "picture-cache-legacy").apply { isDaemon = true }.start()
    }

    /** Whether [folder] is a folder itself, not a link to one, and [user]'s. */
    fun ownedFolder(folder: File, user: String): Boolean = runCatching {
        val path = folder.toPath()
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).name == user
    }.getOrDefault(false)

    /** [root] and what it holds deleted, never following a link out of it. */
    fun delete(root: Path) {
        runCatching {
            Files.walkFileTree(
                root,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        runCatching { Files.deleteIfExists(dir) }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }
    }
}
