package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Every published web source read live, as XTA's
 * test/live/web_sources_smoke_test.dart reads them: the first page of every
 * list, the search when a query is named for the source, and the full text
 * of the first post when the source says where it is. A list that brings no
 * posts fails it: the site changed, and the source's file needs a new rule.
 *
 * The sources are this checkout's `sources.json` and `sources/` when the test
 * runs inside XTA's repository, else the published list. Skipped unless
 * RUN_LIVE=true.
 */
class WebSourcesLiveTest {
    private val queries = mapOf("devto" to "flutter", "lemmy-world" to "linux", "nasa-images" to "mars")

    @Test
    fun everyPublishedWebSourceBringsPosts() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read the live sites")
        val client = WebSourceClient(OkHttpXHttp())

        val problems = sources(client).flatMap { (id, file) ->
            when (file) {
                is WebSourceFile.Read -> check(client, file.source)
                is WebSourceFile.Refused -> listOf("$id: refused, ${file.refusal} ${file.detail}")
            }
        }

        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private suspend fun sources(client: WebSourceClient): List<Pair<String, WebSourceFile>> {
        val checkout = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .take(4).map { File(it, "sources.json") }.firstOrNull { it.isFile && File(it.parentFile, "sources").isDirectory }
            ?: return client.catalogue().map { it.id to client.sourceFile(it.url) }
        return WebSourceCatalogue.parse(checkout.readText(), WebSourceCatalogue.DEFAULT_URL).map { listing ->
            listing.id to WebSources.read(File(checkout.parentFile, listing.url.encodedPath.substringAfter("/HEAD/")).readText())
        }
    }

    private suspend fun check(client: WebSourceClient, source: WebSourceDefinition): List<String> {
        val lists = source.feeds + listOfNotNull(source.search?.takeIf { source.id in queries })
        return lists.flatMap { list ->
            try {
                val page = client.list(source, list, query = queries[source.id])
                val first = page.items.firstOrNull()
                println(
                    "LIVE ${source.id}/${list.id}: ${page.items.size} posts, next ${page.next != null}; first: ${first?.title ?: first?.text} | " +
                        "${first?.link} | ${first?.date} | ${first?.author} | score ${first?.score} | comments ${first?.comments} | pictures ${first?.pictures?.size}",
                )
                listOfNotNull("${source.id}/${list.id}: no posts".takeIf { first == null }) +
                    if (first != null && list == source.feeds.first()) article(client, source, first) else emptyList()
            } catch (failure: IOException) {
                listOf("${source.id}/${list.id}: unreachable, $failure")
            } catch (failure: NetworkRequestException) {
                listOf("${source.id}/${list.id}: answered ${failure.code}")
            } catch (failure: WebSourceLimitException) {
                listOf("${source.id}/${list.id}: $failure")
            }
        }
    }

    private suspend fun article(client: WebSourceClient, source: WebSourceDefinition, item: WebSourceItem): List<String> {
        val article = source.article ?: return emptyList()
        val url = WebSourcePages.articleUrl(article, item) ?: return listOf("${source.id}: no article address for ${item.id}")
        val html = WebSourcePages.articleHtml(article, client.page(source, url, article.type))
        println("LIVE ${source.id} article: ${html?.length ?: 0} characters")
        return if (html.isNullOrEmpty()) listOf("${source.id}: the article rule found no text") else emptyList()
    }
}
