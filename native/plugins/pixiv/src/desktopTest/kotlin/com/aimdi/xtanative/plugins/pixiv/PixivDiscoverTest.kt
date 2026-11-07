package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.store.settings.PixivPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** XTA's pixiv_discover_filter_test: a group's Discover shows a creator through a work the reader's choices let through. */
class PixivDiscoverTest {
    private fun work(id: Int, restrict: Int = 0, ai: Int = 1) =
        """{"id": $id, "title": "Work $id", "type": "illust", "image_urls": {"medium": "https://i.pximg.net/$id.jpg"}, "user": {"id": 50}, "x_restrict": $restrict, "illust_ai_type": $ai}"""

    private val related = """{"user_previews": [{"user": {"id": 50, "name": "Painter", "account": "painter"}, "illusts": [${work(1, restrict = 1)}, ${work(2, ai = 2)}, ${work(3)}]}]}"""

    private fun shownWork(showR18: Boolean, hideAi: Boolean, userId: Long = 7, check: (PixivDiscover) -> Unit = {}): String? {
        val http = FakePixivHttp { request ->
            assertEquals("/v1/user/related", request.url.encodedPath)
            answer(related)
        }
        val settings = signedInSettings(userId).with(PixivPrefs.showR18, showR18).with(PixivPrefs.hideAi, hideAi)
        return withRuntime(PixivTestHost(settings, http)) { runtime ->
            val discover = PixivDiscover(runtime)
            check(discover)
            discover.around("101").single().postUrl
        }
    }

    @Test
    fun hiddenR18AndAiPreviewsNeverBecomeTheSuggestionsWork() {
        assertEquals("https://www.pixiv.net/artworks/3", shownWork(showR18 = false, hideAi = true))
    }

    @Test
    fun theReadersOwnChoicesLetThemBackIn() {
        assertEquals("https://www.pixiv.net/artworks/1", shownWork(showR18 = true, hideAi = false))
        assertEquals("https://www.pixiv.net/artworks/2", shownWork(showR18 = false, hideAi = false))
    }

    @Test
    fun anotherAccountOrAnotherChoiceIsAnotherScope() {
        var first = ""
        var other = ""
        var choice = ""
        shownWork(showR18 = true, hideAi = false, userId = 7) { first = it.scope }
        shownWork(showR18 = true, hideAi = false, userId = 8) { other = it.scope }
        shownWork(showR18 = false, hideAi = true, userId = 7) { choice = it.scope }
        assertNotEquals(first, other, "another account asks again rather than reading the last one's answers")
        assertNotEquals(first, choice, "a choice changed since the last scan applies to the next one")
    }
}
