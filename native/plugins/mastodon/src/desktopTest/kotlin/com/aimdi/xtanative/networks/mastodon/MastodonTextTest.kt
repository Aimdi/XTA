package com.aimdi.xtanative.networks.mastodon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A status's HTML to text with entities, with the rules of XTA's mastodon_models_test.dart and mastodon_reading_cards_test.dart. */
class MastodonTextTest {
    private val blobcat = MastodonEmoji("blobcat", "https://files.example/blobcat.png", "https://files.example/blobcat-still.png")

    @Test
    fun htmlBecomesPlainTextWithItsLineBreaks() {
        assertEquals("Hello \nthere", MastodonText.ofHtml("<p>Hello <br>there</p>"))
        assertEquals("First.\nSecond & third.", MastodonText.ofHtml("<p>First.</p><p>Second &amp; third.</p>"))
        assertEquals("a\n\nb", MastodonText.ofHtml("<p>a<br><br><br><br>b</p>"), "at most one empty line")
        assertEquals("", MastodonText.ofHtml(null))
        assertEquals("", MastodonText.ofHtml("  "))
    }

    @Test
    fun aMastodonLinkReadsAsItsWholeAddress() {
        val html = "<p><a href=\"https://example.com/a/very/long/path\"><span class=\"invisible\">https://</span>" +
            "<span class=\"ellipsis\">example.com/a/very/lo</span><span class=\"invisible\">ng/path</span></a></p>"
        assertEquals("https://example.com/a/very/long/path", MastodonText.ofHtml(html))
    }

    @Test
    fun mentionsAndTagsAreSplitAndALocalNameResolved() {
        val parts = MastodonText.parts("hi @alice see #Flutter now", listOf("alice@mastodon.social"))
        assertEquals(
            listOf(
                MastodonTextPart.Text("hi "),
                MastodonTextPart.Mention("@alice", "alice@mastodon.social"),
                MastodonTextPart.Text(" see "),
                MastodonTextPart.Tag("#Flutter", "Flutter"),
                MastodonTextPart.Text(" now"),
            ),
            parts,
        )
    }

    @Test
    fun addressesKeepFragmentsAndBalancedBracketsAndLoseTrailingPunctuation() {
        val text = "Try (https://example.org/a_(b)?q=1#part), then #Café, #東京! @Reader@social.example."
        val parts = MastodonText.parts(text)
        assertEquals(text, parts.joinToString("") { it.text })
        assertEquals("https://example.org/a_(b)?q=1#part", parts.filterIsInstance<MastodonTextPart.Link>().single().url)
        assertEquals(listOf("Café", "東京"), parts.filterIsInstance<MastodonTextPart.Tag>().map { it.name })
        assertEquals("reader@social.example", parts.filterIsInstance<MastodonTextPart.Mention>().single().acct)
    }

    @Test
    fun anEntityGluedToAWordStaysText() {
        val parts = MastodonText.parts("mail@example.org embedded#fragment @Reader #tag", listOf("reader@social.example"))
        assertEquals("reader@social.example", parts.filterIsInstance<MastodonTextPart.Mention>().single().acct)
        assertEquals("tag", parts.filterIsInstance<MastodonTextPart.Tag>().single().name)
    }

    @Test
    fun addressesWithoutAHostOrWithCredentialsAreNoLinks() {
        val parts = MastodonText.parts("https:///broken wordhttps://example.org https://reader:secret@example.org")
        assertTrue(parts.none { it is MastodonTextPart.Link })
    }

    @Test
    fun anEntityAfterALineBreakStartsAWord() {
        val parts = MastodonText.parts("line\n@alice\n#tag", listOf("alice@x.social"))
        assertEquals(listOf("alice@x.social"), parts.filterIsInstance<MastodonTextPart.Mention>().map { it.acct })
        assertEquals(listOf("tag"), parts.filterIsInstance<MastodonTextPart.Tag>().map { it.name })
    }

    @Test
    fun aMentionResolvesThroughTheStatusesMentions() {
        assertEquals("alice@mastodon.social", MastodonText.resolveMention("@Alice", listOf("Alice@Mastodon.Social")))
        assertEquals("alice@other.example", MastodonText.resolveMention("@Alice@Other.Example", listOf("alice@mastodon.social")))
        assertEquals("alice", MastodonText.resolveMention("@@alice", emptyList()), "unknown names stay bare for the instance to resolve")
        assertEquals("ab@three.example", MastodonText.resolveMention("@ab", listOf("a@one.example", "ab@three.example")))
    }

    @Test
    fun aShortcodeIsAnEmojiOnlyWhenTheStatusHasIt() {
        val parts = MastodonText.parts("so :blobcat: and :unknown: :blobcat:", emojis = listOf(blobcat))
        assertEquals(
            listOf(
                MastodonTextPart.Text("so "),
                MastodonTextPart.Emoji(":blobcat:", blobcat),
                MastodonTextPart.Text(" and :unknown: "),
                MastodonTextPart.Emoji(":blobcat:", blobcat),
            ),
            parts,
        )
    }

    @Test
    fun emojiAreNotLookedForInsideLinks() {
        val text = "https://example.org/:blobcat:/x and :blobcat:"
        val parts = MastodonText.parts(text, emojis = listOf(blobcat))
        assertEquals(text, parts.joinToString("") { it.text })
        assertEquals(1, parts.count { it is MastodonTextPart.Emoji })
        assertEquals("https://example.org/:blobcat:/x", parts.filterIsInstance<MastodonTextPart.Link>().single().url)
    }

    @Test
    fun anEmojiRightAfterATagLeavesTheTagWhole() {
        val parts = MastodonText.parts("#tag:blobcat::blobcat:", emojis = listOf(blobcat))
        assertEquals(
            listOf(MastodonTextPart.Tag("#tag", "tag"), MastodonTextPart.Emoji(":blobcat:", blobcat), MastodonTextPart.Emoji(":blobcat:", blobcat)),
            parts,
        )
    }

    @Test
    fun namesAndWarningsGetTheirEmojiToo() {
        val verified = MastodonEmoji("verified", "https://files.example/verified.png")
        assertEquals(
            listOf(MastodonTextPart.Text("Eugen "), MastodonTextPart.Emoji(":verified:", verified)),
            MastodonText.withEmoji("Eugen :verified:", listOf(verified)),
        )
        assertEquals(listOf(MastodonTextPart.Text("plain")), MastodonText.withEmoji("plain", listOf(verified)))
        assertTrue(MastodonText.withEmoji("", listOf(verified)).isEmpty())
    }

    @Test
    fun withoutEmojiThePartsAreXtasExactly() {
        val text = "@@double :blobcat: z@y #a#b"
        assertEquals(MastodonText.parts(text), MastodonText.parts(text, emojis = emptyList()))
        assertEquals(listOf("@", "@double", " :blobcat: z", "@y", " ", "#a", "#b"), MastodonText.parts(text).map { it.text })
    }
}
