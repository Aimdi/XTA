package com.aimdi.xtanative.core.x.signing

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * X's pages had no signing data, or the signer could not be found or read.
 * Not an IOException: it is no network blip, and is not retried at once.
 */
class SigningUnavailableException(message: String) : IllegalStateException(message)

/**
 * Derives a [TransactionSigner] from what x.com serves, as XTA's
 * ClientTransaction.initialize does (docs/fix-x-signing.md): a page with the
 * key and the loading animations, then the signing script it names.
 */
class SigningBootstrap(private val http: XHttp) {
    suspend fun derive(cookie: String?): TransactionSigner {
        val assets = SigningAssets(http)
        val (page, bundle) = bootstrapPage(assets, cookie)
        return TransactionSigner.derive(page, assets.read(bundle))
    }

    private suspend fun bootstrapPage(assets: SigningAssets, cookie: String?): Pair<Document, HttpUrl> {
        // The logged-out home page can omit the signer, and since October 2026
        // X answers some addresses' /home and /search with a browser check the
        // sign-in page is spared. That page is for visitors: no session.
        val pages = listOf(
            HOME to cookie,
            SEARCH to cookie,
            LOGIN to null,
        )
        // What each page lacked, so a failure says which part of x.com changed.
        val lacked = ArrayList<String>()
        for ((url, pageCookie) in pages) {
            val response = fetchPage(url, pageCookie)
            if (url != LOGIN && response.code in setOf(403, 404)) {
                lacked += "${url.encodedPath}: HTTP ${response.code}"
                continue
            }
            if (!response.isSuccessful) throw SigningUnavailableException("X transaction bootstrap returned HTTP ${response.code}")
            val document = Jsoup.parse(response.body, url.toString())
            val missing = TransactionSigner.missingBootstrapData(document)
            if (missing != null) {
                lacked += "${url.encodedPath}: $missing (${response.body.length} characters, \"${document.title().take(60)}\")"
                continue
            }
            val bundle = ondemandUrlOf(response.body, document) ?: assets.discover(document)
            if (bundle != null) return document to bundle
            lacked += "${url.encodedPath}: no signing script"
        }
        throw SigningUnavailableException("X pages did not contain transaction signing data: ${lacked.joinToString("; ")}")
    }

    private suspend fun fetchPage(start: HttpUrl, cookie: String?): XResponse {
        var url = start
        repeat(MAX_REDIRECTS + 1) { redirects ->
            val headers = if (cookie.isNullOrEmpty()) BOOTSTRAP_HEADERS else BOOTSTRAP_HEADERS + ("Cookie" to cookie)
            val response = http.get(url, headers, followRedirects = false)
            if (response.code !in REDIRECTS) return response
            val next = response.header("location")?.let { url.resolve(it) }
            if (redirects >= MAX_REDIRECTS || next == null || !isX(next)) {
                throw SigningUnavailableException("X transaction bootstrap redirect was refused")
            }
            url = next
        }
        throw SigningUnavailableException("X transaction bootstrap redirect was refused")
    }

    companion object {
        private val HOME = "https://x.com/home".toHttpUrlOrNull()!!
        private val SEARCH = "https://x.com/search?q=AI&f=live".toHttpUrlOrNull()!!
        private val LOGIN = "https://x.com/i/flow/login".toHttpUrlOrNull()!!
        private const val MAX_REDIRECTS = 3
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private const val ONDEMAND_TEMPLATE = "https://abs.twimg.com/responsive-web/client-web/ondemand.s.%sa.js"

        private val BOOTSTRAP_HEADERS = mapOf(
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "no-cache",
            "Referer" to "https://x.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
            "X-Twitter-Active-User" to "yes",
            "X-Twitter-Client-Language" to "en",
        )

        private val ondemandIndex = Regex("""(?:^|[,{])\s*["']?(\d+)["']?\s*:\s*["']ondemand\.s["']""", RegexOption.MULTILINE)
        private val signingBundlePath = Regex("""^/responsive-web/client-web/ondemand\.s\.[a-zA-Z0-9_-]+\.js$""")

        private fun isX(url: HttpUrl) =
            url.scheme == "https" && url.host == "x.com" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()

        /** The signing bundle the page names, by script tag or through the runtime's chunk map. */
        internal fun ondemandUrlOf(html: String, page: Document): HttpUrl? {
            for (script in page.select("script[src]")) {
                val url = script.attr("src").toHttpUrlOrNull()
                if (url != null && isSigningBundle(url)) return url
            }
            val index = ondemandIndex.find(html)?.groupValues?.get(1) ?: return null
            val hash = Regex("""(?:^|[,{])\s*["']?${Regex.escape(index)}["']?\s*:\s*["']([a-zA-Z0-9_-]+)["']""")
                .find(html)?.groupValues?.get(1) ?: return null
            val url = ONDEMAND_TEMPLATE.format(hash).toHttpUrlOrNull() ?: return null
            return url.takeIf { isSigningBundle(it) }
        }

        private fun isSigningBundle(url: HttpUrl) =
            url.scheme == "https" && url.host == "abs.twimg.com" && url.port == 443 && url.username.isEmpty() &&
                url.query == null && url.fragment == null && signingBundlePath.matches(url.encodedPath)
    }
}

/**
 * Finds the current signing script without evaluating any JavaScript, as XTA's
 * SigningAssets does: the scripts a page links, then their module imports,
 * importers first, at most [MAX_ASSETS] downloads, [CONCURRENCY] at a time.
 */
internal class SigningAssets(private val http: XHttp) {
    private val visited = HashSet<HttpUrl>()
    private var requests = 0

    suspend fun read(url: HttpUrl): String {
        if (!trusted(url) || requests >= MAX_ASSETS) throw SigningUnavailableException("X transaction signing asset limit exceeded")
        requests++
        val response = http.get(url, emptyMap(), followRedirects = false)
        if (!response.isSuccessful) throw SigningUnavailableException("X signing bundle returned HTTP ${response.code}")
        return response.body
    }

    suspend fun discover(page: Document): HttpUrl? {
        val linked = LinkedHashSet<HttpUrl>()
        for (element in page.select("script[src]") + page.select("link[rel=modulepreload][href]")) {
            val url = (element.attr("src").ifEmpty { element.attr("href") }).toHttpUrlOrNull()
            if (url != null && trusted(url)) linked.add(url)
        }
        for (script in page.select("script:not([src])")) linked.addAll(references(PAGE, script.data(), literal))
        linked.firstOrNull { signer(it) }?.let { return it }
        return search(linked)
    }

    private suspend fun search(roots: Collection<HttpUrl>): HttpUrl? = coroutineScope {
        val queue = ArrayDeque<Pair<HttpUrl, Int>>()
        fun enqueue(urls: Collection<HttpUrl>, depth: Int) {
            if (depth > MAX_IMPORT_DEPTH) return
            val fresh = urls.filter { it !in visited }.distinct()
            val next = (fresh.filter(::importer) + fresh.filterNot(::importer)).take(MAX_ASSETS).filter { visited.add(it) }
            next.asReversed().forEach { queue.addFirst(it to depth) }
        }
        enqueue(roots, 0)
        var loaded = 0
        var firstError: Throwable? = null
        while (queue.isNotEmpty() && requests < MAX_ASSETS - 1) {
            // While an importer loads, the bundles around it wait: it usually
            // names the signer, and they are the bulk of what the search reads.
            val importers = importer(queue.first().first)
            val wave = ArrayList<Pair<HttpUrl, Int>>()
            while (wave.size < CONCURRENCY && queue.isNotEmpty() && requests + wave.size < MAX_ASSETS - 1 &&
                importer(queue.first().first) == importers
            ) {
                wave.add(queue.removeFirst())
            }
            val sources = wave.map { (url, _) -> async { runCatching { read(url) } } }.awaitAll()
            for ((index, result) in sources.withIndex()) {
                val (url, depth) = wave[index]
                val source = result.getOrNull()
                if (source == null) {
                    firstError = firstError ?: result.exceptionOrNull()
                    continue
                }
                loaded++
                references(url, source, literal).firstOrNull { signer(it) }?.let { return@coroutineScope it }
                enqueue(references(url, source, moduleImport).toList(), depth + 1)
            }
        }
        if (loaded == 0) firstError?.let { throw it }
        null
    }

    companion object {
        const val MAX_ASSETS = 16
        const val CONCURRENCY = 4
        const val MAX_IMPORT_DEPTH = 3
        private val PAGE = "https://x.com/".toHttpUrlOrNull()!!

        private val trustedPath = Regex("""^/(?:x-web|responsive-web/client-web)/[a-zA-Z0-9_./~-]+\.js$""")
        private val signerName = Regex("""^(?:sign\.o|ondemand\.s)[.-][a-zA-Z0-9_-]+\.js$""")
        private val importerName = Regex("""^(?:startup-dynamic|client-transaction-id-plugin)[.-]""")

        // Template literals count only when constant: ${...} is never evaluated.
        private val literal = Regex("""(["'`])([^"'`\s$]+\.js)\1""")
        private val moduleImport = Regex("""(?:\bfrom\s*|\bimport\s*\(?\s*)(["'`])([^"'`\s$]+\.js)\1""")

        fun trusted(url: HttpUrl) =
            url.scheme == "https" && url.host == "abs.twimg.com" && url.port == 443 && url.username.isEmpty() &&
                url.query == null && url.fragment == null && url.pathSegments.none { it == "." || it == ".." } &&
                trustedPath.matches(url.encodedPath)

        fun signer(url: HttpUrl) = trusted(url) && signerName.matches(url.pathSegments.last())

        fun importer(url: HttpUrl) = importerName.containsMatchIn(url.pathSegments.last())

        fun references(parent: HttpUrl, source: String, pattern: Regex): Sequence<HttpUrl> =
            pattern.findAll(source).mapNotNull { parent.resolve(it.groupValues[2]) }.filter(::trusted)
    }
}
