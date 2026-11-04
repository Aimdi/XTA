package com.aimdi.xtanative.ui.settings

import java.io.File

/**
 * How long a picture stays in the picture cache, which X and every plugin
 * share (XTA's imageDiskCacheMaxAge): without an age the cache only ever
 * grew. Trimmed once a launch by when each file was written, as XTA's
 * trimImageDiskCache does; the cache finds a picture gone and reads it again.
 */
object ImageCacheAge {
    const val MAX_AGE_DAYS = 30
    private const val MAX_AGE_MILLIS = MAX_AGE_DAYS * 24 * 60 * 60 * 1000L

    /** The pictures of [folder] written more than [MAX_AGE_DAYS] days before [now]; never the cache's own journal. */
    fun expired(folder: File, now: Long): List<File> =
        folder.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(JOURNAL) && it.lastModified() in 1 until now - MAX_AGE_MILLIS }

    /** How many of [expired] went; a cache that cannot be read is trimmed at the next launch. */
    fun trim(folder: File, now: Long = System.currentTimeMillis()): Int = runCatching { expired(folder, now).count { it.delete() } }.getOrDefault(0)

    /** The disk cache's journal and its copies (`journal.tmp`, `journal.bkp`). */
    private const val JOURNAL = "journal"
}
