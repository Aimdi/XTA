package com.aimdi.xtanative.store.backup

import kotlin.test.Test
import kotlin.test.assertEquals

/** The fixture is an XTA backup whose groups hold every network's accounts as XTA writes them, and a group from before XTA's migration 62. */
class NetworkMembersTest {
    private val backup = BackupDocument.parse(checkNotNull(javaClass.getResource("/backup/xta_network_groups.json")).readText())!!
    private val accounts = NetworkMembers.accounts(backup)
    private val claimed = backup.subscriptions!!.map { it.id }.toSet() + backup.searchSubscriptions!!.map { it.query }

    private fun membersOf(group: String): List<String> = backup.groupMembers!!.filter { it.groupId == group }.map { it.profileId }

    @Test
    fun theAccountsAreTheRowsOfTheNetworksXtasGroupsNameByTheirIdAlone() {
        assertEquals(
            listOf(
                "threads:reader", "bluesky:reader.bsky.social", "bluesky:did:plc:maya", "mastodon:reader@example.social",
                "reddit:dartlang", "substack:astral", "rss:feed-1", "booru:landscape", "booru:sky",
            ),
            accounts.map { it.memberId },
        )
    }

    @Test
    fun xtasBareMembersBecomeThePluginsAndEveryOtherStaysAsXtaWroteIt() {
        assertEquals(
            listOf(
                "1", "threads:reader", "bluesky:reader.bsky.social", "bluesky:did:plc:maya", "mastodon:reader@example.social",
                "reddit:dartlang", "substack:astral", "rss:feed-1", "booru:sky", "tiktok:dancer", "instagram:painter",
                "hackernews:author", "pixiv:123", "websources:lobsters/hottest", "AAPL", "dart",
            ),
            NetworkMembers.adopted(membersOf("g1"), accounts, claimed),
        )
        assertEquals(listOf("booru:landscape", "unfollowed.example"), NetworkMembers.adopted(membersOf("g2"), accounts, claimed), "a tag from before migration 62, as XTA prefixes it")
    }

    @Test
    fun anExportWritesThemBackAsXtaWritesThem() {
        val searches = backup.searchSubscriptions!!.map { it.query }.toSet()
        val exported = { group: String -> NetworkMembers.adopted(membersOf(group), accounts, claimed).map { NetworkMembers.xtaMemberId(it, emptyList(), searches) } }
        assertEquals(membersOf("g1"), exported("g1"))
        assertEquals(listOf("booru:landscape", "unfollowed.example"), exported("g2"))
    }

    @Test
    fun anAccountAPluginReadsByAnotherNameIsNamedSoBothWays() {
        val running = listOf(NetworkAccount("bluesky", "reader.bsky.social", "did:plc:reader"))
        val owners = NetworkMembers.named(accounts, running)
        assertEquals(listOf("1", "bluesky:did:plc:reader"), NetworkMembers.adopted(listOf("1", "reader.bsky.social"), owners, claimed))
        assertEquals("reader.bsky.social", NetworkMembers.xtaMemberId("bluesky:did:plc:reader", running))
        assertEquals("did:plc:other", NetworkMembers.xtaMemberId("bluesky:did:plc:other", running), "without a name of its own, the account is the id")
    }

    @Test
    fun aBareMemberXOrAnotherNetworkKeepsTooIsEachOfThem() {
        val owners = listOf(NetworkAccount("threads", "alice"), NetworkAccount("mastodon", "alice"), NetworkAccount("reddit", "dart"))
        assertEquals(listOf("dart", "reddit:dart"), NetworkMembers.adopted(listOf("dart"), owners, claimed = setOf("dart")))
        assertEquals(listOf("threads:alice", "mastodon:alice"), NetworkMembers.adopted(listOf("alice", "alice"), owners, emptySet()))
        val twice = listOf(NetworkAccount("threads", "a", "first"), NetworkAccount("threads", "a", "second"))
        assertEquals(listOf("threads:first"), NetworkMembers.adopted(listOf("a"), twice, emptySet()), "the first a plugin keeps an id as counts")
        assertEquals(listOf("dancer"), NetworkMembers.adopted(listOf("dancer"), listOf(NetworkAccount("tiktok", "dancer")), emptySet()), "XTA never groups a TikTok account bare")
    }

    @Test
    fun aMemberThatIsNoBareNetworksAccountGoesOutAsItIs() {
        assertEquals("threads:x", NetworkMembers.xtaMemberId("threads:x", emptyList(), searches = setOf("threads:x")), "a saved search with a colon")
        assertEquals("from:someone", NetworkMembers.xtaMemberId("from:someone", emptyList()))
        assertEquals("threads:", NetworkMembers.xtaMemberId("threads:", emptyList()))
        assertEquals("pixiv:123", NetworkMembers.xtaMemberId("pixiv:123", emptyList()))
    }

    @Test
    fun xtasListNamesABareNetworksAccountByItsIdAndTheOthersAsTheirGroupsDo() {
        assertEquals("alice", NetworkMembers.xtaId(NetworkAccount("threads", "alice")))
        assertEquals("reader.bsky.social", NetworkMembers.xtaId(NetworkAccount("bluesky", "reader.bsky.social", "did:plc:reader")))
        assertEquals("tiktok:dancer", NetworkMembers.xtaId(NetworkAccount("tiktok", "dancer")))
        assertEquals("booru:sky", NetworkMembers.xtaId(NetworkAccount("booru", "sky")))
    }
}
