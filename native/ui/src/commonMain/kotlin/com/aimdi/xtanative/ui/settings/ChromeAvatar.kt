package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.profile.ProfileHeader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture in the upper left and atop the account menu (XTA's
 * ChromeAvatarStore): one the reader chose on the device, or their X profile
 * photo, kept in one file of the app's own and never sent anywhere; without
 * one the account's monogram shows. Settings › Accounts' revision changes with
 * each picture, so the screens draw the new one; 0 is none.
 */
class ChromeAvatar(private val file: File, private val settings: SettingsStore, private val media: MediaHttp) {
    /** The picture at [revision], or null for the monogram: none chosen, or a revision a backup brought without its file. */
    fun pictureAt(revision: Int): File? = file.takeIf { revision > 0 && it.isFile }

    /** [bytes] as the picture; nothing changes for none. */
    suspend fun set(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        withContext(Dispatchers.IO) { written(bytes) }
        settings.update { it.with(AccountPrefs.avatarRevision, it[AccountPrefs.avatarRevision].coerceAtLeast(0) + 1) }
    }

    /** The monogram again: the picture's file goes. */
    suspend fun clear() {
        withContext(Dispatchers.IO) { file.delete() }
        settings.set(AccountPrefs.avatarRevision, 0)
    }

    /** [screenName]'s X profile photo as the picture, in X's larger size; throws when there is none to load. */
    suspend fun fromProfile(client: XClient, screenName: String) {
        val url = client.profile(screenName).first.avatarUrl?.trim()?.takeIf { it.isNotEmpty() } ?: throw IOException("No profile photo")
        set(downloaded(ProfileHeader.fullAvatarUrl(url)))
    }

    private suspend fun downloaded(url: String): ByteArray = withContext(Dispatchers.IO) {
        media.get(url, emptyMap()).use { answer ->
            if (answer.code !in 200..299) throw IOException("HTTP ${answer.code}")
            pictureOf(answer.body) ?: throw IOException("Not a profile photo")
        }
    }

    private fun written(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, file.name + ".part")
        partial.writeBytes(bytes)
        if (!partial.renameTo(file)) {
            file.delete()
            partial.renameTo(file)
        }
    }

    companion object {
        /** The largest picture kept: a profile photo is far smaller. */
        const val MAX_BYTES = 8 * 1024 * 1024

        /** The kinds of picture the chooser offers on the desktop. */
        private val PICTURES = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

        fun isPictureName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in PICTURES

        /** What [stream] holds, read up to [MAX_BYTES]; null when it holds nothing or more. */
        fun pictureOf(stream: InputStream): ByteArray? {
            val read = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (read.size() <= MAX_BYTES) {
                val count = stream.read(buffer)
                if (count < 0) break
                read.write(buffer, 0, count)
            }
            return read.toByteArray().takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
        }
    }
}
