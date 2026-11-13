package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl

/** A fixture under `src/test/resources/ehviewer/`. */
fun fixture(name: String): String = FakeEh::class.java.getResource("/ehviewer/$name")!!.readText()

/**
 * Waits on work a call does not await (a search a changed filter runs again)
 * until [done], for at most [timeoutMillis]; then the expectation after it
 * fails. As XTA's test/support/wait_until.dart: a fixed sleep fails whenever
 * the machine is busier than the one the test was written on.
 */
suspend fun waitUntil(timeoutMillis: Long = 5_000, done: () -> Boolean) {
    withTimeoutOrNull(timeoutMillis) { while (!done()) delay(5) }
}

/** One request a fake site was asked: a POST carries its [body] and [contentType]. */
data class Asked(val url: HttpUrl, val headers: Map<String, String>, val body: String? = null, val contentType: String? = null)

/** E-Hentai answering every request with [answer], remembering what was asked. */
class FakeEh(private val answer: suspend (Asked) -> XResponse = { XResponse(200, "") }) : XHttp {
    val asked: MutableList<Asked> = CopyOnWriteArrayList()

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse = answering(Asked(url, headers))

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        answering(Asked(url, headers, body, contentType))

    private suspend fun answering(request: Asked): XResponse {
        asked += request
        return answer(request)
    }
}

/** The gallery XTA's gallery-page tests read. */
val fixtureGallery = EhGallery(gid = 9, token = "tok", title = "Sommer Book", titleJpn = "夏の本")

const val FIXTURE_PER_SHEET = 4

/** One sheet (`?p=[sheet]`) of [fixtureGallery]'s page as the site serves it, as XTA's ehGalleryHtml. */
fun galleryHtml(sheet: Int = 0, sheetCount: Int = 3, comments: Int = 4, thumbs: Boolean = false): String {
    val first = sheet * FIXTURE_PER_SHEET + 1
    val tiles = (0 until FIXTURE_PER_SHEET).joinToString("") { slot -> tile(sheet, first + slot, slot, thumbs) }
    val links = (1 until sheetCount).joinToString("") { p -> """<td><a href="https://e-hentai.org/g/9/tok/?p=$p">${p + 1}</a></td>""" }
    return """
        <h1 id="gn">Sommer Book</h1><h1 id="gj">夏の本</h1>
        <div id="gdc"><div class="cs ct2">Doujinshi</div></div>
        <div id="gdn"><a href="#">Alice</a></div>
        <div id="gdd"><table>
        <tr><td class="gdt1">Posted:</td><td class="gdt2">2024-08-11 21:36</td></tr>
        <tr><td class="gdt1">Language:</td><td class="gdt2">English &nbsp;<span class="halp" title="translated">TR</span></td></tr>
        <tr><td class="gdt1">File Size:</td><td class="gdt2">96.57 MiB</td></tr>
        <tr><td class="gdt1">Length:</td><td class="gdt2">24 pages</td></tr>
        <tr><td class="gdt1">Favorited:</td><td class="gdt2" id="favcount">1234 times</td></tr>
        </table></div>
        <td id="rating_label">Average: 4.62</td><span id="rating_count">321</span>
        <div id="taglist"><table>
        <tr><td class="tc">language:</td><td>
        <div id="td_language:english" class="gt"><a id="ta_language:english" href="#">english</a></div></td></tr>
        <tr><td class="tc">parody:</td><td>
        <div id="td_parody:original" class="gt"><a id="ta_parody:original" href="#">original</a></div></td></tr>
        <tr><td class="tc">female:</td><td>
        <div id="td_female:big_breasts" class="gt"><a id="ta_female:big_breasts" href="#">big breasts</a></div>
        <div id="td_female:twin_tails" class="gtl"><a id="ta_female:twin_tails" href="#">twin tails</a></div></td></tr>
        </table></div>
        <table class="ptt"><tr><td class="ptds"><a href="#">${sheet + 1}</a></td>$links</tr></table>
        <div id="gdt">$tiles</div>
        ${(0 until comments).joinToString("\n") { comment(it, uploader = it == 0) }}
    """.trimIndent()
}

private fun tile(sheet: Int, page: Int, slot: Int, thumbs: Boolean): String {
    val style = if (thumbs) {
        "width:200px;height:283px;background:transparent url(https://ehgt.org/sheet$sheet.webp) -${slot * 200}px 0 no-repeat"
    } else {
        "width:200px;height:283px"
    }
    return """<a href="https://e-hentai.org/s/tk$page/9-$page"><div title="Page $page" style="$style"></div></a>"""
}

private fun comment(index: Int, uploader: Boolean): String {
    val mark = if (uploader) {
        """<div class="c4 nosel">Uploader Comment</div>"""
    } else {
        """<div class="c5 nosel">Score <span id="comment_score_$index">+$index</span></div>"""
    }
    val author = if (uploader) "Alice" else "Reader $index"
    return """<div class="c1"><div class="c2"><div class="c3">Posted on 1$index August 2026, 16:44 by: &nbsp; <a href="#">$author</a></div>""" +
        """$mark</div><div class="c6" id="comment_$index">Comment number $index</div></div>"""
}

/**
 * Serves [fixtureGallery]'s sheets, failing the first [failures] requests
 * with a 500, as XTA's EhFixtureServer. A sheet [hold] is asked for waits
 * until its gate opens.
 */
class EhFixtureServer(private val sheetCount: Int = 3, private val comments: Int = 4, private val thumbs: Boolean = false) : XHttp {
    @Volatile
    var failures = 0
    private val gates = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
    val requests: MutableList<HttpUrl> = CopyOnWriteArrayList()

    val sheetsRequested: List<Int>
        get() = requests.filter { it.encodedPath.startsWith("/g/") }.map { it.queryParameter("p")?.toIntOrNull() ?: 0 }

    fun hold(sheet: Int): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gates[sheet] = it }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
        requests += url
        val sheet = url.queryParameter("p")?.toIntOrNull() ?: 0
        gates[sheet]?.await()
        if (failures > 0) {
            failures--
            return XResponse(500, "busy")
        }
        if (!url.encodedPath.startsWith("/g/")) return XResponse(200, "<table></table>")
        return XResponse(200, galleryHtml(sheet, sheetCount, comments, thumbs))
    }

    fun client(settings: EhSettings = EhSettings()): EhClient = EhClient(this) { settings }
}

/**
 * A gallery of [total] pages on a fake e-hentai.org that serves [sheetSize]
 * tiles per sheet and records every request, as XTA's EhFakeSite.
 */
class EhFakeSite(val total: Int = 30, private val sheetSize: Int = 20, failing: Set<Int> = emptySet()) : XHttp {
    val failingPages: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>().apply { addAll(failing) }
    val requests: MutableList<HttpUrl> = CopyOnWriteArrayList()
    val gallery: EhGallery get() = EhGallery(gid = GID, token = "tok", title = "Sommerfest", pageCount = total)

    val sheetRequests: List<HttpUrl> get() = requests.filter { it.encodedPath.startsWith("/g/") }

    fun imageRequests(page: Int): List<HttpUrl> = requests.filter { it.encodedPath.endsWith("-$page") }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
        requests += url
        val image = imagePath.find(url.encodedPath)
        return when {
            image != null -> imagePage(image.groupValues[1].toInt(), url.queryParameter("nl"))
            url.encodedPath.startsWith("/g/") -> sheet(url.queryParameter("p")?.toIntOrNull() ?: 0)
            else -> XResponse(404, "")
        }
    }

    /** Like the site, a sheet past the end answers with the last one. */
    private fun sheet(index: Int): XResponse {
        val sheet = min(index, (total - 1) / sheetSize)
        val first = sheet * sheetSize + 1
        val tiles = (first..min(first + sheetSize - 1, total)).joinToString("") { """<a href="${linkTo(it)}"><div></div></a>""" }
        return XResponse(200, """<h1 id="gn">Sommerfest</h1><div id="gdt">$tiles</div>""")
    }

    private fun imagePage(page: Int, reloadKey: String?): XResponse {
        if (page in failingPages) return XResponse(500, "")
        val server = if (reloadKey == null) "a" else "b"
        val html = buildString {
            append("""<img id="img" src="https://$server.hath.example/h/$page/0$page.webp" />""")
            if (page > 1) append("""<a id="prev" href="${linkTo(page - 1)}"></a>""")
            if (page < total) append("""<a id="next" href="${linkTo(page + 1)}"></a>""")
            append("""<a href="#" onclick="return nl('nl-$page')">Reload broken image</a>""")
        }
        return XResponse(200, html)
    }

    companion object {
        const val GID = 9L
        private val imagePath = Regex("""^/s/\w+/\d+-(\d+)$""")

        fun tokenOf(page: Int): String = "pt$page"

        fun linkTo(page: Int): String = "https://e-hentai.org/s/${tokenOf(page)}/$GID-$page"

        fun preview(page: Int): EhPreview = EhPreview(pageToken = tokenOf(page), page = page)

        fun pages(first: Int, last: Int): List<EhPreview> = (first..last).map(::preview)
    }
}
