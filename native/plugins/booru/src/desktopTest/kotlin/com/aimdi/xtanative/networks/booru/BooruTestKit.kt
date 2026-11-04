package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl

/**
 * The booru answers under test/resources/booru. XTA keeps no recorded booru answers and no board
 * was reachable to record them, so they are built from the shapes XTA's own tests read
 * (test/booru_*_test.dart), as those tests' answers were.
 */
internal object Fixtures {
    fun text(name: String): String = Fixtures::class.java.getResource("/booru/$name")!!.readText()

    fun json(name: String): J = J.parse(text(name))
}

/** A booru that answers from [answer], and keeps what it was asked, from whichever thread. */
internal class FakeBooru(private val answer: (HttpUrl) -> XResponse) : XHttp {
    val asked = CopyOnWriteArrayList<HttpUrl>()
    val headers = CopyOnWriteArrayList<Map<String, String>>()

    val paths: List<String> get() = asked.map { it.encodedPath }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
        asked += url
        this.headers += headers
        return answer(url)
    }
}

/** A clock that moves only when a gate waits, so every wait is recorded and none takes real time. */
internal class FakeClock {
    private val millis = AtomicLong(1_760_000_000_000)
    val waits = CopyOnWriteArrayList<Duration>()

    fun now(): Long = millis.get()

    suspend fun advance(duration: Duration) {
        waits += duration
        millis.addAndGet(duration.inWholeMilliseconds)
    }

    fun gate(perSecond: Int = 10, maxPause: Duration = 32.seconds): BooruRequestGate =
        BooruRequestGate(perSecond, maxPause, ::now, ::advance)
}

internal fun ok(body: String): XResponse = XResponse(200, body)

/** A Danbooru-shaped post row, as `/posts.json` lists one. */
internal fun danbooruRow(id: Int, tags: String, rating: String = "g"): String =
    """{"id":$id,"tag_string":"$tags","rating":"$rating","file_url":"https://cdn.example/$id.png"}"""

internal fun post(
    id: String = "1",
    engine: BooruEngine = BooruEngine.DANBOORU,
    host: String = "https://example.com",
    tags: List<String> = listOf("solo", "smile"),
    rating: BooruRating? = BooruRating.GENERAL,
    categories: Map<String, BooruTagCategory> = emptyMap(),
    score: Int = 0,
    width: Int = 1,
    height: Int = 1,
    previewUrl: String? = null,
    sampleUrl: String? = null,
    fileUrl: String? = null,
    fileExt: String? = "jpg",
    favCount: Int? = null,
    uploader: String? = null,
    md5: String? = null,
    parentId: String? = null,
    source: String? = null,
): BooruPost = BooruPost(
    id = id,
    host = host,
    engine = engine,
    tags = tags,
    rating = rating,
    score = score,
    width = width,
    height = height,
    previewUrl = previewUrl,
    sampleUrl = sampleUrl,
    fileUrl = fileUrl,
    fileExt = fileExt,
    source = source,
    createdAt = null,
    tagCategories = categories,
    favCount = favCount,
    uploader = uploader,
    md5 = md5,
    parentId = parentId,
)
