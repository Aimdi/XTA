package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.ehviewer.EhErrorKind
import com.aimdi.xtanative.networks.ehviewer.EhException
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import java.io.IOException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EhRoutesTest {
    private val gallery = EhGallery(2345, "abc0", "A gallery")

    @Test
    fun eachScreenOpensFromItsRoute() {
        assertEquals(EhRoute.Gallery(2345, "abc0", "k1"), EhRoutes.parse(EhRoutes.gallery(gallery, "k1")))
        assertEquals(EhRoute.Reader(2345, "abc0", 7, null), EhRoutes.parse(EhRoutes.reader(gallery, 7, null)))
        assertEquals(EhRoute.Search("female:\"glasses$\""), EhRoutes.parse(EhRoutes.search(" female:\"glasses$\" ")))
        assertEquals(EhRoute.Search(""), EhRoutes.parse(EhRoutes.search(null)))
    }

    @Test
    fun aRouteWithoutItsGalleryOpensNothing() {
        assertNull(EhRoutes.parse("gallery?gid=0&token=abc"))
        assertNull(EhRoutes.parse("reader?gid=12"))
        assertNull(EhRoutes.parse("toplist"))
        assertEquals(EhRoute.Reader(1, "t", 1, null), EhRoutes.parse("reader?gid=1&token=t&page=-3"))
    }

    @Test
    fun failuresReadAsXtaTellsThem() {
        assertEquals(Strings.error_ban, EhText.error(EhException(EhErrorKind.BANNED, "banned")))
        assertEquals(Strings.error_unauthorized, EhText.error(NetworkRequestException("ehviewer", 403, "403")))
        assertEquals(Strings.error_rate_limited, EhText.error(NetworkRequestException("ehviewer", 429, "429")))
        assertEquals(Strings.error_network, EhText.error(IOException("offline")))
        assertEquals(Strings.error_response, EhText.error(EhException(EhErrorKind.CONTENT_WARNING, "warned")))
    }

    @Test
    fun sizesBadgesAndStarsAreWrittenAsXtaWritesThem() {
        assertEquals("512 B", EhFormats.size(512))
        assertEquals("1.0 kB", EhFormats.size(1024))
        assertEquals("96.6 MB", EhFormats.size(101_260_000))
        assertEquals("EN", EhFormats.languageCode("English"))
        assertEquals("HU", EhFormats.languageCode("hungarian"))
        assertEquals(listOf(EhStar.FULL, EhStar.FULL, EhStar.FULL, EhStar.FULL, EhStar.HALF), EhFormats.stars(4.5))
        assertEquals("4,62", EhFormats.rating(4.62, Locale.GERMAN))
    }

    @Test
    fun gridsGainColumnsWithWidthAndKeepThemReadableAtLargeText() {
        assertEquals(2, EhLayout.galleryColumns(360f, 1f))
        assertEquals(4, EhLayout.galleryColumns(1200f, 1f))
        assertEquals(1, EhLayout.galleryColumns(360f, 1.5f))
        assertEquals(3, EhLayout.previewColumns(300f))
        assertEquals(7, EhLayout.previewColumns(800f))
    }
}
