package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** One SauceNAO match that points at a Pixiv work. */
data class PixivSauceMatch(
    val illustId: Long,
    /** How alike SauceNAO judged the pictures, in percent. */
    val similarity: Double? = null,
    val title: String = "",
    val author: String = "",
)

/**
 * SauceNAO's reverse image search, read for the Pixiv works it names: the
 * picture goes to [SEARCH_URL] as a multipart form (a PNG in the field
 * `file`), to saucenao.com and nowhere else, with no Pixiv token, as XTA's
 * PixivSauceNaoApi sends it. [com.aimdi.xtanative.core.x.http.XHttp] sends
 * no binary body, so the upload goes through the app's OkHttp client itself.
 */
object PixivSauceNao {
    const val SEARCH_URL = "https://saucenao.com/search.php"

    /** How long the whole exchange may take, the upload included. */
    private const val TIMEOUT_SECONDS = 45L

    /**
     * [png] searched for: the Pixiv works SauceNAO finds, best first. A rate
     * limit comes back as Pixiv's 429 would, any other refusal as an answer
     * of no use; no answer at all as the IOException it was.
     */
    suspend fun search(png: ByteArray, client: OkHttpClient = OkHttpXHttp.defaultClient): List<PixivSauceMatch> {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "image.png", png.toRequestBody("image/png".toMediaType()))
            .build()
        val call = client.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build().newCall(Request.Builder().url(SEARCH_URL).post(form).build())
        return call.answer().use { response ->
            when {
                response.code == 429 -> throw NetworkRequestException(PIXIV_NETWORK, 429, "saucenao: 429")
                !response.isSuccessful -> throw unusableAnswer("saucenao: ${response.code}")
                else -> parse(withContext(Dispatchers.IO) { response.body?.string().orEmpty() })
            }
        }
    }

    /** The call's answer, the call itself cancelled when the coroutine waiting for it is. */
    private suspend fun Call.answer(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) = continuation.resume(response) { _, _, _ -> response.close() }
        })
    }

    /** The Pixiv work a link names, from `artworks/<id>` or `illust_id=<id>`; null for any other site. */
    fun idFromLink(link: String): Long? {
        val uri = PixivUri.parse(link.trim()) ?: return null
        if (uri.host != "pixiv.net" && !uri.host.endsWith(".pixiv.net")) return null
        val segments = uri.allSegments
        val at = segments.indexOf("artworks")
        val digits = if (at >= 0 && at + 1 < segments.size) segments[at + 1] else uri.query("illust_id")
        return digits?.toLongOrNull()?.takeIf { it > 0 }
    }

    /** SauceNAO's answer, the public page's HTML or its API's JSON, as the Pixiv works it found: best first, each once. */
    fun parse(body: String): List<PixivSauceMatch> {
        val text = body.trimStart()
        val matches = if (text.startsWith("{")) fromJson(J.parse(text)) else fromHtml(text)
        return matches.distinctBy { it.illustId }
    }

    private fun fromJson(json: J): List<PixivSauceMatch> = json["results"].list.mapNotNull { result ->
        val data = result["data"]
        jsonId(data)?.let { id ->
            PixivSauceMatch(
                illustId = id,
                similarity = result["header"]["similarity"].double,
                title = data["title"].string?.trim().orEmpty(),
                author = (data["member_name"].string ?: data["author_name"].string).orEmpty().trim(),
            )
        }
    }

    private fun jsonId(data: J): Long? =
        data["pixiv_id"].long?.takeIf { it > 0 } ?: data["ext_urls"].list.firstNotNullOfOrNull { idFromLink(it.string.orEmpty()) }

    private fun fromHtml(html: String): List<PixivSauceMatch> {
        val document = Jsoup.parse(html)
        val matches = document.below(".result").mapNotNull { block ->
            linkedId(block)?.let { id ->
                PixivSauceMatch(
                    illustId = id,
                    similarity = percent(block.firstBelow(".resultsimilarityinfo")),
                    title = PixivText.textOf(block.firstBelow(".resulttitle")).trim(),
                    author = author(block),
                )
            }
        }
        return matches.ifEmpty { document.below("a[href]").mapNotNull { idFromLink(it.attr("href")) }.map { PixivSauceMatch(it) } }
    }

    private fun linkedId(block: Element): Long? = block.below("a[href]").firstNotNullOfOrNull { idFromLink(it.attr("href")) }

    private fun author(block: Element): String = block.below("a[href]")
        .firstOrNull { "member.php" in it.attr("href") || "/users/" in it.attr("href") }
        ?.let { PixivText.textOf(it).trim() }
        .orEmpty()

    private fun percent(element: Element?): Double? = element?.let { PixivText.textOf(it).replace("%", "").trim().toDoubleOrNull() }
}
