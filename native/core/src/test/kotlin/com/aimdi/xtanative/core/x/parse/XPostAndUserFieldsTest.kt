package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XAffiliation
import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XGrokShare
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTranslation
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVerifiedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The fields XTA reads from a post and an account beyond the basics
 * (tweet_models.dart, user.dart, user_verification.dart).
 */
class XPostAndUserFieldsTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/x/$name")!!.readText())

    private fun post(extra: String, legacy: String = "") = XParser.post(
        J.parse(
            """{"rest_id":"5","core":{"user_results":{"result":{"rest_id":"u","core":{"screen_name":"someone","name":"S"}}}},$extra
               "legacy":{"id_str":"5","full_text":"text"$legacy}}""",
        ),
    )!!

    @Test
    fun aCommunityNoteKeepsItsTextItsLinksInCodePointsAndItsHeading() {
        val note = post(
            """"birdwatch_pivot":{"title":"Readers added context","destinationUrl":"https://x.com/i/birdwatch/n/1",
                "subtitle":{"text":"😀 See https://example.com for more","entities":[{"fromIndex":7,"toIndex":26,"ref":{"url":"https://example.com"}},{"fromIndex":40,"toIndex":90,"ref":{"url":"https://broken"}}]}},""",
        ).note!!
        assertEquals("😀 See https://example.com for more", note.text)
        assertEquals(listOf(XEntity.Link(6, 25, "https://example.com", "https://example.com", "https://example.com")), note.links, "an entity past the text is dropped")
        assertEquals("Readers added context", note.title)
        assertEquals(listOf("😀 See ", "https://example.com", " for more"), XText.note(note).map { it.text })
    }

    @Test
    fun anArticleIsReadForItsTitleSummaryAndCover() {
        val article = post(
            """"article":{"article_results":{"result":{"rest_id":"9","title":"Long read","preview_text":"It starts","cover_media":{"media_info":{"original_img_url":"https://pbs.twimg.com/cover.jpg"}},"content_state":{"blocks":[]}}}},""",
        ).article!!
        assertEquals("Long read" to "It starts", article.title to article.previewText)
        assertEquals("https://pbs.twimg.com/cover.jpg", article.coverUrl)
        assertTrue(article.content["content_state"].exists, "its body is kept for the article reader")
        assertEquals("Older shape", post(""""article":{"article":{"title":"Older shape"}},""").article?.title)
    }

    @Test
    fun aPostsLanguageSensitivityReplyAndCommunityAreRead() {
        val post = post(
            """"community_results":{"result":{"__typename":"Community","id_str":"77"}},""",
            ""","lang":"fr","possibly_sensitive":true,"in_reply_to_status_id_str":"4","in_reply_to_screen_name":"other"""",
        )
        assertEquals("fr", post.lang)
        assertTrue(post.sensitive)
        assertEquals("4" to "other", post.replyToPostId to post.replyToScreenName)
        assertEquals("77", post.communityId)
        val back = XCodec.decode(XCodec.encode(listOf(XThread(post.id, listOf(post))))).single().posts.single()
        assertEquals(post.copy(author = back.author), back, "the disk cache keeps them")
    }

    @Test
    fun aQuoteXWouldNotGiveStillNamesThePostAndItsAuthor() {
        val (_, second) = XParser.timeline(fixture("user_originals_page.json")).threads.map { it.posts.single() }
        assertNull(second.quoted)
        val gap = second.quotedWithheld!!
        assertEquals("2095934459606376826", gap.id)
        assertEquals("quax_tests", gap.screenName, "from the link X keeps, which is what finding it elsewhere takes")
        val tombstone = post(
            """"quoted_status_result":{"result":{"__typename":"TweetTombstone","tombstone":{"text":{"text":"This Post is unavailable. Learn more"}}}},""",
            ""","is_quote_status":true,"quoted_status_id_str":"3"""",
        ).quotedWithheld!!
        assertEquals("3" to "This Post is unavailable.", tombstone.id to tombstone.reason)
        assertNull(post("").quotedWithheld, "a post that quotes nothing")
    }

    @Test
    fun theChecksBadgesAndRelationshipAreReadWhereverXPutThem() {
        fun user(json: String) = XParser.user(J.parse("""{"rest_id":"1","core":{"screen_name":"a","name":"A"},$json}"""))!!
        assertEquals(XVerifiedType.BUSINESS, user(""""verification":{"verified_type":"Business"},"is_blue_verified":false""").verifiedType)
        assertEquals(XVerifiedType.GOVERNMENT, user(""""legacy":{"verified_type":"Government"}""").verifiedType)
        assertEquals(XVerifiedType.BLUE, user(""""legacy":{"verified":true},"is_blue_verified":false""").verifiedType, "a legacy check is drawn blue")
        assertEquals(XVerifiedType.NONE, user(""""is_blue_verified":false""").verifiedType)
        assertTrue(user(""""verification":{"verified_type":"Business"}""").verified)

        val badge = """{"label":{"badge":{"url":"https://pbs.twimg.com/badge.png"},"description":"Org","url":{"url":"https://x.com/org"},"userLabelType":"BusinessLabel"}}"""
        assertEquals(XAffiliation("https://pbs.twimg.com/badge.png", "Org", "https://x.com/org", "BusinessLabel"), user(""""affiliates_highlighted_label":$badge""").affiliation)
        assertNull(user(""""affiliates_highlighted_label":${badge.replace("BusinessLabel", "AutomatedLabel")}""").affiliation, "the bot label is no badge")

        val hidden = user(""""privacy":{"protected":true},"relationship_perspectives":{"following":false},"action_counts":{"favorites_count":12}""")
        assertTrue(hidden.postsHidden, "X sends none of a protected account's posts to one that does not follow it")
        assertEquals(12L, hidden.likes)
        assertTrue(!user(""""privacy":{"protected":true},"relationship_perspectives":{"following":true}""").postsHidden)
    }

    @Test
    fun pinnedPostsComeFromPinnedItemsElseFromLegacy() {
        val modern = fixture("user_modern.json")["data"]["user"]["result"].raw as JsonObject
        val pinned = JsonObject(modern + ("pinned_items" to J.parse("""{"tweet_ids_str":["2095951992489152745"]}""").raw!!))
        assertEquals(listOf("2095951992489152745"), XParser.profile(J(JsonObject(mapOf("data" to JsonObject(mapOf("user" to JsonObject(mapOf("result" to pinned))))))))!!.second)
        assertTrue(XParser.profile(fixture("user_modern.json"))!!.second.isEmpty(), "pinned_items is {} when nothing is pinned")
        val legacy = J.parse("""{"rest_id":"1","legacy":{"screen_name":"a","pinned_tweet_ids_str":["1"]}}""")
        assertEquals(listOf("1"), XParser.pinnedIdsOf(legacy), "older results pin from legacy")
    }

    @Test
    fun theTextLeavesOutLinksShownUnderItAndKeepsTheRest() {
        fun link(start: Int, end: Int, expanded: String) = XEntity.Link(start, end, "https://t.co/$start", expanded, expanded.removePrefix("https://"))
        val text = "Live now\n\nhttps://t.co/1"
        val live = XPost("1", XUser("9", "a", "A"), null, text, entities = listOf(link(10, 24, "https://x.com/i/broadcasts/1ZkJzbdvLgRJv")))
        assertEquals(listOf("Live now"), XText.runs(live).map { it.text }, "no line break is left before a broadcast shown under the text")
        val article = live.copy(text = "Read https://t.co/5", entities = listOf(link(5, 19, "https://x.com/i/article/123")))
        assertEquals(listOf("Read"), XText.runs(article).map { it.text })

        val share = "Look https://t.co/5 at this"
        val grok = XPost("1", XUser("9", "a", "A"), null, share, entities = listOf(link(5, 19, "https://x.com/i/grok/share/abc123")))
        assertEquals("Look x.com/i/grok/share/abc123 at this", XText.plain(grok), "a Grok link the card does not show stays")
        assertEquals("Look  at this", XText.plain(grok.copy(grokShare = XGrokShare("https://x.com/i/grok/share/abc123", "q", "a"))))
    }

    @Test
    fun aTranslationIsDrawnLikeAPost() {
        val translation = XParser.translation(J.parse("""{"result":{"text":"Hallo @dart","entities":{"user_mentions":[{"screen_name":"dart","indices":[6,11]}]}}}"""))!!
        assertEquals(XTranslation("Hallo @dart", listOf(XEntity.Mention(6, 11, "dart"))), translation)
        assertEquals(listOf("Hallo ", "@dart"), XText.translation(translation).map { it.text })
        assertNull(XParser.translation(J.parse("""{"result":{}}""")))
    }

    @Test
    fun aPostSurvivesLosingOrReshapingAnySingleField() {
        val result = fixture("tweet_result.json").raw as JsonObject
        val legacy = result.getValue("legacy") as JsonObject
        for (key in result.keys + legacy.keys.map { "legacy.$it" }) {
            for (value in listOf(null, JsonPrimitive("reshaped"))) {
                val field = key.removePrefix("legacy.")
                fun changed(map: JsonObject) = JsonObject(if (value == null) map - field else map + (field to value))
                val mangled = if (key.startsWith("legacy.")) JsonObject(result + ("legacy" to changed(legacy))) else changed(result)
                XParser.post(J(mangled))
            }
        }
    }
}
