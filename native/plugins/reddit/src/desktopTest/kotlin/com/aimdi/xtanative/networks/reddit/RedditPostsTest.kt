package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a post carries and shows: XTA's reddit_media_urls_test, reddit_text_test,
 * reddit_gallery_link_test, reddit_listing_page_test, reddit_community_icon_test
 * and the post groups of reddit_client_test.
 */
class RedditPostsTest {
    private fun post(url: String? = null, domain: String? = null) =
        RedditPost(id = "a", title = "t", subreddit = "x", permalink = "/r/x/comments/a/", url = url, domain = domain)

    @Test
    fun aPictureHostServesAPictureWhateverThePathAndAnImageExtensionIsEnoughAnywhere() {
        assertEquals("https://i.redd.it/abc", RedditMedia.imageUrl("https://i.redd.it/abc"))
        assertNotNull(RedditMedia.imageUrl("https://preview.redd.it/x?width=640"))
        assertEquals("https://example.com/photo.PNG", RedditMedia.imageUrl("https://example.com/photo.PNG"))
        assertNotNull(RedditMedia.imageUrl("https://example.com/anim.gif"))
    }

    @Test
    fun imgursGifvIsAVideoPlayerAndTheGifBesideItTheAnimation() {
        assertEquals("https://i.imgur.com/abc.gif", RedditMedia.imageUrl("https://i.imgur.com/abc.gifv"))
    }

    @Test
    fun aPageIsNotAPicture() {
        assertNull(RedditMedia.imageUrl("https://www.reddit.com/gallery/abc"))
        assertNull(RedditMedia.imageUrl("https://v.redd.it/abc"))
        assertNull(RedditMedia.imageUrl(null))
        assertNull(RedditMedia.imageUrl("not a url at all"))
        assertNull(RedditMedia.imageUrl("javascript:alert(1)"), "only http(s) is ever loaded")
    }

    @Test
    fun aGiphyShareLinkAndASingleImgurPageResolveToTheirFiles() {
        assertEquals(
            "https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif",
            RedditMedia.embeddableImage("https://giphy.com/gifs/funny-cat-l0HlvtIPzPdt2usKs"),
        )
        assertNotNull(RedditMedia.embeddableImage("https://giphy.com/gifs/l0HlvtIPzPdt2usKs"), "a slug without words is the id")
        assertNull(RedditMedia.embeddableImage("https://giphy.com/explore/cats"))
        assertEquals("https://i.imgur.com/AbCd123.jpeg", RedditMedia.embeddableImage("https://imgur.com/AbCd123"))
        assertNull(RedditMedia.embeddableImage("https://imgur.com/a/AbCd123"))
        assertNull(RedditMedia.embeddableImage("https://imgur.com/gallery/AbCd123"))
    }

    @Test
    fun aHostThatNeedsAKeyOrARoundTripIsLeftALink() {
        assertNull(RedditMedia.embeddableImage("https://redgifs.com/watch/somename"))
        assertNull(RedditMedia.embeddableImage("https://tenor.com/view/thing-12345"))
    }

    @Test
    fun redditsMediaTokenNamesAGiphyFileAndAnEmoteNone() {
        val gif = RedditMedia.MEDIA_TOKEN.find("![gif](giphy|l0HlvtIPzPdt2usKs|downsized)")!!
        val emote = RedditMedia.MEDIA_TOKEN.find("![img](emote|free_emotes_pack|joy)")!!

        assertEquals("https://media.giphy.com/media/l0HlvtIPzPdt2usKs/giphy.gif", RedditMedia.tokenImage(gif))
        assertNull(RedditMedia.tokenImage(emote))
    }

    @Test
    fun videoHostsAreKnownWithOrWithoutWww() {
        assertTrue(RedditMedia.isVideoHost("v.redd.it"))
        assertTrue(RedditMedia.isVideoHost("www.youtube.com"))
        assertFalse(RedditMedia.isVideoHost("example.com"))
        assertFalse(RedditMedia.isVideoHost(null))
    }

    @Test
    fun copiesOfOnePictureCollapseAndDifferentPicturesStayInOrder() {
        assertEquals(
            listOf("https://preview.redd.it/abc.jpg?width=1080&s=b"),
            RedditMedia.collapseImageUrls(listOf("https://preview.redd.it/abc.jpg?width=320&s=a", "https://preview.redd.it/abc.jpg?width=1080&s=b")),
        )
        assertEquals(
            listOf("https://i.redd.it/abc.jpg"),
            RedditMedia.collapseImageUrls(listOf("https://preview.redd.it/abc.jpg?width=1080&s=a", "https://i.redd.it/abc.jpg")),
        )
        assertEquals(
            listOf("https://preview.redd.it/one.jpg?width=640", "https://preview.redd.it/two.jpg?width=640"),
            RedditMedia.collapseImageUrls(listOf("https://preview.redd.it/one.jpg?width=640", "https://preview.redd.it/two.jpg?width=640")),
        )
        assertEquals(listOf("https://i.redd.it/a.png"), RedditMedia.collapseImageUrls(listOf("https://i.redd.it/a.png", "https://i.redd.it/a.png")))
    }

    @Test
    fun titlesThatOnlyAnnounceAPictureAreRecognisedAndRealOnesAreNot() {
        listOf("<image>", ">image>", ">image<", "[image]", "(image)", "image", "<Image>", "IMAGE", "<img>", "[pic]", "picture", "&gt;image&gt;", ">gif>", "[video]")
            .forEach { assertTrue(RedditMedia.isPlaceholderTitle(it), it) }
        listOf("Look at this image", "image of a cat", "<imaginary>", "my picture day", "")
            .forEach { assertFalse(RedditMedia.isPlaceholderTitle(it), it) }
    }

    @Test
    fun placeholderLeftoversAreStrippedFromALongerBody() {
        assertEquals("nice one", RedditMedia.stripPlaceholderTokens(">image> nice one"))
        assertEquals("", RedditMedia.stripPlaceholderTokens(">image>"))
        assertEquals("look at this image", RedditMedia.stripPlaceholderTokens("look at this image"))
    }

    @Test
    fun aPicturesCdnLinkIsDroppedFromTheTextButAPageLinkIsKept() {
        assertEquals(
            "\"not a gooner bait\" in question",
            RedditMedia.stripMediaLinks("\"not a gooner bait\" in question: https://preview.redd.it/aabro673w5mh1.jpeg?width=2400&format=pjpg&auto=webp&s=d16262017f25cb6c3bccc4639d5526e6c599bc41"),
        )
        assertEquals("", RedditMedia.stripMediaLinks("https://i.redd.it/abc.jpg"))
        assertEquals("see https://dart.dev/blog", RedditMedia.stripMediaLinks("see https://dart.dev/blog"))
    }

    @Test
    fun aBareVRedditLinkGetsItsDashManifest() {
        assertEquals("https://v.redd.it/abc123xyz/DASHPlaylist.mpd", RedditMedia.vRedditDashUrl("https://v.redd.it/abc123xyz"))
        assertEquals("https://v.redd.it/abc123xyz/DASHPlaylist.mpd", RedditMedia.vRedditDashUrl("https://www.v.redd.it/abc123xyz/"))
        assertNull(RedditMedia.vRedditDashUrl("https://i.redd.it/abc.jpg"))
        assertNull(RedditMedia.vRedditDashUrl("https://v.redd.it/abc.mp4"))
        assertNull(RedditMedia.vRedditDashUrl(null))
        assertNull(RedditMedia.vRedditDashUrl("not a url"))
    }

    @Test
    fun aGalleryLinkAListingCarriedWithoutItsPicturesIsRecognised() {
        assertTrue(RedditMedia.isGalleryUrl("https://www.reddit.com/gallery/abc123"))
        assertTrue(RedditMedia.isGalleryUrl("https://old.reddit.com/gallery/abc123"))
        assertTrue(RedditMedia.isGalleryUrl("https://www.reddit.com/gallery/abc123/"))
        assertFalse(RedditMedia.isGalleryUrl("https://www.reddit.com/r/pics/comments/abc/title/"))
        assertFalse(RedditMedia.isGalleryUrl("https://i.redd.it/abc.jpg"))
        assertFalse(RedditMedia.isGalleryUrl("https://example.test/gallery/abc"))
        assertFalse(RedditMedia.isGalleryUrl(null))
        assertFalse(RedditMedia.isGalleryUrl(""))
    }

    @Test
    fun aCommunityIconIsUnescapedAndItsSignedResizingStripped() {
        assertEquals(
            "https://styles.redditmedia.com/t5_x/styles/communityIcon.png",
            RedditMedia.communityIconUrl(
                communityIcon = "https://styles.redditmedia.com/t5_x/styles/communityIcon.png?width=64&amp;s=abc",
                iconImg = "https://b.thumbs.redditmedia.com/tiny.png",
            ),
        )
        assertEquals("https://b.thumbs.redditmedia.com/logo.png", RedditMedia.communityIconUrl("", "//b.thumbs.redditmedia.com/logo.png"))
        assertNull(RedditMedia.communityIconUrl("https://www.redditstatic.com/icon.png", "default"), "the site's snoo is no community's picture")
    }

    @Test
    fun aDirectPictureIsShownAtFullWidthAndAPageIsNot() {
        assertEquals("https://i.redd.it/abc.jpg", post("https://i.redd.it/abc.jpg", "i.redd.it").imageUrl)
        assertEquals("https://example.com/photo.PNG", post("https://example.com/photo.PNG").imageUrl)
        assertNotNull(post("https://preview.redd.it/x?width=640", "preview.redd.it").imageUrl)
        assertNull(post("https://www.reddit.com/gallery/abc", "reddit.com").imageUrl)
        assertNull(post("https://v.redd.it/abc", "v.redd.it").imageUrl)
        assertNull(post("https://news.example.com/story", "news.example.com").imageUrl)
        assertNull(post().imageUrl)
        assertNull(post("not a url at all").imageUrl)
    }

    @Test
    fun placeholderTitlesStayHiddenBesideThePictureTheyAnnounce() {
        val picture = "https://i.redd.it/abc.jpg"

        assertFalse(post(picture, "i.redd.it").copy(title = "<image>").showsTitle)
        assertTrue(post(picture, "i.redd.it").copy(title = "Look at this").showsTitle)
        assertTrue(post().copy(title = "<image>", isSelf = true).showsTitle, "no media: keep the title")
        assertFalse(post(picture, "i.redd.it").copy(title = ">image>").showsTitle)
        val placeholderBody = post(picture, "i.redd.it").copy(title = "A real title", isSelf = true, selfText = ">image>")
        assertTrue(placeholderBody.showsTitle)
        assertFalse(placeholderBody.showsSelfText)
    }

    @Test
    fun aTitleThatIsThePicturesUrlStaysHiddenBesideIt() {
        val url = "https://preview.redd.it/aabro673w5mh1.jpeg?width=2400&format=pjpg"
        val onlyUrl = post(url, "preview.redd.it").copy(title = url)
        val caption = post(url, "preview.redd.it").copy(title = "look: $url")

        assertFalse(onlyUrl.showsTitle)
        assertEquals("", onlyUrl.displayTitle)
        assertTrue(caption.showsTitle)
        assertEquals("look", caption.displayTitle)
    }

    @Test
    fun aVideoIsRecognisedAndABareVRedditLinkStillPlays() {
        assertTrue(post("https://v.redd.it/abc", "v.redd.it").isVideo)
        assertTrue(post("https://www.youtube.com/watch?v=1", "youtube.com").isVideo)
        assertFalse(post("https://example.com/story", "example.com").isVideo)
        assertFalse(post().isVideo)
        assertEquals("https://v.redd.it/pigday1/DASHPlaylist.mpd", post("https://v.redd.it/pigday1", "v.redd.it").playableVideoUrl)
        assertNull(post("https://youtube.com/watch?v=1", "youtube.com").playableVideoUrl, "only Reddit's own video is rebuilt")
        val withDash = post("https://v.redd.it/abc", "v.redd.it").copy(videoManifestUrl = "https://v.redd.it/abc/DASHPlaylist.mpd")
        assertEquals(withDash.videoManifestUrl, withDash.playableVideoUrl, "the post's own stream wins")
    }

    @Test
    fun linkCardsUseThePreviewElseTheListingThumbnail() {
        val withPreview = post("https://t-online.de/story", "t-online.de")
            .copy(previewImage = "https://preview.redd.it/article.jpg?width=1080", thumbnail = "https://b.thumbs.redditmedia.com/small.jpg")
        val htmlOnly = post("https://t-online.de/story", "t-online.de").copy(thumbnail = "https://b.thumbs.redditmedia.com/article.jpg")

        assertEquals(withPreview.previewImage, withPreview.cardPreviewImage)
        assertEquals("https://b.thumbs.redditmedia.com/article.jpg", htmlOnly.cardPreviewImage)
        assertNull(post().copy(thumbnail = "self").thumbnailUrl, "a sentinel is not a picture")
    }

    @Test
    fun hidingNsfwRemovesOver18Posts() {
        val safe = post()
        val adult = post().copy(id = "b", over18 = true)

        assertEquals(listOf(safe), filterRedditPosts(listOf(safe, adult), hideNsfw = true))
        assertEquals(listOf(safe, adult), filterRedditPosts(listOf(safe, adult), hideNsfw = false))
    }

    @Test
    fun aNextPageIsAppendedInOrderEachPostOnce() {
        fun p(id: String) = post().copy(id = id)
        val first = p("a")

        assertEquals(listOf("a", "b", "c"), appendRedditPosts(listOf(p("a")), listOf(p("b"), p("c"))).map { it.id })
        val appended = appendRedditPosts(listOf(first), listOf(p("a"), p("b")))
        assertEquals(listOf("a", "b"), appended.map { it.id })
        assertSame(first, appended.first(), "the first copy is kept")
        assertEquals(listOf(first), appendRedditPosts(listOf(first), emptyList()))
    }

    private fun child(extra: String = "") = J.parse(
        """{"kind":"t3","data":{"id":"g1","title":"A gallery","subreddit":"pics","permalink":"/r/pics/comments/g1/a_gallery/",""" +
            """"url":"https://www.reddit.com/gallery/g1"$extra}}""",
    )

    @Test
    fun aGalleryCarriesItsPicturesInTheAuthorsOrderUnescaped() {
        val post = RedditJson.post(
            child(
                ""","gallery_data":{"items":[{"media_id":"two"},{"media_id":"one"}]},"media_metadata":{""" +
                    """"one":{"s":{"u":"https://preview.redd.it/one.jpg?width=640&amp;s=abc"}},""" +
                    """"two":{"s":{"u":"https://preview.redd.it/two.jpg?width=640&amp;s=def"}}}""",
            ),
        )!!

        assertEquals(listOf("https://preview.redd.it/two.jpg?width=640&s=def", "https://preview.redd.it/one.jpg?width=640&s=abc"), post.galleryImages)
        assertEquals("https://preview.redd.it/two.jpg?width=640&s=def", post.imageUrl, "a gallery whose url is a page still has a picture")
    }

    @Test
    fun aGalleryPictureIsTheWidestCopyThatFitsAPhoneButAGifKeepsMoving() {
        val post = RedditJson.post(
            child(
                ""","gallery_data":{"items":[{"media_id":"big"},{"media_id":"anim"}]},"media_metadata":{""" +
                    """"big":{"s":{"u":"https://preview.redd.it/big.jpg?width=4000"},"p":[{"x":640,"u":"https://preview.redd.it/big.jpg?width=640"},""" +
                    """{"x":1080,"u":"https://preview.redd.it/big.jpg?width=1080"},{"x":2160,"u":"https://preview.redd.it/big.jpg?width=2160"}]},""" +
                    """"anim":{"e":"AnimatedImage","s":{"gif":"https://i.redd.it/anim.gif"},"p":[{"x":640,"u":"https://preview.redd.it/still.jpg"}]}}""",
            ),
        )!!

        assertEquals(listOf("https://preview.redd.it/big.jpg?width=1080", "https://i.redd.it/anim.gif"), post.galleryImages)
    }

    @Test
    fun galleryFilesWithNoOrderAreStillShownAndNonsenseIsNothing() {
        val unordered = RedditJson.post(child(""","media_metadata":{"only":{"s":{"u":"https://preview.redd.it/only.jpg"}}}"""))!!
        val nonsense = RedditJson.post(child(""","gallery_data":"nonsense","media_metadata":{"one":{"s":"also nonsense"},"two":42}"""))!!

        assertEquals(listOf("https://preview.redd.it/only.jpg"), unordered.galleryImages)
        assertTrue(nonsense.galleryImages.isEmpty())
        assertTrue(RedditJson.post(child())!!.galleryImages.isEmpty())
    }

    @Test
    fun aPreviewIsReadAndUnescapedForTheCardButIsNotThePostsPicture() {
        val post = RedditJson.post(
            J.parse(
                """{"kind":"t3","data":{"id":"v1","title":"A clip","subreddit":"videos","permalink":"/r/videos/comments/v1/a_clip/",""" +
                    """"url":"https://v.redd.it/v1","preview":{"images":[{"source":{"url":"https://preview.redd.it/poster.jpg?width=1080&amp;s=xyz"}}]}}}""",
            ),
        )!!

        assertEquals("https://preview.redd.it/poster.jpg?width=1080&s=xyz", post.previewImage)
        assertNull(post.imageUrl, "a poster frame is the card's banner, not the post's picture")
        assertNull(RedditJson.post(J.parse("""{"kind":"t3","data":{"id":"p","title":"t","subreddit":"x","permalink":"/r/x/comments/p/"}}"""))!!.previewImage)
    }

    @Test
    fun aVideoBringsItsStreamsAndShapeFromItselfOrTheOriginalItCrossposts() {
        val own = RedditJson.post(
            J.parse(
                """{"kind":"t3","data":{"id":"v","title":"t","url":"https://v.redd.it/v","secure_media":{"reddit_video":""" +
                    """{"dash_url":"https://v.redd.it/v/DASHPlaylist.mpd?a=1&amp;b=2","fallback_url":"https://v.redd.it/v/DASH_720.mp4","width":1280,"height":720}}}}""",
            ),
        )!!
        val crosspost = RedditJson.post(
            J.parse(
                """{"kind":"t3","data":{"id":"c","title":"t","crosspost_parent_list":[{"secure_media":{"reddit_video":""" +
                    """{"dash_url":"https://v.redd.it/o/DASHPlaylist.mpd","height":0}}}]}}""",
            ),
        )!!

        assertEquals("https://v.redd.it/v/DASHPlaylist.mpd?a=1&b=2", own.videoManifestUrl)
        assertEquals("https://v.redd.it/v/DASH_720.mp4", own.videoFallbackUrl)
        assertEquals(1280.0 / 720.0, own.videoAspectRatio)
        assertEquals("https://v.redd.it/o/DASHPlaylist.mpd", crosspost.videoManifestUrl)
        assertNull(crosspost.videoAspectRatio, "no height is no shape")
    }

    @Test
    fun childrenThatAreNotPostsAndPostsWithoutATitleAreSkipped() {
        val page = RedditJson.listing(
            J.parse(listingBody(children = """{"kind":"t1","data":{"id":"comment"}},{"kind":"t3","data":{"id":"no_title"}},""" +
                """{"kind":"t3","data":{"id":"ok","title":"Fine","subreddit":"x","permalink":"/x"}}""")),
        )!!

        assertEquals(listOf("ok"), page.items.map { it.id })
        assertNull(page.next, "a null after is the end of the listing")
        assertNull(RedditJson.listing(J.parse("""{"kind":"Listing"}""")), "a listing without data is no listing")
    }

    @Test
    fun aSelfPostCarriesItsTrimmedTextAndASentinelThumbnailIsNone() {
        val post = RedditJson.post(
            J.parse(
                """{"kind":"t3","data":{"id":"s1","title":"Ask anything","subreddit":"dartlang","permalink":"/r/dartlang/comments/s1/x/",""" +
                    """"is_self":true,"selftext":"  Some body text  ","thumbnail":"self","over_18":true,"spoiler":true}}""",
            ),
        )!!

        assertTrue(post.isSelf)
        assertEquals("Some body text", post.selfText)
        assertNull(post.thumbnailUrl)
        assertTrue(post.over18)
        assertTrue(post.spoiler)
    }

    private val thread = "/r/FlutterDev/comments/1xa75i8/why_flutter_text_looks_lighter_than_swiftui_on_ios/"

    @Test
    fun aPostsOwnCopyFillsABareOneWithoutReplacingWhatAFeedPostCameWith() {
        val bare = RedditPost(id = "1xa75i8", title = "1xa75i8", subreddit = "FlutterDev", permalink = thread, isSelf = true)
        val fetched = RedditPost(
            id = "1xa75i8",
            title = "Why Flutter Text Looks Lighter",
            subreddit = "FlutterDev",
            permalink = thread,
            author = "birjuvachhani",
            score = 68,
            commentCount = 15,
            url = "https://www.reddit.com/gallery/1xa75i8",
            galleryImages = listOf("https://preview.redd.it/a.jpeg"),
            flair = "Article",
        )
        assertTrue(bare.arrivedBare)

        val filled = bare.filledFrom(fetched)
        assertEquals("Why Flutter Text Looks Lighter", filled.title)
        assertEquals("birjuvachhani", filled.author)
        assertEquals(68 to 15, filled.score to filled.commentCount)
        assertEquals("https://www.reddit.com/gallery/1xa75i8", filled.url)
        assertFalse(filled.isSelf, "it links somewhere after all")
        assertEquals(listOf("https://preview.redd.it/a.jpeg"), filled.galleryImages)
        assertFalse(filled.arrivedBare)

        val fromFeed = RedditPost(
            id = "1xa75i8",
            title = "Why Flutter Text Looks Lighter",
            subreddit = "FlutterDev",
            permalink = thread,
            flair = "Discussion",
            videoManifestUrl = "https://v.redd.it/x/HLSPlaylist.m3u8",
        )
        val kept = fromFeed.filledFrom(fetched)
        assertEquals("Discussion", kept.flair)
        assertEquals("https://v.redd.it/x/HLSPlaylist.m3u8", kept.videoManifestUrl)
        assertFalse(fromFeed.arrivedBare)
    }

    @Test
    fun ordinaryWordsAreOneRun() {
        assertEquals(listOf(RedditTextPart("hello there")), redditTextParts("hello there"))
        assertTrue(redditTextParts("").isEmpty())
    }

    @Test
    fun aMarkdownLinkIsShownByItsLabelNotItsUrl() {
        val parts = redditTextParts(
            "Download>> [OTs-14](https://drive.google.com/drive/folders/abc?usp=sharing) [Source](https://x.com/GFL2EXILIUM_EN/status/2093171725546770459)",
        )

        assertEquals(listOf("Download>> ", "OTs-14", " ", "Source"), parts.map { it.text })
        assertEquals("https://drive.google.com/drive/folders/abc?usp=sharing", parts[1].url)
        assertEquals("https://x.com/GFL2EXILIUM_EN/status/2093171725546770459", parts[3].url)
    }

    @Test
    fun aLabelEndingInASpaceKeepsItsShortName() {
        val parts = redditTextParts(
            "Sticker Download>> [OTs-14](https://drive.google.com/drive/mobile/folders/1PoYmalZzYdWTWfP2xdD3xGsG0blUU-r?usp=sharing) [Source ](https://x.com/GFL2EXILIUM_EN/status/2093171725546770459)",
        )

        assertEquals(listOf("Sticker Download>> ", "OTs-14", " ", "Source"), parts.map { it.text })
        assertTrue("drive.google.com/drive/mobile/folders/" in parts[1].url!!)
        assertTrue("x.com/GFL2EXILIUM_EN/status/" in parts[3].url!!)
    }

    @Test
    fun bareLinksShrinkToTheirHostOrTheAccountTheyName() {
        val drive = redditTextParts("see https://drive.google.com/drive/folders/abc?usp=sharing").last()
        val status = redditTextParts("https://x.com/GFL2EXILIUM_EN/status/2093171725546770459").single()

        assertEquals("drive.google.com", drive.text)
        assertTrue(drive.url!!.startsWith("https://drive.google.com/"))
        assertEquals("x.com/GFL2EXILIUM_EN", status.text)
        assertTrue(status.isLink)
        assertEquals("r/FlutterDev", compactRedditLinkLabel("https://www.reddit.com/r/FlutterDev/comments/x/"))
    }

    @Test
    fun aLabelIsPreferredUnlessItIsTheUrlItself() {
        assertEquals("Source", compactRedditLinkLabel("https://example.com/a", label = "Source"))
        assertEquals("example.com/story", compactRedditLinkLabel("https://example.com/story", label = "https://example.com/story"))
    }
}
