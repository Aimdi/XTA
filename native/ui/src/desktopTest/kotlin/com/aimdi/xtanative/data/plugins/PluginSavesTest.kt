package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedMedia
import com.aimdi.xtanative.ui.plugins.PluginFiledPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.saved.Keepable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the post sheets and Saved's filing hand the plugins that send a post elsewhere. */
class PluginSavesTest {
    private val jack = XUser("12", "jack", "Jack")
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/20/pu/img/T.jpg",
        1280,
        720,
        variants = listOf(XVideoVariant("https://video.twimg.com/x/1280x720/high.mp4", 2_176_000, "video/mp4")),
    )
    private val post = XPost("20", jack, null, "just setting up\nmy twttr", media = listOf(video))
    private val videoKept = SavedMedia("20-0", XMedia.Kind.VIDEO, video.url, video.variants)

    @Test
    fun anXPostGoesByItsAuthorAndItsWordsWithItsMediaAsTheArchiveKeepsThem() {
        val shared = PluginSaves.shared(post, "https://x.com/jack/status/20")
        assertEquals(PluginSharedPost("https://x.com/jack/status/20", "Jack (@jack): just setting up my twttr", listOf(videoKept)), shared)
    }

    @Test
    fun aRepostGoesByThePostItShares() {
        val repost = XPost("30", XUser("13", "biz", "Biz"), null, "RT", reposted = post)
        assertEquals("Jack (@jack): just setting up my twttr", PluginSaves.shared(repost, post.url).title)
    }

    @Test
    fun aPluginsPostGoesByWhatItsPluginGaveAndTheMediaOfItsRecord() {
        val archive = PluginPostArchive.link("example", "https://example.com/p/7", "Ann", "A study", listOf("https://example.com/a.jpg"))
        val given = PluginSaves.shared(PluginPostMenu(archive, "https://example.com/p/7", title = "Ann: A study"))
        assertEquals("Ann: A study", given.title)
        assertEquals(listOf("https://example.com/a.jpg"), given.media.map { it.url })
        assertEquals("", PluginSaves.shared(PluginPostMenu(archive, "https://example.com/p/7")).title, "no title is none, not a guess")
    }

    @Test
    fun aFilingReachesThePluginsOnlyWhileTheFoldersSwitchIsOn() {
        val item = Keepable.of(post)
        assertEquals(PluginFiledPost("20", "Art", listOf(videoKept)), PluginSaves.filed(SavedFolder("f1", "Art", autoUpload = true), item))
        assertNull(PluginSaves.filed(SavedFolder("f2", "Later"), item))
    }
}
