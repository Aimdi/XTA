package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.networks.booru.BooruEngine.DANBOORU
import com.aimdi.xtanative.networks.booru.BooruEngine.E621
import com.aimdi.xtanative.networks.booru.BooruEngine.GELBOORU_V2
import com.aimdi.xtanative.networks.booru.BooruEngine.MOEBOORU
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Links to posts on known boorus, as XTA's booru_links.dart recognises them. */
class BooruLinksTest {
    private fun ref(url: String, sites: List<BooruPreset> = BooruPresets.ALL) = BooruLinks.postRef(url, sites)

    @Test
    fun eachEngineOpensItsOwnPostPageForm() {
        assertEquals(BooruPostRef("https://danbooru.donmai.us", DANBOORU, "7"), ref("https://danbooru.donmai.us/posts/7"))
        assertEquals(BooruPostRef("https://e621.net", E621, "12"), ref("https://e621.net/posts/12?q=wolf"))
        assertEquals(BooruPostRef("https://yande.re", MOEBOORU, "3"), ref("https://yande.re/post/show/3"))
        assertEquals(BooruPostRef("https://konachan.com", MOEBOORU, "4"), ref("https://konachan.com/post/show/4/long_hair-original"))
        assertEquals(
            BooruPostRef("https://safebooru.org", GELBOORU_V2, "5"),
            ref("https://safebooru.org/index.php?page=post&s=view&id=5&tags=solo"),
        )
    }

    @Test
    fun aPostPageIsTheOneThePostItselfLinksTo() {
        BooruPresets.ALL.forEach { preset ->
            val page = post("42", engine = preset.engine, host = preset.host).hostPageUrl!!
            assertEquals(BooruPostRef(preset.host, preset.engine, "42"), ref(page), preset.name)
        }
    }

    @Test
    fun wwwHttpAndLetterCaseDoNotMatter() {
        assertEquals("7", ref("http://www.danbooru.donmai.us/posts/7")?.id)
        assertEquals("https://www.sakugabooru.com", ref("https://sakugabooru.com/post/show/8")?.host)
        assertEquals("https://rule34.xxx", ref("https://Rule34.xxx/index.php?page=post&s=view&id=9")?.host)
    }

    @Test
    fun otherPagesAndHostsAreLeftAlone() {
        listOf(
            "https://danbooru.donmai.us/posts",
            "https://danbooru.donmai.us/posts?tags=solo",
            "https://danbooru.donmai.us/posts/7.json",
            "https://danbooru.donmai.us/posts/7/edit",
            "https://danbooru.donmai.us/wiki_pages/solo",
            "https://yande.re/post/show",
            "https://yande.re/posts/3",
            "https://safebooru.org/index.php?page=post&s=list&tags=solo",
            "https://safebooru.org/index.php?page=post&s=view",
            "https://example.com/posts/7",
            "https://notdanbooru.donmai.us/posts/7",
            "not a link",
        ).forEach { assertNull(ref(it), it) }
    }

    @Test
    fun aSiteTheReaderAddedIsKnownByItsOwnPath() {
        val custom = BooruPreset("custom:example.com", "Example", DANBOORU, "https://example.com/booru")
        assertEquals("https://example.com/booru", ref("https://example.com/booru/posts/7", listOf(custom))?.host)
        assertNull(ref("https://example.com/posts/7", listOf(custom)))
    }
}
