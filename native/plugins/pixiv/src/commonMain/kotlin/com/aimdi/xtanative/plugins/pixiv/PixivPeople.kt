package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivAuthUser
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A creator followed on this device, as XTA keeps one among `plugin.pixiv.group_subscriptions`. Pixiv is never told. */
internal data class PixivFollow(val id: Long, val account: String, val name: String, val avatarUrl: String?, val createdAt: String) {
    val displayName: String get() = name.ifEmpty { account.ifEmpty { "$id" } }

    fun toUser(): PixivUser = PixivUser(id, name, account, avatarUrl = avatarUrl)
}

/**
 * The creators followed on this device, in the preference XTA keeps them in
 * (its group subscriptions: `id`, `screen_name`, `name`, `avatar_url`,
 * `created_at`), so XTA's backup brings them and takes them back. A row's
 * other fields go back out as they came.
 */
internal object PixivFollows {
    fun of(settings: Settings): List<PixivFollow> = rows(settings).mapNotNull(::followOf).distinctBy { it.id }

    fun isFollowed(settings: Settings, id: Long): Boolean = of(settings).any { it.id == id }

    /** [settings] with [user] followed last, or with their row brought up to date; [at] is when, as ISO-8601. */
    fun followed(settings: Settings, user: PixivUser, at: String): Settings {
        val rows = rows(settings)
        val old = rows.firstOrNull { followOf(it)?.id == user.id }
        val row = JsonObject(
            old.orEmpty() + mapOf(
                "id" to JsonPrimitive("${user.id}"),
                "screen_name" to JsonPrimitive(user.account),
                "name" to JsonPrimitive(user.name),
                "avatar_url" to (user.avatarUrl?.let(::JsonPrimitive) ?: JsonPrimitive(null as String?)),
                "created_at" to (old?.get("created_at") ?: JsonPrimitive(at)),
            ),
        )
        val next = if (old == null) rows + row else rows.map { if (it === old) row else it }
        return settings.with(PixivPrefs.groupSubscriptions, JsonArray(next).toString())
    }

    fun unfollowed(settings: Settings, id: Long): Settings =
        settings.with(PixivPrefs.groupSubscriptions, JsonArray(rows(settings).filterNot { followOf(it)?.id == id }).toString())

    private fun rows(settings: Settings): List<JsonObject> =
        (J.parse(settings[PixivPrefs.groupSubscriptions]).raw as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private fun followOf(row: JsonObject): PixivFollow? {
        val json = J(row)
        val id = json["id"].long?.takeIf { it > 0 } ?: return null
        return PixivFollow(
            id = id,
            account = json["screen_name"].string.orEmpty(),
            name = json["name"].string.orEmpty(),
            avatarUrl = json["avatar_url"].string?.ifEmpty { null },
            createdAt = json["created_at"].string.orEmpty(),
        )
    }
}

/** A Pixiv account signed in on this device, its refresh token with it: a secret, never backed up. */
internal data class PixivAccount(
    val userId: Long,
    val name: String,
    val account: String,
    val refreshToken: String,
    val avatarUrl: String? = null,
    val isPremium: Boolean = false,
) {
    val displayName: String get() = name.ifEmpty { account.ifEmpty { "$userId" } }

    override fun toString() = "PixivAccount(userId=$userId)"

    fun json(): JsonElement = buildJsonObject {
        put("userId", userId)
        put("name", name)
        put("account", account)
        put("avatar", avatarUrl.orEmpty())
        put("isPremium", isPremium)
        put("refreshToken", refreshToken)
    }

    companion object {
        fun of(user: PixivAuthUser, refreshToken: String) =
            PixivAccount(user.id, user.name, user.account, refreshToken, user.avatarUrl, user.isPremium)

        /** A stored account, or null without the id or token needed to use it. */
        fun parse(json: J): PixivAccount? {
            val id = json["userId"].long?.takeIf { it > 0 } ?: return null
            val token = json["refreshToken"].string?.trim()?.ifEmpty { null } ?: return null
            return PixivAccount(
                userId = id,
                name = json["name"].string.orEmpty(),
                account = json["account"].string.orEmpty(),
                refreshToken = token,
                avatarUrl = json["avatar"].string?.ifEmpty { null },
                isPremium = json["isPremium"].boolean == true,
            )
        }
    }
}

/**
 * Every Pixiv account signed in on this device (`plugin.pixiv.accounts`), as
 * XTA keeps them; the one in use is also in the single-account keys the client
 * reads ([PixivSettingsCredentials]). Each rule works on the settings as they
 * are stored, so a check that lands after a switch keeps nothing of the old.
 */
internal object PixivAccounts {
    fun of(settings: Settings): List<PixivAccount> =
        J.parse(settings[PixivPrefs.accounts]).list.mapNotNull(PixivAccount::parse).distinctBy { it.userId }

    fun active(settings: Settings): PixivAccount? = of(settings).firstOrNull { it.userId == settings[PixivPrefs.userId] }

    fun signedIn(settings: Settings): Boolean = settings[PixivPrefs.refreshToken].isNotBlank()

    /** [accounts] with [account] in its place, or added last when new. */
    fun with(accounts: List<PixivAccount>, account: PixivAccount): List<PixivAccount> =
        if (accounts.any { it.userId == account.userId }) accounts.map { if (it.userId == account.userId) account else it } else accounts + account

    /**
     * [settings] keeping [user], just signed in or confirmed by a token check,
     * with the token in use: only while the id in use is theirs, since every
     * way a token is set sets that id too, or clears it for a pasted one.
     */
    fun remembered(settings: Settings, user: PixivAuthUser): Settings {
        val token = settings[PixivPrefs.refreshToken].trim()
        if (user.id <= 0 || user.id != settings[PixivPrefs.userId] || token.isEmpty()) return settings
        return stored(settings, with(of(settings), PixivAccount.of(user, token)))
    }

    /**
     * [settings] keeping the account in use among the stored ones before
     * another takes its place: the token now in use goes into its entry, since
     * Pixiv may have rotated it, and an account never confirmed joins by id.
     */
    fun keepingActive(settings: Settings): Settings {
        val id = settings[PixivPrefs.userId]
        val token = settings[PixivPrefs.refreshToken].trim()
        if (id <= 0 || token.isEmpty()) return settings
        val active = of(settings).firstOrNull { it.userId == id }
        if (active?.refreshToken == token) return settings
        val kept = active?.copy(refreshToken = token) ?: PixivAccount(id, "", "", token, isPremium = settings[PixivPrefs.isPremium])
        return stored(settings, with(of(settings), kept))
    }

    fun without(settings: Settings, userId: Long): Settings = stored(settings, of(settings).filterNot { it.userId == userId })

    private fun stored(settings: Settings, accounts: List<PixivAccount>): Settings =
        settings.with(PixivPrefs.accounts, JsonArray(accounts.map { it.json() }).toString())
}
