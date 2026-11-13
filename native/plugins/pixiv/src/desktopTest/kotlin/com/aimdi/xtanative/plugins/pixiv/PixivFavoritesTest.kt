package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PIXIV_UNCLASSIFIED_TAG
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.networks.pixiv.illustsJson
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.PixivSection
import kotlin.test.Test
import kotlin.test.assertEquals

/** XTA's pixiv_bookmark_tag_picker_test: what Favorites reads for the visibility and tag picked, and how the chip names them. */
class PixivFavoritesTest {
    @Test
    fun theChipAndThePickerSayAllUncategorizedOrTheTag() {
        assertEquals("All", tagLabel(null, "en"))
        assertEquals("Uncategorized", tagLabel(PixivDeviceBookmarks.UNTAGGED, "en"))
        assertEquals("Favs", tagLabel("Favs", "en"))
        assertEquals(PIXIV_UNCLASSIFIED_TAG, PixivDeviceBookmarks.UNTAGGED, "Uncategorized filters by Pixiv's 未分類 tag")
    }

    private fun favorites(block: suspend (PixivHomeModel, () -> List<String>) -> Unit) {
        val http = FakePixivHttp { answer(illustsJson()) }
        val read = { http.reads.filter { it.url.encodedPath == "/v1/user/bookmarks/illust" }.map { "${it.query["restrict"]}:${it.query["tag"] ?: "-"}" } }
        withRuntime(PixivTestHost(signedInSettings().with(PixivPrefs.startSection, PixivSection.FAVORITES), http)) { runtime ->
            block(PixivHomeModel(runtime, runtime.scope), read)
        }
    }

    @Test
    fun theFavoritesListLoadsTheVisibilityAndTagPicked() = favorites { home, read ->
        home.ensureShown()
        eventually { read().isNotEmpty() }
        assertEquals("public:-", read().last())

        home.favorites(PixivFavoritesSource.PRIVATE, "Secret", picked = true)
        eventually { read().size > 1 }
        assertEquals("private:Secret", read().last(), "a tag picked on the Private tab is kept with it")
        assertEquals(PixivFavoritesSource.PRIVATE to "Secret", home.view.value.favorites to home.view.value.tag)
    }

    @Test
    fun aChosenTagClearsBackToAllAndAnotherVisibilityStartsOnAll() = favorites { home, read ->
        home.favorites(PixivFavoritesSource.PUBLIC, "Favs", picked = true)
        eventually { read().lastOrNull() == "public:Favs" }

        home.favorites(PixivFavoritesSource.PUBLIC, null)
        eventually { read().lastOrNull() == "public:-" }
        home.favorites(PixivFavoritesSource.PUBLIC, "Favs", picked = true)
        home.favorites(PixivFavoritesSource.PRIVATE, home.view.value.tag)
        eventually { read().lastOrNull() == "private:-" }
        assertEquals(null, home.view.value.tag)
    }
}
