package com.aimdi.xtanative.store.published

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.repairs.Repairs
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.store.StoreFile
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * A document XTA's repository publishes for installs to read (XTA's
 * PublishedDocument: `repairs.json` and its siblings), read at most once per
 * [intervalMillis] and kept in [file] with when it was read, so the copy last
 * read applies before, or without, the network.
 */
class PublishedDocument(
    private val http: XHttp,
    val url: HttpUrl,
    private val file: StoreFile,
    private val intervalMillis: Long = DAY_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The document last read; null before the first. */
    suspend fun kept(): String? = stored()?.body?.ifEmpty { null }

    /**
     * Reads the document again when the last read is [intervalMillis] old or
     * more, and keeps it. The body read; null when nothing new was: too soon,
     * no answer, or an answer other than 200.
     */
    suspend fun refresh(): String? {
        val now = clock()
        stored()?.readAt?.let { if (now - it < intervalMillis) return null }
        val response = try {
            http.get(url, emptyMap(), followRedirects = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return null
        }
        if (response.code != 200) return null
        file.write(buildJsonObject { put("readAt", now); put("body", response.body) }.toString())
        return response.body
    }

    private class Kept(val readAt: Long?, val body: String?)

    private suspend fun stored(): Kept? = file.read()?.let(J::parse)?.let { Kept(it["readAt"].long, it["body"].string) }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** Where XTA publishes its repairs; XTA Native reads the same file. */
        val REPAIRS_URL = "https://raw.githubusercontent.com/Aimdi/XTA/HEAD/repairs.json".toHttpUrl()
    }
}

/**
 * XTA's published repairs as this install holds them (XTA's
 * PluginRepairsRegistry): the copy last read is put in force at start, before
 * any network asks for anything, and read again once a day. [published] is
 * every entry of the file; each network takes the ones it knows that fit
 * (core's [com.aimdi.xtanative.core.repairs.RepairSlot.valueIn]), so a network
 * installed later finds its entries there too.
 */
class RepairsRegistry(private val document: PublishedDocument) {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    val published: StateFlow<Map<String, String>> = state.asStateFlow()

    /** Puts the copy last read in force. How many entries it holds. */
    suspend fun applyKept(): Int = apply(document.kept().orEmpty())

    /** Reads the file again when it is due and puts it in force; null when nothing new was read, so what is in force stays. */
    suspend fun refresh(): Int? = document.refresh()?.let(::apply)

    private fun apply(body: String): Int = Repairs.parse(body).also { state.value = it }.size
}
