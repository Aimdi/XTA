package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PixivAuthUser
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.networks.pixiv.answer
import com.aimdi.xtanative.networks.pixiv.illustsJson
import com.aimdi.xtanative.networks.pixiv.tokenAnswer
import com.aimdi.xtanative.networks.pixiv.workJson
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.SecretPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray

/** XTA's pixiv_accounts_test: the accounts kept on this device, and switching between them. */
class PixivAccountsTest {
    private val mika = PixivAuthUser(1, "Mika", "mika", isPremium = true, avatarUrl = "https://i.pximg.net/a.jpg")
    private val haru = PixivAuthUser(2, "Haru", "haru")

    private fun stored(vararg accounts: Pair<PixivAuthUser, String>): String =
        JsonArray(accounts.map { (user, token) -> PixivAccount.of(user, token).json() }).toString()

    private fun signedIn(userId: Long, token: String, vararg accounts: Pair<PixivAuthUser, String>) = Settings()
        .with(PixivPrefs.refreshToken, token)
        .with(PixivPrefs.accessToken, "access-$token")
        .with(PixivPrefs.accessExpiresAt, "2999-01-01T00:00:00Z")
        .with(PixivPrefs.userId, userId)
        .with(PixivPrefs.accounts, stored(*accounts))

    private fun tokens(settings: Settings): Map<Long, String> = PixivAccounts.of(settings).associate { it.userId to it.refreshToken }

    @Test
    fun eachUserIsReadOnceAndEntriesWithoutAnIdOrTokenAreSkipped() {
        val raw = "[" + listOf(PixivAccount.of(mika, "m1").json(), PixivAccount.of(mika, "m2").json()).joinToString(",") +
            """,{"userId":3},{"refreshToken":"x"},"junk"]"""
        val accounts = PixivAccounts.of(Settings().with(PixivPrefs.accounts, raw))
        assertEquals(listOf("m1"), accounts.map { it.refreshToken })
        assertTrue(accounts.single().isPremium)
        assertEquals("https://i.pximg.net/a.jpg", accounts.single().avatarUrl)
        assertTrue(PixivAccounts.of(Settings().with(PixivPrefs.accounts, "{oops")).isEmpty())
        assertTrue(PixivAccounts.of(Settings()).isEmpty())
    }

    @Test
    fun anAccountIsReplacedInPlaceAndANewOneAddedLast() {
        val first = listOf(PixivAccount.of(mika, "m1"), PixivAccount.of(haru, "h1"))
        val updated = PixivAccounts.with(first, PixivAccount.of(mika, "m2"))
        assertEquals(listOf("m2", "h1"), updated.map { it.refreshToken })
        val settings = Settings().with(PixivPrefs.accounts, JsonArray(updated.map { it.json() }).toString())
        assertEquals(listOf(2L), PixivAccounts.of(PixivAccounts.without(settings, 1)).map { it.userId })
    }

    @Test
    fun switchingPutsTheStoredAccountInUseAndClearsTheAccessToken() = withRuntime(PixivTestHost(signedIn(2, "haru-token", mika to "mika-token", haru to "haru-token"))) { runtime ->
        PixivSession.switchTo(runtime, PixivAccounts.of(runtime.settings.now).first())
        val now = runtime.settings.now
        assertEquals("mika-token", now[PixivPrefs.refreshToken])
        assertEquals("", now[PixivPrefs.accessToken])
        assertEquals("", now[PixivPrefs.accessExpiresAt])
        assertEquals(1L, now[PixivPrefs.userId])
        assertTrue(now[PixivPrefs.isPremium])
        assertEquals("Mika", PixivAccounts.active(now)?.name)
    }

    @Test
    fun aTokenPixivRotatedSinceStoringIsKeptWhenSwitchingAway() = withRuntime(PixivTestHost(signedIn(1, "rotated", mika to "old", haru to "haru"))) { runtime ->
        PixivSession.switchTo(runtime, PixivAccounts.of(runtime.settings.now).last())
        assertEquals("rotated", PixivAccounts.of(runtime.settings.now).first().refreshToken)
        assertEquals("haru", runtime.settings[PixivPrefs.refreshToken])
    }

    @Test
    fun signingOutForgetsOnlyTheActiveAccountAndHandsOverToAnother() = withRuntime(PixivTestHost(signedIn(2, "haru", mika to "mika", haru to "haru"))) { runtime ->
        assertEquals(1L, PixivSession.signOut(runtime)?.userId)
        assertEquals(listOf(1L), PixivAccounts.of(runtime.settings.now).map { it.userId })
        assertEquals("mika", runtime.settings[PixivPrefs.refreshToken])

        assertNull(PixivSession.signOut(runtime))
        assertTrue(PixivAccounts.of(runtime.settings.now).isEmpty())
        assertEquals("", runtime.settings[PixivPrefs.refreshToken])
        assertFalse(runtime.signedIn)
    }

    @Test
    fun aTokenCheckAnsweredAfterASwitchReportsAndKeepsOnlyTheAccountNowInUse() {
        val sent = mutableListOf<String>()
        lateinit var switch: () -> Unit
        val http = FakePixivHttp { request ->
            val token = request.form.getValue("refresh_token")
            sent += token
            val user = if (token == "mika-token") mika else haru
            if (user == mika) switch()
            tokenAnswer("access-${user.account}", refresh = token, user = """{"id": "${user.id}", "name": "${user.name}", "account": "${user.account}"}""")
        }
        val start = signedIn(1, "mika-token", mika to "mika-token", haru to "haru-token").with(PixivPrefs.accessExpiresAt, "")
        withRuntime(PixivTestHost(start, http)) { runtime ->
            switch = { PixivSession.switchTo(runtime, PixivAccounts.of(runtime.settings.now).last()) }
            assertEquals("Haru", PixivSession.verified(runtime)?.name, "the old account's answer is not reported as the one in use")
            assertEquals(listOf("mika-token", "haru-token"), sent)
            val now = runtime.settings.now
            assertEquals(mapOf(1L to "mika-token", 2L to "haru-token"), tokens(now))
            assertEquals("haru-token", now[PixivPrefs.refreshToken])
            assertEquals("access-haru", now[PixivPrefs.accessToken])
            assertEquals(2L, now[PixivPrefs.userId])
        }
    }

    @Test
    fun anAccountNeverCheckedIsKeptByIdAndNamedByIt() {
        val kept = PixivAccounts.of(PixivAccounts.keepingActive(Settings().with(PixivPrefs.userId, 5L).with(PixivPrefs.refreshToken, "old-token"))).single()
        assertEquals(listOf<Any>(5L, "old-token", "5"), listOf(kept.userId, kept.refreshToken, kept.displayName))
    }

    @Test
    fun aTokenTypedInSettingsHasNoOwnerSoASwitchKeepsEveryStoredToken() = withRuntime(PixivTestHost(signedIn(1, "mika-token", mika to "mika-token", haru to "haru-token"))) { runtime ->
        PixivSession.useToken(runtime, "mistyped")
        assertEquals(0L, runtime.settings[PixivPrefs.userId])
        PixivSession.switchTo(runtime, PixivAccounts.of(runtime.settings.now).last())
        assertEquals(mapOf(1L to "mika-token", 2L to "haru-token"), tokens(runtime.settings.now))
        assertEquals("haru-token", runtime.settings[PixivPrefs.refreshToken])
    }

    @Test
    fun aTokenEnteredInSettingsIsASwitchTheLastAccountKeepsItsTokenAndItsMarksGo() = withRuntime(PixivTestHost(signedIn(1, "rotated-mika-token", mika to "mika-token"))) { runtime ->
        runtime.bookmarking.works.mark(123, true)
        PixivSession.useToken(runtime, "another-accounts-token")
        assertTrue(runtime.bookmarking.works.now.marked.isEmpty())
        assertEquals(mapOf(1L to "rotated-mika-token"), tokens(runtime.settings.now))
        assertEquals("another-accounts-token", runtime.settings[PixivPrefs.refreshToken])
        assertEquals(0L, runtime.settings[PixivPrefs.userId])
    }

    @Test
    fun aSwitchMadeAnywhereReloadsFavoritesForTheNewAccount() {
        val http = FakePixivHttp { request ->
            when {
                request.isTokenCall -> tokenAnswer("access-x", refresh = request.form.getValue("refresh_token"), user = null)
                else -> {
                    val user = request.query["user_id"]!!.toLong()
                    answer(illustsJson(workJson(user * 100)))
                }
            }
        }
        val start = signedIn(1, "mika-token", mika to "mika-token", haru to "haru-token").with(PixivPrefs.startSection, com.aimdi.xtanative.store.settings.PixivSection.FAVORITES)
        withRuntime(PixivTestHost(start, http)) { runtime ->
            val home = PixivHomeModel(runtime, runtime.scope)
            home.ensureShown()
            withTimeout(5_000) { home.bookmarks.state.first { it.items.isNotEmpty() } }
            assertEquals(listOf(100L), home.bookmarks.state.value.items.map { it.id })

            PixivSession.switchTo(runtime, PixivAccounts.of(runtime.settings.now).last())
            withTimeout(5_000) { home.bookmarks.state.first { state -> state.items.none { it.id == 100L } && state.items.isNotEmpty() } }
            assertEquals(listOf(200L), home.bookmarks.state.value.items.map { it.id }, "the last account's favourites are gone")
        }
    }

    @Test
    fun theAccountsAreASecretSoExportsLeaveThemOut() {
        assertTrue(SecretPrefs.isSecret(PixivPrefs.accounts.key))
        val settings = Settings().with(PixivPrefs.accounts, stored(mika to "token")).with(PixivPrefs.showR18, true)
        val exported = settings.exported(includeSettings = true, includeSubscriptions = true)!!
        assertFalse(PixivPrefs.accounts.key in exported)
        assertFalse("token" in exported.toString())
    }

    @Test
    fun whoseTheTokensAreAndTheSavedPagesStayOnTheDeviceBothWays() {
        val keys = listOf(PixivPrefs.userId, PixivPrefs.isPremium, PixivPrefs.accessExpiresAt, PixivPrefs.downloadIndex).map { it.key }
        keys.forEach { assertTrue(SecretPrefs.isSecret(it), it) }
        assertFalse(SecretPrefs.isSecret(PixivPrefs.showR18.key))
    }

    @Test
    fun restoringAnotherAccountsBackupLeavesTheAccountInUseAndEveryStoredTokenAlone() {
        val start = Settings().with(PixivPrefs.refreshToken, "haru-token").with(PixivPrefs.userId, 2L)
            .with(PixivPrefs.accounts, stored(mika to "mika-token", haru to "haru-token"))
        val backup = mapOf(
            PixivPrefs.userId.key to kotlinx.serialization.json.JsonPrimitive(1),
            PixivPrefs.isPremium.key to kotlinx.serialization.json.JsonPrimitive(true),
            PixivPrefs.showR18.key to kotlinx.serialization.json.JsonPrimitive(true),
        )
        val restored = start.restoring(backup)
        assertEquals(2L, restored[PixivPrefs.userId])
        assertTrue(restored[PixivPrefs.showR18])
        withRuntime(PixivTestHost(restored)) { runtime ->
            PixivSession.switchTo(runtime, PixivAccounts.of(runtime.settings.now).first { it.userId == 1L })
            assertEquals(mapOf(1L to "mika-token", 2L to "haru-token"), tokens(runtime.settings.now))
            assertEquals("mika-token", runtime.settings[PixivPrefs.refreshToken])
        }
    }

    @Test
    fun aSwitchDropsWhatWasLoadedForTheLastAccount() = withRuntime(PixivTestHost(signedIn(1, "mika-token", mika to "mika-token", haru to "haru-token"))) { runtime ->
        val painter = PixivUser(9, "Painter", "painter")
        runtime.following.marks.mark(painter.id, true)
        PixivSession.switchTo(runtime, PixivAccounts.of(runtime.settings.now).last())
        assertEquals("haru-token", runtime.settings[PixivPrefs.refreshToken])
        assertEquals("", runtime.settings[PixivPrefs.accessToken])
        assertTrue(runtime.following.marks.now.marked.isEmpty(), "what was loaded for the last account is dropped")
        assertEquals("Haru", PixivAccounts.active(runtime.settings.now)?.displayName)
    }

    @Test
    fun removingAnAccountKeepsTheOthers() = withRuntime(PixivTestHost(signedIn(1, "mika-token", mika to "mika-token", haru to "haru-token"))) { runtime ->
        PixivSession.remove(runtime, PixivAccounts.of(runtime.settings.now).last())
        assertEquals(listOf(1L), tokens(runtime.settings.now).keys.toList())
    }
}
