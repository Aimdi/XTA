package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J

/**
 * A post's words in the reader's language, as Meta translates them for a
 * signed-in reader of Threads (threads.com's Translate, which Meta offers
 * where a caption says `can_translate`). Asked with the reader's session by
 * the post's media id ([ThreadsPeople.mediaId]) or its caption's id. No
 * probe has seen the path the Threads app asks at: [paths] names Instagram's
 * documented one (`language/translate/`, a caption being type 1) and two
 * likely ones, tried in turn (a 404 moves on), and CI's live probe
 * (ThreadsPostContentLiveTest) logs how each answers a guest: a session path
 * sends a guest to sign in, a wrong one is a 404. A read only: asking for a
 * translation changes nothing on Threads.
 */
object ThreadsTranslation {
    /** The paths a translation of the post Meta keeps under [mediaId] may be read at, in the order they are tried. */
    fun paths(mediaId: String): List<String> =
        listOf("/api/v1/language/translate/", "/api/v1/text_feed/$mediaId/translate/", "/api/v1/media/$mediaId/translate/")

    /** The query: the caption's id where Meta gave one, else the post's, as a caption (type 1). */
    fun params(mediaId: String, captionId: String?): List<Pair<String, String>> =
        listOf("id" to (captionId?.takeIf { it.isNotBlank() } ?: mediaId), "type" to "1")

    /**
     * The language Meta is asked to translate into: [target] first (a BCP 47
     * tag such as `de-DE`), English after it as Meta's own fallback.
     */
    fun acceptLanguage(target: String): String {
        val tag = target.trim().replace('_', '-').ifEmpty { "en" }
        val base = tag.substringBefore('-')
        val wanted = listOfNotNull(tag, base.takeIf { it != tag }, "en".takeIf { base != "en" })
        return wanted.zip(WEIGHTS) { language, weight -> language + weight }.joinToString(",")
    }

    /**
     * The translated words in Meta's answer: `translation` (Instagram's
     * shape), or under the names an answer is likely to use (`translated_text`,
     * `caption_translation`, a list of `translations` or
     * `comment_translations`, a `data` wrapper); null when it holds none.
     */
    fun text(body: J): String? {
        val roots = listOf(body, body["data"], body["result"])
        val direct = roots.flatMap { root -> TEXT_KEYS.map { root[it] } }
        val listed = roots.flatMap { root -> LIST_KEYS.map { root[it][0] } }
        return (direct + listed).firstNotNullOfOrNull(::said)
    }

    private val WEIGHTS = listOf("", ";q=0.9", ";q=0.8")
    private val TEXT_KEYS =listOf("translation", "translated_text", "caption_translation", "translated_caption")
    private val LIST_KEYS = listOf("translations", "comment_translations", "caption_translations")

    /** Words given as a string, or as an object's `translation` or `text`. */
    private fun said(node: J): String? =
        (node.string ?: node["translation"].string ?: node["translated_text"].string ?: node["text"].string)?.trim()?.ifEmpty { null }
}
