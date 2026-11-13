package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.core.json.J
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

/** What a plugin says of itself in `xta-plugin.json`, inside its JAR or APK. */
data class PluginManifest(
    val id: String,
    val version: String,
    /** The `:plugin-api` version it was built against. */
    val apiVersion: Int,
    /** The class implementing XtaPlugin, made with its no-argument constructor. */
    val entryClass: String,
) {
    companion object {
        const val NAME = "xta-plugin.json"

        private const val LIMIT = 64 * 1024

        /**
         * The manifest in [file], a JAR or an APK (both are zip files), at its
         * root or under `assets/`; null when there is none or it is not one.
         */
        fun read(file: File): PluginManifest? = try {
            ZipFile(file).use { zip ->
                listOf(NAME, "assets/$NAME").firstNotNullOfOrNull { zip.getEntry(it) }
                    ?.let { entry -> zip.getInputStream(entry).use { readAtMost(it, LIMIT) } }
                    ?.let { parse(it.toString(Charsets.UTF_8)) }
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

        /** The manifest [text] holds; null when a field is missing or could not be what it names. */
        fun parse(text: String): PluginManifest? {
            val root = J.parse(text)
            return PluginManifest(
                id = root["id"].string?.takeIf(PluginNames::isId) ?: return null,
                version = root["version"].string?.takeIf(PluginNames::isVersion) ?: return null,
                apiVersion = root["apiVersion"].int?.takeIf { it > 0 } ?: return null,
                entryClass = root["entry"].string?.takeIf(PluginNames::isClassName) ?: return null,
            )
        }

        /** [input]'s bytes, or null when there are more than [limit]. */
        private fun readAtMost(input: InputStream, limit: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            generateSequence { input.read(buffer).takeIf { it >= 0 } }
                .takeWhile { out.size() <= limit }
                .forEach { out.write(buffer, 0, it) }
            return out.toByteArray().takeIf { it.size <= limit }
        }
    }
}
