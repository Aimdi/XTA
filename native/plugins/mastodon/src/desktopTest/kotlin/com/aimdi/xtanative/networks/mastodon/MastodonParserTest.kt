package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parser on each response shape, with the rules XTA's mastodon_models_test.dart and mastodon_reader_metadata_test.dart encode. */
class MastodonParserTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/mastodon/$name")!!.readText())

    private val statuses = MastodonParser.statuses(fixture("account_statuses.json"), "mastodon.social")

    private fun status(id: String) = statuses.single { it.id == id }

    private fun instant(text: String) = Instant.parse(text)

    // ---- statuses ----

    @Test
    fun aStatusReadsItsTextAuthorMediaCountsAndCard() {
        val post = status("113000000000000010")
        assertEquals("Hello @alice :blobcat: see #Fediverse\nand https://example.org/article", post.text)
        assertEquals("gargron@mastodon.social", post.acct)
        assertEquals("Eugen Rochko :verified:", post.authorName)
        assertEquals("https://mastodon.social/@Gargron/113000000000000010", post.url)
        assertEquals(instant("2026-08-01T09:00:00Z"), post.publishedAt)
        assertEquals(listOf(3L, 12L, 40L), listOf(post.repliesCount, post.reblogsCount, post.favouritesCount))
        assertEquals(listOf("alice@mastodon.online"), post.mentionAccts)

        val picture = post.media.single()
        assertEquals("https://files.mastodon.social/media_attachments/files/000/000/200/small/cat.jpg", picture.url, "the video without a file and the repeated picture are left out")
        assertEquals("https://files.mastodon.social/media_attachments/files/000/000/200/original/cat.jpg", picture.originalUrl)
        assertEquals("A cat sitting by a window", picture.description)
        assertEquals(4.0 / 3, picture.aspectRatio!!, 0.001)
        assertFalse(picture.isPlayable)

        val card = post.linkCard!!
        assertEquals(MastodonLinkCard("https://example.org/article", "An article", "About things", card.imageUrl, "Example", "link"), card)
        assertTrue(card.hasImage)
    }

    @Test
    fun aBoostIsUnwrappedToThePostItShares() {
        val post = status("112999000000000001")
        assertTrue(post.boosted)
        assertEquals("Boosted #art", post.text)
        assertEquals("bob@other.social", post.acct)
        assertEquals("bob@other.social", post.authorName, "no display name reads as the address")
        assertEquals("https://other.social/@bob/98765", post.url)
        assertEquals("Eugen Rochko :verified:", post.boostedBy)
        assertEquals("gargron@mastodon.social", post.boostedByAcct)
        assertEquals(listOf(1L, 2L, 5L), listOf(post.repliesCount, post.reblogsCount, post.favouritesCount))
        assertNull(post.avatarUrl, "an empty avatar is no avatar, even with avatar_static set")
    }

    @Test
    fun aBoostPagesAndIsDatedByItsWrapperButKeepsTheOriginalsIdentity() {
        val post = status("112999000000000001")
        assertEquals("113000000000000009", post.pagingId)
        assertEquals(instant("2026-07-31T20:00:00Z"), post.publishedAt)
        assertEquals(instant("2026-08-01T08:30:00Z"), post.timelineDate)
    }

    @Test
    fun aGifvPlaysTheRemoteFileUntilTheInstanceHasCachedIt() {
        val clip = status("112999000000000001").media.single()
        assertEquals(MastodonMedia.Kind.GIFV, clip.kind)
        assertTrue(clip.isGif && clip.isVideo && clip.isPlayable)
        assertEquals("https://other.social/system/media_attachments/files/000/000/300/original/g.mp4", clip.videoUrl)
        assertEquals("https://files.mastodon.social/cache/media_attachments/files/000/000/300/small/g.png", clip.url, "the preview is its poster")
        assertEquals(1.0, clip.aspectRatio)
        assertNull(clip.description, "an empty alt text is none")
    }

    @Test
    fun aLinkOnlyStatusIsKept() {
        val post = status("113000000000000008")
        assertEquals("", post.text)
        assertFalse(post.hasMedia)
        assertEquals("https://news.example/story", post.linkCard?.url)
        assertNull(post.linkCard?.description)
        assertNull(post.linkCard?.providerName, "a blank provider is none")
        assertEquals("video", post.linkCard?.type)
    }

    @Test
    fun emptyStatusesAndOnesWithoutAnIdOrAddressAreDropped() {
        assertEquals(9, statuses.size, "of the twelve in the fixture")
        assertTrue(statuses.none { it.id == "113000000000000001" || it.id == "" || it.id == "113000000000000000" })
        assertTrue(MastodonParser.statuses(J.parse("null")).isEmpty())
        assertTrue(MastodonParser.statuses(J.parse("\"nope\"")).isEmpty())
        assertTrue(MastodonParser.statuses(J.parse("""{"id":"1"}""")).isEmpty())
    }

    @Test
    fun aContentWarningStaysOutOfTheBody() {
        val post = status("113000000000000007")
        assertEquals("They all lived.", post.text)
        assertEquals("Spoilers for the finale", post.spoilerText)
        assertTrue(post.hasSpoiler && post.sensitive)
        val picture = post.media.single()
        assertEquals("https://files.mastodon.social/media_attachments/files/000/000/400/original/finale.jpg", picture.url, "an empty preview falls back to the picture")
        assertNull(picture.aspectRatio, "a zero height gives no shape")
    }

    @Test
    fun aWarningMarksTheMediaSensitiveWhenTheFlagIsMissing() {
        val post = MastodonParser.status(J.parse("""{"id":"1","url":"https://s.example/@a/1","spoiler_text":"cw","content":"<p>x</p>","account":{"acct":"a"}}"""))!!
        assertTrue(post.sensitive)
    }

    @Test
    fun aPollIsReadForItsResults() {
        val poll = status("113000000000000007").poll!!
        assertEquals(listOf(MastodonPoll.Option("Yes", 6), MastodonPoll.Option("No", 3)), poll.options, "an option without a title is left out")
        assertEquals(10L, poll.votesCount)
        assertEquals(8L, poll.votersCount)
        assertTrue(poll.multiple && !poll.expired && poll.resultsAvailable)
        assertEquals(instant("2026-08-02T07:00:00Z"), poll.expiresAt)
        assertEquals(0.75, poll.fraction(poll.options.first()), "several choices count voters")
        assertFalse(poll.closed(instant("2026-08-01T12:00:00Z")))
        assertTrue(poll.closed(instant("2026-08-03T00:00:00Z")))
    }

    @Test
    fun aPollThatHoldsItsResultsBackDoesNotClaimZeroVotes() {
        val poll = status("113000000000000002").poll!!
        assertFalse(poll.resultsAvailable)
        assertEquals(listOf("A", "B"), poll.options.map { it.title })
        assertNull(poll.fraction(poll.options.first()))
        assertNull(poll.votersCount)
        assertTrue(poll.closed(instant("2026-08-01T12:00:00Z")), "expired, whatever its end date says")
    }

    @Test
    fun pollSharesAreBoundedAndNeedATotal() {
        val choice = MastodonPoll.Option("Choice", 6)
        assertEquals(0.6, MastodonPoll(listOf(choice), votesCount = 10).fraction(choice))
        assertEquals(0.75, MastodonPoll(listOf(choice), votesCount = 10, votersCount = 8, multiple = true).fraction(choice))
        assertNull(MastodonPoll(listOf(choice), votesCount = 10, multiple = true).fraction(choice))
        assertEquals(1.0, MastodonPoll(listOf(choice), votesCount = 2).fraction(choice))
        assertEquals(0.0, MastodonPoll(listOf(choice), votesCount = 0).fraction(choice))
        assertNull(MastodonPoll(listOf(choice), resultsAvailable = false).fraction(choice))
    }

    @Test
    fun aReplyNamesTheFirstAccountItMentions() {
        val post = status("113000000000000006")
        assertEquals("carol@social.example", post.replyToAcct)
        assertEquals(listOf("carol@social.example", "dave"), post.mentionAccts, "a mention without an address is left out")
        assertEquals("113000000000000001", post.replyToId)
        assertTrue(post.isReply)
    }

    @Test
    fun aReplyToTheAuthorsOwnPostMentionsNobodyButIsStillAReply() {
        val post = status("113000000000000003")
        assertNull(post.replyToAcct)
        assertTrue(post.isReply)
        assertEquals("https://mastodon.social/users/Gargron/statuses/113000000000000003", post.url, "no url: the uri stands in")
        assertEquals("Continuing my own thread.\nSecond paragraph & more.", post.text)
    }

    @Test
    fun anEditedPostReadsItsQuoteOneLevelDeep() {
        val post = status("113000000000000005")
        assertTrue(post.edited)
        val quote = post.quote!!
        assertEquals("113000000000000004", quote.id)
        assertEquals("dave@mastodon.social", quote.acct)
        assertEquals("quoted secret", quote.text)
        assertEquals("Spoiler", quote.spoilerText)
        assertTrue(quote.sensitive && quote.hasSpoiler)
        assertTrue(quote.media.single().isPlayable)
        assertNull(quote.quote, "a quote carries no quote of its own")
    }

    @Test
    fun theOlderQuoteShapesAreReadToo() {
        fun quoteOf(json: String) = MastodonParser.status(J.parse(json))?.quote?.text
        val quoted = """{"id":"9","url":"https://s.example/@b/9","content":"<p>quoted</p>","account":{"acct":"b"}}"""
        assertEquals("quoted", quoteOf("""{"id":"1","url":"https://s.example/@a/1","content":"<p>x</p>","account":{"acct":"a"},"quoted_status":$quoted}"""))
        assertEquals("quoted", quoteOf("""{"id":"1","url":"https://s.example/@a/1","content":"<p>x</p>","account":{"acct":"a"},"quote":$quoted}"""))
        assertNull(quoteOf("""{"id":"1","url":"https://s.example/@a/1","content":"<p>x</p>","account":{"acct":"a"},"quote":{"state":"pending","quoted_status":null}}"""))
    }

    @Test
    fun aNumberForAnIdIsReadAsTheId() {
        val post = status("4242")
        assertEquals("ak@akkoma.example", post.acct)
        assertEquals(1.5, post.media.single().aspectRatio, "an aspect written as a string is not taken, the size is")
    }

    @Test
    fun aBoostKeepsItsWrapperCursorAndDateApartFromTheOriginal() {
        val post = MastodonParser.status(
            J.parse(
                """{"id":"200","created_at":"2026-09-20T10:00:00Z","account":{"acct":"booster"},"reblog":
                {"id":"10","url":"https://social.example/@reader/10","created_at":"2026-09-01T10:00:00Z","content":"<p>Reading</p>","account":{"id":"1","acct":"reader"}}}""",
            ),
            "social.example",
        )!!
        assertEquals("10", post.id)
        assertEquals("200", post.pagingId)
        assertEquals(instant("2026-09-01T10:00:00Z"), post.publishedAt)
        assertEquals(instant("2026-09-20T10:00:00Z"), post.timelineDate)
        assertEquals("booster@social.example", post.boostedByAcct)
    }

    // ---- beyond XTA: audio, custom emoji, media meta ----

    @Test
    fun audioIsReadBesideTheMedia() {
        val post = status("4242")
        assertEquals(1, post.media.size)
        assertEquals(MastodonAudio("https://akkoma.example/media/a.mp3", "https://akkoma.example/media/cover.png", "A song", 129.98), post.audio.single())
    }

    @Test
    fun aPostWithOnlyAudioIsKeptWhereXtaDroppedIt() {
        val post = MastodonParser.status(
            J.parse(
                """{"id":"1","url":"https://s.example/@a/1","content":"","account":{"acct":"a"},
                "media_attachments":[{"type":"audio","url":null,"remote_url":"https://r.example/a.ogg","meta":{"original":{"duration":61.2}}}]}""",
            ),
        )!!
        assertTrue(post.media.isEmpty())
        assertEquals(MastodonAudio("https://r.example/a.ogg", durationSeconds = 61.2), post.audio.single())
    }

    @Test
    fun customEmojiAreReadForTheTextAndForTheAuthorsName() {
        val post = status("113000000000000010")
        assertEquals(listOf("blobcat"), post.emojis.map { it.shortcode })
        assertEquals("https://files.mastodon.social/custom_emojis/images/000/000/001/static/blobcat.png", post.emojis.single().staticUrl)
        assertEquals(listOf("verified"), post.authorEmojis.map { it.shortcode })
        val emoji = MastodonText.parts(post).filterIsInstance<MastodonTextPart.Emoji>().single()
        assertEquals(":blobcat:", emoji.text)
    }

    @Test
    fun mediaCarriesItsMeta() {
        val picture = status("113000000000000010").media.single()
        assertEquals(1600, picture.width)
        assertEquals(1200, picture.height)
        assertEquals("UFBWY:8_0Jxv4mx]t8t64.%M-:IUWGWAt6M}", picture.blurhash)
        assertEquals(MastodonMedia.Focus(-0.42, 0.69), picture.focus)
        val clip = status("112999000000000001").media.single()
        assertEquals(2.5, clip.durationSeconds)
        assertEquals(480, clip.width)
        assertEquals(12.5, status("113000000000000005").quote!!.media.single().durationSeconds)
    }

    // ---- accounts ----

    @Test
    fun aProfileReadsItsFieldsHeaderAndEmoji() {
        val profile = MastodonParser.profile(fixture("account_lookup.json"), "mastodon.social")
        assertEquals("1", profile.id)
        assertEquals("gargron@mastodon.social", profile.acct)
        assertEquals("Gargron", profile.username)
        assertEquals("Eugen Rochko :verified:", profile.displayName)
        assertEquals("Founder, CEO and lead developer @Mastodon, Germany.\nSecond line & more", profile.note)
        assertEquals("https://files.mastodon.social/accounts/headers/000/000/001/original/header.png", profile.headerUrl, "an unsafe header_static gives way to header")
        assertEquals(instant("2016-03-16T00:00:00Z"), profile.createdAt)
        assertEquals(listOf(312_000L, 400L, 77_000L), listOf(profile.followersCount, profile.followingCount, profile.statusesCount))
        assertEquals("https://mastodon.social/@Gargron", profile.webUrl)

        val (patreon, homepage, pronouns) = profile.fields
        assertEquals(3, profile.fields.size, "fields without a name are left out")
        assertEquals(MastodonField("Patreon", "https://www.patreon.com/mastodon", "https://www.patreon.com/mastodon", null), patreon)
        assertEquals(instant("2019-07-15T18:29:57.191Z"), homepage.verifiedAt)
        assertEquals(MastodonField("Pronouns", "he/him"), pronouns)
        assertEquals(listOf("verified"), profile.emojis.map { it.shortcode }, "emoji without a shortcode or a safe picture are left out")
    }

    @Test
    fun aProfileFieldLinksOnlyToExactlyOneSafeAddress() {
        val profile = MastodonParser.profile(
            J.parse(
                """{"header_static":"https://media.example/banner.png","created_at":"2020-01-01T00:00:00Z","fields":[
                {"name":"Site","value":"<a href=\"https://example.org\">My site</a>","verified_at":"2025-01-01T00:00:00Z"},
                {"name":"Unsafe","value":"<a href=\"javascript:alert(1)\">Do not open</a>"},
                {"name":"Ambiguous","value":"<a href=\"https://a.example\">A</a><a href=\"https://b.example\">B</a>"},
                {"name":"Credentials","value":"<a href=\"https://secret@example.org\">No</a>"}]}""",
            ),
        )
        assertEquals("https://media.example/banner.png", profile.headerUrl)
        assertEquals(MastodonField("Site", "My site", "https://example.org", instant("2025-01-01T00:00:00Z")), profile.fields.first())
        assertTrue(profile.fields.drop(1).all { it.url == null })
    }

    @Test
    fun malformedOptionalMetadataNeverBlocksAProfileOrAPost() {
        val profile = MastodonParser.profile(J.parse("""{"header":[],"created_at":7,"fields":[null,false]}"""))
        assertNull(profile.headerUrl)
        assertNull(profile.createdAt)
        assertTrue(profile.fields.isEmpty())
        val post = MastodonParser.status(
            J.parse("""{"id":"20","url":"https://s.example/@a/20","content":"<p>x</p>","account":{"acct":"a"},"poll":{"options":[false,{"title":"Option","votes_count":[]}]}}"""),
        )!!
        assertFalse(post.poll!!.resultsAvailable)
        assertEquals(listOf(MastodonPoll.Option("Option", 0)), post.poll!!.options)
    }

    @Test
    fun aLocalNameGetsTheInstancesDomainAndAMissingNameTheAddress() {
        val profile = MastodonParser.profile(J.parse("""{"id":"1","acct":"Alice","note":"<p>Hi</p>"}"""), "Mastodon.Social")
        assertEquals("alice@mastodon.social", profile.acct)
        assertEquals("alice", profile.username)
        assertEquals("alice@mastodon.social", profile.displayName)
        assertNull(profile.webUrl)
    }

    // ---- context, search, trends ----

    @Test
    fun aContextHasItsTwoHalves() {
        val context = MastodonParser.context(fixture("status_context.json"), "social.example")
        assertEquals(listOf("110", "111"), context.ancestors.map { it.id })
        assertEquals(listOf("113", "114"), context.descendants.map { it.id }, "the empty reply is dropped")
        assertEquals("guest@remote.example", context.descendants.first().acct)
        assertEquals("writer@social.example", context.descendants.last().acct)
        assertEquals("guest@remote.example", context.descendants.last().replyToAcct)
    }

    @Test
    fun aSearchReadsAccountsStatusesAndHashtags() {
        val page = MastodonParser.search(fixture("search.json"), "mastodon.social")
        assertEquals(listOf("alice@mastodon.social", "bob@other.social"), page.accounts.map { it.acct })
        assertTrue(page.accounts.first().bot && page.accounts.last().locked)
        assertEquals("about #photography", page.posts.single().text)
        assertEquals(listOf(MastodonTag("photography", "https://mastodon.social/tags/photography", 20), MastodonTag("PhotographyTips", "https://mastodon.social/tags/PhotographyTips", 0)), page.tags)
        assertFalse(page.isEmpty)
        assertTrue(MastodonParser.search(J.parse("{}")).isEmpty)
    }

    @Test
    fun trendingTagsAddUpTheirDays() {
        val tags = MastodonParser.tags(fixture("trends_tags.json"))
        assertEquals(listOf(MastodonTag("flutter", "https://mastodon.social/tags/flutter", 10), MastodonTag("kotlin", null, 0)), tags)
    }

    // ---- Misskey ----

    @Test
    fun misskeyNotesReadOntoPosts() {
        val notes = MastodonParser.misskeyNotes(fixture("misskey_notes.json"), "https://misskey.io")
        assertEquals(listOf("9x1", "9x0", "9x3", "9x4"), notes.map { it.id }, "an empty note and ones without an id or a user are dropped")

        val note = notes[0]
        assertEquals("neo@misskey.io", note.acct)
        assertEquals("Neo", note.authorName)
        assertEquals("hello from misskey", note.text)
        assertEquals("cats", note.spoilerText)
        assertTrue(note.sensitive)
        assertEquals(listOf("https://misskey.io/files/thumb-f1.webp", "https://misskey.io/files/f2.mp4"), note.media.map { it.url })
        assertEquals(4L, note.favouritesCount, "reactions are its likes")
        assertEquals(listOf(2L, 4L), listOf(note.repliesCount, note.reblogsCount))
        assertEquals("https://misskey.io/notes/9x1", note.url)
        assertEquals("https://misskey.io/avatar/neo.webp", note.avatarUrl)
    }

    @Test
    fun aMisskeyRenoteIsABoostAndOneWithTextAQuote() {
        val (_, renote, quote, reply) = MastodonParser.misskeyNotes(fixture("misskey_notes.json"), "https://misskey.io")
        assertTrue(renote.boosted)
        assertEquals("author@other.example", renote.acct)
        assertEquals("original", renote.text)
        assertEquals("boosty", renote.boostedBy, "a booster without a name goes by the username")
        assertEquals("boosty@followed.example", renote.boostedByAcct)
        assertEquals("9x2", renote.pagingId)
        assertEquals(5L, renote.favouritesCount)

        assertFalse(quote.boosted)
        assertEquals("adding this", quote.text)
        assertEquals("boosty@misskey.io", quote.acct)
        assertEquals("author@other.example", quote.quote?.acct)

        assertEquals("neo", reply.replyToAcct)
        assertEquals("9x1", reply.replyToId)
        assertEquals("trinity@remote.example", reply.acct)
    }
}
