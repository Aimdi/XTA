package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPostJson
import com.aimdi.xtanative.store.offline.OfflineArticle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A Substack post as the app's offline library keeps it (XTA's OfflineArticle.substack). */
internal object SubstackOffline {
    fun idOf(post: SubstackPost): String = "substack:${post.publication.id}:${post.id}"

    fun articleOf(post: SubstackPost): OfflineArticle = OfflineArticle(
        id = idOf(post),
        title = post.title,
        source = post.publicationName,
        bodyHtml = post.bodyHtml.orEmpty(),
        payload = JsonObject(mapOf("kind" to JsonPrimitive("substack")) + SubstackPostJson.encode(post)),
        url = post.canonicalUrl ?: "${post.publicationBaseUrl}/p/${post.slug}",
    )

    fun postOf(article: OfflineArticle): SubstackPost? =
        if (article.kind != "substack") null else SubstackPostJson.decode(J(article.payload)).copy(bodyHtml = article.bodyHtml, publicationName = article.source)
}

/** Where the app's reading journal keeps a post's place. */
internal object ReaderIds {
    /** The journal's id of a post, as XTA's substackArticleReadingId. */
    fun readingIdOf(post: SubstackPost): String = "substack:${post.publication.id}:${post.slug.ifEmpty { post.id }}"
}
