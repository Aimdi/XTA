package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.post
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BooruFormatsTest {
    @Test
    fun aLabelNamesWhoAndWhatIsDrawnFirstAndNoMetaTags() {
        val drawn = post(
            tags = listOf("solo", "highres", "hatsune_miku", "vocaloid", "kei_(keigarou)", "smile"),
            categories = mapOf(
                "highres" to BooruTagCategory.META,
                "hatsune_miku" to BooruTagCategory.CHARACTER,
                "vocaloid" to BooruTagCategory.COPYRIGHT,
                "kei_(keigarou)" to BooruTagCategory.ARTIST,
            ),
        )
        assertEquals(listOf("kei_(keigarou)", "hatsune_miku", "vocaloid", "solo"), BooruFormats.labelTags(drawn, count = 4))
        assertEquals("hatsune miku", BooruFormats.tagName("hatsune_miku"))
    }

    @Test
    fun countsBesideATagAreShortToThreeFigures() {
        assertEquals("50", BooruFormats.compactCount(50, Locale.US))
        assertEquals("36.3K", BooruFormats.compactCount(36_312, Locale.US))
        assertEquals("36,3K", BooruFormats.compactCount(36_312, Locale.GERMANY))
        assertEquals("5.45M", BooruFormats.compactCount(5_452_000, Locale.US))
        assertEquals("1M", BooruFormats.compactCount(999_999, Locale.US))
        assertEquals("12.3B", BooruFormats.compactCount(12_345_678_901, Locale.US))
        assertEquals("36,312", BooruFormats.suggestionCount(36_312, Locale.US))
        assertEquals("5.45M", BooruFormats.suggestionCount(5_452_000, Locale.US))
    }

    @Test
    fun aFilesTypeAndSizeReadAsOneFact() {
        assertEquals("500 B", BooruFormats.size(500))
        assertEquals("1.5 kB", BooruFormats.size(1536))
        assertEquals("PNG", BooruFormats.file(post(fileExt = "png")))
        assertNull(BooruFormats.file(post(fileExt = null)))
        assertEquals("pixiv.net", BooruFormats.siteName("https://www.pixiv.net/users/1"))
    }
}
