package com.aimdi.xtanative.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XtaBackupTest {
    private val file = """
        {
          "formatVersion": 2,
          "appVersion": "aimdi189",
          "accounts": [
            {"id": "ct0value", "screen_name": "maya", "auth_header": "{\"Cookie\":\"auth_token=a;ct0=ct0value\",\"authorization\":\"Bearer X\",\"x-csrf-token\":\"ct0value\"}"},
            {"id": "broken", "screen_name": "nobody", "auth_header": "{}"}
          ],
          "subscriptions": [
            {"id": "1", "screen_name": "nasa", "name": "NASA", "profile_image_url_https": "https://pbs.twimg.com/a_normal.jpg", "verified": 1, "created_at": "2024-03-01 10:00:00", "in_feed": 1},
            {"id": "2", "screen_name": "esa", "name": "ESA", "profile_image_url_https": null, "verified": 0, "created_at": "2023-01-01T08:00:00.000", "in_feed": 0},
            {"id": "1", "screen_name": "nasa", "name": "NASA again", "verified": 1, "created_at": "2024-03-01 10:00:00", "in_feed": 1}
          ],
          "subscriptionGroups": [{"id": "g1", "name": "Space", "icon": "rocket", "emoji": "🚀", "pinned": 1, "nsfw": 0}, {"id": "-1", "name": "Everyone"}],
          "subscriptionGroupMembers": [{"group_id": "g1", "profile_id": "1"}, {"group_id": "g1", "profile_id": "2"}],
          "tweets": [{"id": "5"}]
        }
    """.trimIndent()

    @Test
    fun theSessionsAndTheAccountsFollowedAreRead() {
        val backup = XtaBackup.parse(file)!!
        val account = backup.accounts.single()
        assertEquals("ct0value", account.id)
        assertEquals("maya", account.screenName)
        assertEquals("auth_token=a;ct0=ct0value", account.cookie)
        assertEquals("ct0value", account.headers["x-csrf-token"])

        assertEquals(listOf("nasa", "esa"), backup.following.map { it.screenName })
        val (nasa, esa) = backup.following
        assertTrue(nasa.verified && nasa.inFeed)
        assertTrue(!esa.verified && !esa.inFeed)
        assertTrue(esa.followedAt < nasa.followedAt, "both date formats XTA wrote are read")

        assertEquals(
            listOf(FollowGroup("g1", "Space", listOf("1", "2"), emoji = "🚀", pinned = true, icon = "rocket")),
            backup.groups,
            "XTA's own everyone row is no group",
        )
    }

    @Test
    fun aGroupKeepsItsColourMarkDateAndPlace() {
        val backup = XtaBackup.parse(
            """
            {"formatVersion": 2, "subscriptionGroups": [
              {"id": "g1", "name": "Art", "icon": "{\"pack\":\"custom\",\"key\":\"palette\"}", "color": 4294198070,
               "mark_style": 2, "created_at": "2024-03-01 10:00:00", "position": 3},
              {"id": "g2", "name": "Plain", "color": null}
            ]}
            """.trimIndent(),
        )!!
        val (art, plain) = backup.groups
        assertEquals(4294198070, art.color)
        assertEquals("""{"pack":"custom","key":"palette"}""", art.icon)
        assertEquals(2, art.markStyle)
        assertEquals(XtaBackup.millisOf("2024-03-01 10:00:00"), art.createdAt)
        assertEquals(3, art.position)
        assertEquals(FollowGroup("g2", "Plain", emptyList()), plain, "what a row leaves out reads as XTA's defaults")
    }

    @Test
    fun anAccountSignedInWithoutACookieIsKeptAsXtaKeepsIt() {
        val backup = XtaBackup.parse("""{"accounts": [{"id": "oauth", "auth_header": "{\"authorization\":\"Bearer B\"}"}, {"id": "empty", "auth_header": "{}"}]}""")!!
        val account = backup.accounts.single()
        assertEquals("oauth", account.id)
        assertNull(account.cookie)
        assertEquals("Bearer B", account.headers["authorization"])
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() {
        assertNull(XtaBackup.parse("not json"))
        assertNull(XtaBackup.parse("""{"hello": "world"}"""))
    }

    @Test
    fun aBackupWithoutAccountsStillGivesTheFollowing() {
        val backup = XtaBackup.parse("""{"formatVersion": 2, "subscriptions": [{"id": "1", "screen_name": "nasa", "name": "NASA"}]}""")!!
        assertTrue(backup.accounts.isEmpty())
        assertEquals("nasa", backup.following.single().screenName)
        assertTrue(backup.following.single().inFeed, "a row without in_feed is shown in the feed")
    }
}
