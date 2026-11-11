package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which size of a picture is shown where: a tile the smallest that fills it, the viewer and a download the largest. */
class ThreadsImagesTest {
    private fun image(name: String, width: Int? = null, height: Int? = null) = ThreadsImage("https://scontent.cdninstagram.com/$name", width, height)

    private val sizes = ThreadsImages.ordered(
        listOf(image("150.jpg", 150, 188), image("1440.jpg", 1440, 1800), image("640.jpg", 640, 800), image("1080.jpg", 1080, 1350), image("320.jpg", 320, 400)),
    )

    @Test
    fun theLargestComesFirstWhateverOrderMetaSentThemIn() {
        assertEquals(listOf(1440, 1080, 640, 320, 150), sizes.map { it.width })
    }

    @Test
    fun aTileLoadsTheSmallestSizeThatStillFillsIt() {
        assertEquals(640, ThreadsImages.fitting(sizes, 600)?.width)
        assertEquals(640, ThreadsImages.fitting(sizes, 640)?.width, "one exactly as wide is enough")
        assertEquals(1080, ThreadsImages.fitting(sizes, 641)?.width)
        assertEquals(150, ThreadsImages.fitting(sizes, 96)?.width)
    }

    @Test
    fun aCroppedTileIsFilledOnBothSides() {
        // A row's tile 300 wide and 700 tall: the 320 one is wide enough and too short.
        assertEquals(640, ThreadsImages.fitting(sizes, 300, 700)?.width)
        assertEquals(320, ThreadsImages.fitting(sizes, 300, 400)?.width)
    }

    @Test
    fun aTileLargerThanEverySizeAndABoxNotMeasuredYetTakeTheLargest() {
        assertEquals(1440, ThreadsImages.fitting(sizes, 2000)?.width)
        assertEquals(1440, ThreadsImages.fitting(sizes, 0)?.width)
        assertNull(ThreadsImages.fitting(emptyList(), 600))
    }

    @Test
    fun candidatesWithoutASizeKeepMetasOrderWhichListsTheLargestFirst() {
        // The rule before took the last of several it could not tell apart: Meta's smallest.
        val unsized = ThreadsImages.ordered(listOf(image("original.jpg"), image("medium.jpg"), image("thumb.jpg")))
        assertEquals("https://scontent.cdninstagram.com/original.jpg", unsized.first().url)
        assertEquals("https://scontent.cdninstagram.com/original.jpg", ThreadsImages.fitting(unsized, 300)?.url, "none is known to fill it")
    }

    @Test
    fun candidatesOfOneSizeKeepMetasOrderAndEachAddressIsKeptOnce() {
        val same = ThreadsImages.ordered(listOf(image("a.jpg", 1080, 1080), image("b.webp", 1080, 1080), image("a.jpg", 1080, 1080)))
        assertEquals(listOf("a.jpg", "b.webp"), same.map { it.url.substringAfterLast('/') })
    }

    @Test
    fun aSizeMetaDidNotSayIsReadFromTheAddress() {
        assertEquals(640 to 640, ThreadsImages.boxIn("https://scontent.cdninstagram.com/v/t51/1_n.jpg?stp=dst-jpg_e35_p640x640_sh0.08_tt6&_nc_ht=x"))
        assertEquals(150 to 150, ThreadsImages.boxIn("https://scontent.cdninstagram.com/v/t51/s150x150/1_n.jpg"))
        assertNull(ThreadsImages.boxIn("https://scontent.cdninstagram.com/v/t51/1_n.jpg?stp=dst-jpg_e35_tt6"))
        assertNull(ThreadsImages.boxIn("https://example.com/maps640x640.jpg"), "a size inside a word is not Meta's token")

        val mixed = ThreadsImages.ordered(
            listOf(image("1_n.jpg?stp=dst-jpg_e35_p320x320_tt6"), image("1_n.jpg?stp=dst-jpg_e35_p1080x1080_tt6"), image("1_n.jpg?stp=dst-jpg_e35_p640x640_tt6")),
        )
        assertEquals(listOf("p1080x1080", "p640x640", "p320x320"), mixed.map { Regex("p\\d+x\\d+").find(it.url)!!.value })
        assertEquals("p640x640", Regex("p\\d+x\\d+").find(ThreadsImages.fitting(mixed, 500)!!.url)!!.value)
    }

    @Test
    fun aSizedCandidateRanksBeforeOneOfNoKnownSize() {
        val mixed = ThreadsImages.ordered(listOf(image("unknown.jpg"), image("320.jpg", 320, 400)))
        assertEquals(listOf("320.jpg", "unknown.jpg"), mixed.map { it.url.substringAfterLast('/') })
    }

    @Test
    fun aPostsPictureKeepsEverySizeTheViewerTheLargestATileOneThatFits() {
        val post = ThreadsParser.post(
            j(
                postJson(
                    "1",
                    extra = mapOf(
                        "image_versions2" to mapOf(
                            "candidates" to listOf(
                                mapOf("url" to "https://scontent.cdninstagram.com/p-1440.jpg", "width" to 1440, "height" to 1800),
                                mapOf("url" to "https://scontent.cdninstagram.com/p-720.jpg", "width" to 720, "height" to 900),
                                mapOf("url" to "https://scontent.cdninstagram.com/p-240.jpg", "width" to 240, "height" to 300),
                            ),
                        ),
                    ),
                ),
            ),
        )!!
        val picture = post.media.single()
        assertEquals("https://scontent.cdninstagram.com/p-1440.jpg", picture.url, "the viewer and a download take the largest")
        assertEquals(0.8, picture.aspectRatio)
        assertEquals(listOf(1440, 720, 240), picture.images.map { it.width })
        assertEquals("https://scontent.cdninstagram.com/p-720.jpg", picture.tileUrl(660, 400))
        assertEquals("https://scontent.cdninstagram.com/p-1440.jpg", picture.tileUrl(1000))
        assertEquals("https://scontent.cdninstagram.com/p-240.jpg", picture.tileUrl(200, 250))
    }

    @Test
    fun aPictureKeptWithoutItsSizesLoadsItsOneAddress() {
        assertEquals("https://x/a.jpg", ThreadsMedia("https://x/a.jpg").tileUrl(400, 400))
    }

    @Test
    fun aVideosPosterIsPickedAsAPictureIs() {
        val post = ThreadsParser.post(
            j(
                postJson(
                    "2",
                    extra = mapOf(
                        "image_versions2" to mapOf(
                            "candidates" to listOf(
                                mapOf("url" to "https://scontent.cdninstagram.com/poster-360.jpg", "width" to 360, "height" to 640),
                                mapOf("url" to "https://scontent.cdninstagram.com/poster-1080.jpg", "width" to 1080, "height" to 1920),
                            ),
                        ),
                        "video_versions" to listOf(mapOf("url" to "https://scontent.cdninstagram.com/v.mp4", "width" to 720, "height" to 1280)),
                    ),
                ),
            ),
        )!!
        val video = post.media.single()
        assertEquals("https://scontent.cdninstagram.com/poster-1080.jpg", video.url)
        assertEquals("https://scontent.cdninstagram.com/poster-360.jpg", video.tileUrl(300))
    }

    @Test
    fun aProfileKeepsItsLargestPictureForItsPageAndTheSmallOneForRows() {
        val profile = ThreadsParser.profile(
            j(
                mapOf(
                    "pk" to "1",
                    "username" to "zuck",
                    "profile_pic_url" to "https://scontent.cdninstagram.com/zuck-150.jpg",
                    "hd_profile_pic_versions" to listOf(
                        mapOf("url" to "https://scontent.cdninstagram.com/zuck-320.jpg", "width" to 320, "height" to 320),
                        mapOf("url" to "https://scontent.cdninstagram.com/zuck-640.jpg", "width" to 640, "height" to 640),
                    ),
                    "hd_profile_pic_url_info" to mapOf("url" to "https://scontent.cdninstagram.com/zuck-1080.jpg", "width" to 1080, "height" to 1080),
                ),
            ),
        )!!
        assertEquals("https://scontent.cdninstagram.com/zuck-150.jpg", profile.profilePicUrl)
        assertEquals("https://scontent.cdninstagram.com/zuck-1080.jpg", profile.pictureUrl)

        val plain = ThreadsParser.profile(j(mapOf("username" to "maya", "profile_pic_url" to "https://scontent.cdninstagram.com/maya.jpg")))!!
        assertEquals("https://scontent.cdninstagram.com/maya.jpg", plain.pictureUrl, "the small one when Meta sent no other")

        val onlyHd = ThreadsParser.profile(j(mapOf("username" to "ola", "hd_profile_pic_url_info" to mapOf("url" to "https://scontent.cdninstagram.com/ola-hd.jpg"))))!!
        assertEquals("https://scontent.cdninstagram.com/ola-hd.jpg", onlyHd.profilePicUrl)
    }
}
