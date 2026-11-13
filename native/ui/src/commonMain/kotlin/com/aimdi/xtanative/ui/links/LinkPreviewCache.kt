package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.services.links.LinkPreview
import com.aimdi.xtanative.services.links.LinkPreviewReader
import kotlinx.coroutines.CancellationException

/**
 * The previews pages gave of themselves, read once per address while the
 * app runs: a card scrolled past and back does not ask again, nor does one
 * whose page gave none. [read] answers what a page says, or null.
 */
class LinkPreviewCache(private val read: suspend (String) -> LinkPreview? = LinkPreviewReader(OkHttpXHttp())::read) {
    private class Answer(val preview: LinkPreview?)

    private val answers = object : LinkedHashMap<String, Answer>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Answer>?) = size > KEPT
    }

    /** What is known of [url] already: the preview, or null; [known] tells an empty answer from none yet. */
    @Synchronized
    fun cached(url: String): LinkPreview? = answers[url]?.preview

    @Synchronized
    fun known(url: String): Boolean = url in answers

    /** [url]'s preview, from its page the first time; a page that fails to answer is asked again next time. */
    suspend fun load(url: String): LinkPreview? {
        synchronized(this) { answers[url]?.let { return it.preview } }
        val preview = try {
            read(url)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        synchronized(this) { answers[url] = Answer(preview) }
        return preview
    }

    companion object {
        private const val KEPT = 200

        /** The app's previews. */
        val shared: LinkPreviewCache by lazy { LinkPreviewCache() }
    }
}
