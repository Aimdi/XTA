package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivIllust
import kotlin.test.Test
import kotlin.test.assertEquals

class PixivDownloadsTest {
    private val illust = PixivIllust(id = 12, title = "", caption = "", type = "illust", thumbnailUrl = "", pageCount = 2, userId = 1, userName = "", userAccount = "")

    @Test
    fun aSharedPictureIsNamedAsPixivNamesItsPagesWithTheTypeItsFileIs() {
        assertEquals("12_p1.png" to "image/png", PixivDownloads.sharedName(illust, 1, "https://i.pximg.net/img-original/img/2024/01/01/00/00/00/12_p1.png"))
        assertEquals("12_p0.jpg" to "image/jpeg", PixivDownloads.sharedName(illust, 0, "https://i.pximg.net/c/600x1200_90/img-master/img/12_p0_master1200.jpg"))
        assertEquals("12_p0.gif" to "image/gif", PixivDownloads.sharedName(illust, 0, "https://mirror.example/img/12_p0.GIF"))
        assertEquals("12_p0.webp" to "image/webp", PixivDownloads.sharedName(illust, 0, "https://mirror.example/img/12_p0.webp?x=1"))
    }

    @Test
    fun aFileOfNoKnownTypeIsSharedAsAJpeg() {
        assertEquals("12_p0.jpg" to "image/jpeg", PixivDownloads.sharedName(illust, 0, "https://i.pximg.net/img/12_p0"))
        assertEquals("12_p0.jpg" to "image/jpeg", PixivDownloads.sharedName(illust, 0, "not a link"))
    }
}
