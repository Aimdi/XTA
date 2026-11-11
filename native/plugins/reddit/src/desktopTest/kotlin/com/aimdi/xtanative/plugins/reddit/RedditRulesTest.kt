package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.RedditSort
import com.aimdi.xtanative.networks.reddit.RedditTimeFilter
import com.aimdi.xtanative.store.settings.RedditFeedMode
import com.aimdi.xtanative.store.settings.RedditNsfwMode
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.RedditSource
import com.aimdi.xtanative.store.settings.Settings
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.aimdi.xtanative.store.settings.RedditSort as StoredSort
import com.aimdi.xtanative.store.settings.RedditTimeFilter as StoredWindow

class RedditRulesTest {
    private val post = RedditPost(id = "p", title = "t", subreddit = "s", permalink = "/r/s/comments/p/t/")

    private fun choices(nsfw: RedditNsfwMode = RedditNsfwMode.TAP, spoilers: Boolean = false) =
        RedditChoices(StoredSort.HOT, StoredWindow.DAY, nsfw, spoilers, preferPublic = false)

    @Test
    fun theStoredOrderIsAskedOfRedditByItsOwnName() {
        assertEquals(RedditSort.NEW, StoredSort.NEWEST.listing())
        assertEquals(RedditSort.CONTROVERSIAL, StoredSort.CONTROVERSIAL.listing())
        assertEquals(RedditTimeFilter.WEEK, StoredWindow.WEEK.listing())
        assertTrue(StoredSort.TOP.usesTimeFilter)
        assertFalse(StoredSort.RISING.usesTimeFilter)
    }

    @Test
    fun theChoicesAreReadFromThePreferencesWithXtasDefaults() {
        val defaults = RedditChoices.of(Settings())
        assertEquals(choices(), defaults)
        val set = RedditChoices.of(Settings().with(RedditPrefs.source, RedditSource.PUBLIC).with(RedditPrefs.showSpoilers, true))
        assertTrue(set.preferPublic && set.showSpoilers)
    }

    @Test
    fun aSpoilerIsCoveredFirstThenOver18PostsAsTheReaderChose() {
        val both = post.copy(over18 = true, spoiler = true)
        assertEquals(MediaGate.SPOILER, choices().gate(both))
        assertEquals(MediaGate.NSFW, choices(spoilers = true).gate(both))
        assertNull(choices(nsfw = RedditNsfwMode.SHOW, spoilers = true).gate(both))
        assertEquals(MediaGate.NSFW, choices(nsfw = RedditNsfwMode.HIDE).gate(post.copy(over18 = true)))
        assertNull(choices().gate(post))
    }

    @Test
    fun hidingOver18PostsTakesThemOutOfTheListings() {
        val adult = post.copy(id = "adult", over18 = true)
        assertEquals(listOf(post), choices(nsfw = RedditNsfwMode.HIDE).shown(listOf(post, adult)))
        assertEquals(listOf(post, adult), choices(nsfw = RedditNsfwMode.TAP).shown(listOf(post, adult)))
    }

    @Test
    fun aPostShowsItsVideoThenItsGalleryThenItsPictureThenItsLink() {
        val video = post.copy(url = "https://v.redd.it/abc", domain = "v.redd.it")
        assertEquals(PostMedia.Video("https://v.redd.it/abc/HLSPlaylist.m3u8"), RedditMediaRules.of(video))
        val gallery = post.copy(galleryImages = listOf("https://i.redd.it/1.jpg", "https://i.redd.it/2.jpg"))
        assertEquals(PostMedia.Gallery(listOf("https://i.redd.it/1.jpg", "https://i.redd.it/2.jpg")), RedditMediaRules.of(gallery))
        assertEquals(PostMedia.Image("https://i.redd.it/1.jpg"), RedditMediaRules.of(post.copy(url = "https://i.redd.it/1.jpg")))
        assertEquals(PostMedia.GalleryLink("https://www.reddit.com/gallery/abc"), RedditMediaRules.of(post.copy(url = "https://www.reddit.com/gallery/abc")))
        assertEquals(PostMedia.Link("https://example.com/a"), RedditMediaRules.of(post.copy(url = "https://example.com/a")))
        assertNull(RedditMediaRules.of(post.copy(url = "https://example.com/a", isSelf = true)))
        assertNull(RedditMediaRules.of(post))
    }

    @Test
    fun aRedditVideoPlaysAsItsHlsPlaylistWithItsSound() {
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8", RedditMediaRules.stream("https://v.redd.it/abc/DASHPlaylist.mpd?a=1"))
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8", RedditMediaRules.stream("https://v.redd.it/abc/DASH_720.mp4?source=fallback"))
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&f=sd", RedditMediaRules.stream("https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&f=sd"))
        assertEquals("https://example.com/v.mp4", RedditMediaRules.stream("https://example.com/v.mp4"))
    }

    @Test
    fun aShapeIsBoundedAtBothEnds() {
        assertEquals(16.0 / 9.0, RedditMediaRules.aspectRatio(null))
        assertEquals(9.0 / 16.0, RedditMediaRules.aspectRatio(0.1))
        assertEquals(16.0 / 9.0, RedditMediaRules.aspectRatio(5.0))
        assertEquals(1.25, RedditMediaRules.aspectRatio(1.25))
        assertEquals(16.0 / 9.0, RedditMediaRules.aspectRatio(Double.NaN))
    }

    @Test
    fun aCardCountsInFullAsXtasDoes() {
        val post = RedditPost(id = "p", title = "t", subreddit = "dartlang", permalink = "/r/dartlang/comments/p/", score = 12345, commentCount = 6789)
        assertEquals("12345", RedditCounts.score(post, upvoted = false), "never 12.3K")
        assertEquals("12346", RedditCounts.score(post, upvoted = true), "with this device's upvote in it")
        assertEquals("6789", RedditCounts.comments(post))
    }

    @Test
    fun aSavedPictureIsNamedAfterWhoPostedIt() {
        val post = RedditPost(id = "p", title = "t", subreddit = "pics", permalink = "/r/pics/comments/p/", author = "alice")
        assertEquals("alice-abc.jpg", RedditMediaRules.fileName("https://preview.redd.it/abc.jpg?width=640&s=1", post), "the file alone, without the address's query")
        assertEquals("pics-abc.jpg", RedditMediaRules.fileName("https://i.redd.it/abc.jpg", post.copy(author = null)), "the community stands in for a deleted author")
        assertEquals("reddit-x.gif", RedditMediaRules.fileName("https://i.redd.it/x.gif"), "a comment's picture")
        assertEquals(null, RedditMediaRules.fileName("https://i.redd.it/", post), "nothing to name it by: the app names it")
        assertEquals(null, RedditMediaRules.fileName("not an address", post))
    }

    @Test
    fun aLinkCardNamesTheSite() {
        assertEquals("example.com", RedditMediaRules.domain(post, "https://www.example.com/a"))
        assertEquals("i.imgur.com", RedditMediaRules.domain(post.copy(domain = "i.imgur.com"), "https://x"))
    }

    @Test
    fun theTabShowsARailOrOneFollowedCommunity() {
        val rail = RedditHomeSource.of(Settings())
        assertEquals(RedditHomeSource(RedditFeedMode.FOLLOWING, null), rail)
        assertTrue(rail.railSelected(RedditFeedMode.FOLLOWING))
        assertEquals("following", rail.key)

        val open = RedditHomeSource.of(RedditHomeSource.selectingSubreddit(Settings(), "r/FlutterDev"))
        assertEquals("FlutterDev", open.subreddit)
        assertEquals("r/flutterdev", open.key)
        assertFalse(open.railSelected(RedditFeedMode.FOLLOWING))
        assertTrue(open.isSelected("flutterdev"))

        val popular = RedditHomeSource.of(RedditHomeSource.selectingMode(RedditHomeSource.selectingSubreddit(Settings(), "pics"), RedditFeedMode.POPULAR))
        assertEquals(RedditHomeSource(RedditFeedMode.POPULAR, null), popular)
        assertEquals(Settings(), RedditHomeSource.selectingSubreddit(Settings(), "not a name!"))
    }

    @Test
    fun aCommunityNoLongerFollowedDoesNotOpenAgain() {
        val open = RedditHomeSource.selectingSubreddit(Settings(), "pics")
        assertEquals(open, RedditHomeSource.reconciled(open, listOf("PICS")))
        assertNull(RedditHomeSource.of(RedditHomeSource.reconciled(open, listOf("dartlang"))).subreddit)
        assertTrue(listOf("FlutterDev").follows("flutterdev"))
    }

    @Test
    fun aFailureIsNamedAsXtaNamesIt() {
        assertEquals(RedditTrouble.CLIENT_ID, RedditTrouble.of(NetworkRequestException("reddit", 401, "x")))
        assertEquals(RedditTrouble.BLOCKED, RedditTrouble.of(NetworkRequestException("reddit", 403, "x")))
        assertEquals(RedditTrouble.NOT_FOUND, RedditTrouble.of(NetworkRequestException("reddit", 404, "x")))
        assertEquals(RedditTrouble.RATE_LIMITED, RedditTrouble.of(NetworkRequestException("reddit", 429, "x")))
        assertEquals(RedditTrouble.RESPONSE, RedditTrouble.of(NetworkRequestException("reddit", 200, "x")))
        assertEquals(RedditTrouble.NETWORK, RedditTrouble.of(IOException("down")))
        assertEquals(RedditTrouble.RESPONSE, RedditTrouble.of(IllegalStateException()))
        assertEquals(RedditTrouble.NOT_CONFIGURED, RedditTrouble.of(IllegalArgumentException("Signing in still needs a client id")))
        assertEquals(RedditTrouble.NOT_CONFIGURED, RedditTrouble.of(IllegalArgumentException("Missing client id")))
    }

    @Test
    fun aColourIsDarkenedOrLightenedUntilItReadsOnThePage() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(21.0, RedditContrast.ratio(white, black), 0.01)
        val amber = 0xFFD9A646.toInt()
        assertTrue(RedditContrast.ratio(amber, white) < 3)
        assertTrue(RedditContrast.ratio(RedditContrast.ensure(amber, white, 3.0), white) >= 3)
        assertEquals(amber, RedditContrast.ensure(amber, black, 3.0))
    }

    @Test
    fun aNameAlwaysHasTheSameFaceWhateverItsCase() {
        assertEquals(RedditFaces.of("r/dartlang"), RedditFaces.of("R/DartLang"))
        // As XTA's redditAvatarSeed and redditAvatarFor give them: the second face, the burnt orange, the blue accent.
        assertEquals(351720937, RedditFaces.seed("spez"))
        val face = RedditFaces.of("spez")
        assertEquals(RedditFaces.faces[1], face.rows)
        assertEquals(0xFFB25400.toInt(), face.background)
        assertEquals(0xFF448AFF.toInt(), face.accent)
        assertEquals(288152099, RedditFaces.seed("r/dartlang"))
        assertEquals(0xFFFFFFFF.toInt(), RedditFace(face.rows, 0xFF37474F.toInt(), 0).ink)
        assertEquals(0xFF15202B.toInt(), RedditFace(face.rows, 0xFFE9E9E9.toInt(), 0).ink)
    }

    @Test
    fun aboutsPictureIsNothingWhileReadEmptyAfterAMissElseThePicture() {
        assertNull(RedditAboutIcon.of(read = false, iconUrl = null), "while loading, so a kept picture can still paint")
        assertNull(RedditAboutIcon.of(read = false, iconUrl = "https://styles.redditmedia.com/a.png"))
        assertEquals("", RedditAboutIcon.of(read = true, iconUrl = null), "a miss is not looked up again")
        assertEquals("", RedditAboutIcon.of(read = true, iconUrl = ""))
        assertEquals("https://styles.redditmedia.com/a.png", RedditAboutIcon.of(read = true, iconUrl = "https://styles.redditmedia.com/a.png"))
    }

    @Test
    fun theBarLeadsWithSearchThenTheOrderThenOneMenuAndNoPlus() {
        assertEquals(listOf(RedditHomeChrome.Action.SEARCH, RedditHomeChrome.Action.SORT, RedditHomeChrome.Action.MENU), RedditHomeChrome.actions)
    }

    @Test
    fun theMenuOffersSavedCommunitiesTheRoutesAndRedditsSettingsNotASignIn() {
        assertEquals(
            listOf(
                RedditHomeChrome.MenuItem.SAVED,
                RedditHomeChrome.MenuItem.SUBSCRIPTIONS,
                RedditHomeChrome.MenuItem.SOURCE_AUTO,
                RedditHomeChrome.MenuItem.SOURCE_PUBLIC,
                RedditHomeChrome.MenuItem.SETTINGS,
            ),
            RedditHomeChrome.menu,
        )
    }

    @Test
    fun choosingHowRedditIsReadStoresItAndTheChosenRouteIsChecked() {
        val chosen = RedditHomeChrome.choosing(Settings(), RedditHomeChrome.MenuItem.SOURCE_PUBLIC)!!
        assertEquals(RedditSource.PUBLIC, chosen[RedditPrefs.source])
        assertTrue(RedditHomeChrome.checked(RedditHomeChrome.MenuItem.SOURCE_PUBLIC, RedditChoices.of(chosen)))
        assertFalse(RedditHomeChrome.checked(RedditHomeChrome.MenuItem.SOURCE_AUTO, RedditChoices.of(chosen)))
        assertNull(RedditHomeChrome.choosing(chosen, RedditHomeChrome.MenuItem.SOURCE_PUBLIC), "the route in force: nothing to read again")
        assertNull(RedditHomeChrome.choosing(chosen, RedditHomeChrome.MenuItem.SAVED))
    }
}
