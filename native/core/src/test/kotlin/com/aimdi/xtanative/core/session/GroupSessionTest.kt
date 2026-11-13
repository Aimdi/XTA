package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.feed.FeedRules
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What a follow and a group keep, and what each group's feed reads: XTA's group model, group tree, home group filter and feed defaults, for the native port. */
class GroupSessionTest {
    private fun account(id: String, followedAt: Long, inFeed: Boolean = true) =
        FollowedAccount(id, id, id.uppercase(), "https://pbs.twimg.com/profile_images/1/${id}_normal.jpg", verified = false, inFeed = inFeed, followedAt = followedAt)

    /**
     * news ─ tech ─ ai;  friends;  art (switched off the home feeds) ─ nsfw
     */
    private val session = Session(
        following = listOf(account("nasa", 3), account("esa", 1, inFeed = false), account("openai", 2), account("anna", 4), account("bob", 5)),
        groups = listOf(
            FollowGroup("news", "News", listOf("nasa", "list:9")),
            FollowGroup("tech", "Tech", listOf("esa")),
            FollowGroup("ai", "AI", listOf("openai", "ai art")),
            FollowGroup("friends", "Friends", listOf("anna")),
            FollowGroup("art", "Art", listOf("bob")),
            FollowGroup("nsfw", "NSFW", listOf("anna")),
            FollowGroup("searchOnly", "Searches", listOf("ai art")),
        ),
        searches = listOf(SavedSearch("list:9", 6), SavedSearch("ai art", 0)),
        nesting = mapOf("tech" to "news", "ai" to "tech", "nsfw" to "art"),
        offFollowing = setOf("art"),
    )

    private fun names(members: List<FeedMember>) = members.map { it.id }

    @Test
    fun aGroupReadsEverythingNestedInItAndItsSavedSearches() {
        assertEquals(listOf("nasa", "esa", "openai", "list:9", "ai art"), names(session.membersOf("news")))
        assertEquals(listOf("esa", "openai", "ai art"), names(session.membersOf("tech")))
        assertTrue(session.membersOf("news").single { it.id == "ai art" }.search)
        assertEquals(listOf("ai art"), names(session.membersOf("searchOnly")))
    }

    @Test
    fun groupsReadTogetherBringWhatIsNestedInEach() {
        assertEquals(listOf("anna", "bob"), names(session.membersOf("friends", alsoRead = setOf("art"))))
        assertEquals(listOf("anna"), names(session.membersOf("friends", alsoRead = setOf("friends"))))
    }

    @Test
    fun theFollowingFeedLeavesOutGroupsSwitchedOffItAndEverySavedSearch() {
        assertEquals(setOf("bob", "anna"), session.excludedFromFollowing, "turning off a parent hides its nested groups too")
        assertEquals(listOf("nasa", "openai"), names(session.feedMembers))
        assertEquals(listOf("anna"), names(session.membersOf("friends")), "a group still reads its own members")
        assertEquals(listOf("nasa", "openai", "anna", "bob"), names(session.withOffFollowing("art", false).feedMembers))
    }

    @Test
    fun aGroupWithOnlyASavedSearchOrOnlyNestedMembersHasSomethingToRead() {
        val parentOnly = session.withGroupMembers("news", emptyList())
        assertTrue(parentOnly.feedGroups.any { it.id == "news" })
        assertTrue(session.feedGroups.any { it.id == "searchOnly" })
        assertTrue(session.withNewGroup("empty", "Empty", emptyList()).feedGroups.none { it.id == "empty" })
    }

    @Test
    fun aFeedsOwnChoicesOverrideTheSessionsAndItsRulesApplyWhateverTheSort() {
        val rules = FeedRules(minLikes = 10, mutedKeywords = listOf(MutedKeyword("spoilers")))
        val custom = session.copy(includeReplies = false, includeReposts = true)
            .withSettings("news", FeedSettings(includeReposts = false, sort = FeedSort.RECENT, rules = rules, catchUp = true))
            .withPostCap("nasa", 2)
            .withPostCap("anna", 1)
        val plan = custom.feedPlan("news")
        assertEquals("news", plan.key)
        assertEquals(false, plan.includeReplies, "unset follows the session")
        assertEquals(false, plan.includeReposts)
        assertEquals(rules, plan.shaping.rules, "sorting by recent leaves the mute list on")
        assertEquals(mapOf("nasa" to 2), plan.shaping.postCaps, "caps of the group's own members only")
        assertTrue(plan.catchUp)

        val following = custom.withSettings(FeedKeys.FOLLOWING, FeedSettings(sort = FeedSort.POPULAR)).feedPlan(null)
        assertEquals(FeedKeys.FOLLOWING, following.key)
        assertTrue(following.popular)
        assertEquals(listOf("nasa", "openai"), names(following.members))
    }

    @Test
    fun aNewGroupFollowsTheSessionsSwitchesAndDefaultSettingsAreNotKept() {
        val made = session.withNewGroup("japan", "  Japan ", listOf("nasa", "nasa"))
        assertEquals(FollowGroup("japan", "Japan", listOf("nasa")), made.groups.last())
        assertEquals(FeedSettings(), made.settingsOf("japan"))
        assertEquals(made.includeReplies, made.feedPlan("japan").includeReplies)
        assertEquals(made, made.withSettings("japan", FeedSettings()))
    }

    @Test
    fun ownChoicesAreClearedOnlyWhenAskedAndTheFollowingFeedIsNoGroup() {
        val chosen = session
            .withSettings("news", FeedSettings(includeReplies = true, includeReposts = false))
            .withSettings("tech", FeedSettings(includeReplies = false, catchUp = true))
            .withSettings(FeedKeys.FOLLOWING, FeedSettings(includeReplies = true))
        assertEquals(OwnChoices(replies = 2, reposts = 1), chosen.ownChoices)
        assertEquals(chosen.feeds, chosen.copy(includeReplies = false).feeds, "turning the switch leaves every feed's own choice")

        val cleared = chosen.withoutOwnChoices(replies = true)
        assertEquals(OwnChoices(replies = 0, reposts = 1), cleared.ownChoices)
        assertEquals(FeedSettings(catchUp = true), cleared.settingsOf("tech"), "the rest of a feed's settings stay")
        assertNull(cleared.feeds[FeedKeys.FOLLOWING], "the Following feed follows the switch again too")
        assertEquals(false, cleared.settingsOf("news").includeReposts, "the repost choice was not the one cleared")
    }

    @Test
    fun nestingThatWouldPutAGroupInsideItselfIsRefused() {
        assertNull(session.nestedIn("news", "news"))
        assertNull(session.nestedIn("news", "ai"), "ai already sits under news")
        assertEquals("sport", assertNotNull(session.nestedIn("friends", "sport")).nesting["friends"])
        assertNull(session.nestedIn("tech", null)!!.nesting["tech"])
    }

    @Test
    fun deletingAGroupLiftsWhatIsNestedInItAndForgetsItsSettings() {
        val gone = session.withSettings("tech", FeedSettings(catchUp = true)).toggledDeck("tech").withOffFollowing("tech", true).withoutGroup("tech")
        assertTrue(gone.groups.none { it.id == "tech" })
        assertNull(gone.nesting["ai"], "ai comes back to the top rather than vanishing")
        assertTrue("tech" !in gone.feeds && "tech" !in gone.deck && "tech" !in gone.offFollowing)
    }

    @Test
    fun mergingMovesEveryMemberOnceAndDropsTheSource() {
        val merged = session.merging("friends", "nsfw")
        assertTrue(merged.groups.none { it.id == "friends" })
        assertEquals(listOf("anna"), merged.groups.single { it.id == "nsfw" }.memberIds)
        assertEquals(session, session.merging("friends", "friends"))
    }

    @Test
    fun membershipIsAddedKeepingOtherGroupsRemovedOnlyWhereAskedOrReplacedWhole() {
        val added = session.addingTo("anna", listOf("news", "friends"))
        assertEquals(listOf("nasa", "list:9", "anna"), added.groups.single { it.id == "news" }.memberIds)
        assertEquals(listOf("anna"), added.groups.single { it.id == "friends" }.memberIds, "a repeat is taken quietly")
        val removed = added.removingFrom("anna", listOf("news"))
        assertEquals(listOf("friends", "nsfw"), removed.groups.filter { "anna" in it.memberIds }.map { it.id })
        val exactly = removed.withMemberships("anna", listOf("art"))
        assertEquals(listOf("art"), exactly.groups.filter { "anna" in it.memberIds }.map { it.id })
    }

    @Test
    fun aSavedSearchIsFollowedOnceAndUnfollowedOutOfEveryGroup() {
        val followed = session.followSearch("  from:nasa filter:media ", at = 9)
        assertEquals(SavedSearch("from:nasa filter:media", 9), followed.followedSearch("from:nasa filter:media"))
        assertSame(followed, followed.followSearch("from:nasa filter:media", at = 10))
        assertSame(followed, followed.followSearch("   ", at = 10))
        val gone = session.unfollowSearch("ai art")
        assertNull(gone.followedSearch("ai art"))
        assertTrue(gone.groups.none { "ai art" in it.memberIds })
    }

    @Test
    fun anAccountsCapIsKeptUntilItIsUnfollowed() {
        val capped = session.withPostCap("nasa", 3)
        assertEquals(3, capped.membersOf("news").single { it.id == "nasa" }.maxPosts)
        assertEquals(emptyMap(), capped.withPostCap("nasa", 0).postCaps)
        assertEquals(emptyMap(), capped.unfollow("nasa").postCaps)
    }

    @Test
    fun theDeckShowsThePinnedGroupsThatStillExistInPinOrder() {
        val deck = session.toggledDeck("friends").toggledDeck("news").toggledDeck("gone")
        assertEquals(listOf("friends", "news"), deck.deckGroups.map { it.id })
        assertEquals(listOf("news", "gone"), deck.toggledDeck("friends").deck)
    }

    @Test
    fun anImportedListBecomesAGroupAndFollowsOnlyWhoIsNew() {
        val list = ImportedList("42", "Space agencies", listOf(XUser("esa", "esa", "European Space Agency"), XUser("jaxa", "jaxa", "JAXA")))
        val imported = session.importingList(list, groupId = "g42", at = 99)
        assertEquals(FollowGroup("g42", "Space agencies", listOf("esa", "jaxa")), imported.groups.last())
        assertEquals(FollowedAccount("jaxa", "jaxa", "JAXA", null, verified = false, inFeed = true, followedAt = 99), imported.followed("jaxa"))
        val esa = imported.followed("esa")!!
        assertEquals(1L to false, esa.followedAt to esa.inFeed, "an account already followed keeps its date and switch")
    }

    @Test
    fun aGroupLeavesAsAPackAndComesBackAsANewGroup() {
        val pack = session.packOf("news")!!
        assertEquals(GroupPack("News", listOf(PackMember("user", "nasa", "nasa"), PackMember("search", "list:9"))), pack)
        assertNull(session.packOf("nope"))

        val shared = GroupPack("Tech", listOf(PackMember("user", "783214", "X"), PackMember("search", "dart lang"), PackMember("reddit", "flutter"), PackMember("user", "nasa", "nasa")))
        val imported = Session().copy(following = listOf(account("nasa", 1))).importingPack(shared, groupId = "p1", at = 7)
        assertEquals(FollowGroup("p1", "Tech", listOf("783214", "dart lang", "nasa")), imported.groups.single())
        assertEquals(FollowedAccount("783214", "X", "X", null, verified = false, inFeed = true, followedAt = 7), imported.followed("783214"))
        assertEquals(listOf(SavedSearch("dart lang", 7)), imported.searches)
        assertEquals(account("nasa", 1), imported.followed("nasa"))
    }

    @Test
    fun whatXShowsOfAFollowedAccountReplacesItsHandleNameAndPictureOnly() {
        val renamed = session.withProfilesFrom(listOf(XUser("nasa", "NASA_official", "NASA")))
        val nasa = renamed.followed("nasa")!!
        assertEquals("NASA_official", nasa.screenName)
        assertEquals(session.followed("nasa")!!.avatarUrl, nasa.avatarUrl, "what X left out stays")
        assertEquals(3L to true, nasa.followedAt to nasa.inFeed)
        assertEquals(listOf("NASA_official", "openai"), session.withProfilesFrom(listOf(XUser("nasa", "NASA_official", "NASA"))).feedPlan(null).members.map { it.screenName })

        val sameBigger = XUser("nasa", "nasa", "NASA", avatarUrl = "https://pbs.twimg.com/profile_images/1/nasa_400x400.jpg")
        assertSame(session, session.withProfilesFrom(listOf(sameBigger, XUser("stranger", "x", "X"))), "the same picture at another size is no change")
    }
}
