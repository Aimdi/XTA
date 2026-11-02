package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.backup.XtaBackup
import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What XTA Native keeps between launches: the X sessions, who is followed, and the feed's two switches. */
data class Session(
    val accounts: List<XAccount> = emptyList(),
    val following: List<FollowedAccount> = emptyList(),
    val includeReplies: Boolean = true,
    val includeReposts: Boolean = true,
) {
    /** The accounts the Following feed reads, as XTA's home feed picks them. */
    val feedMembers: List<FeedMember> get() = following.filter { it.inFeed }.map { it.member }

    /** The sessions and the follows of [backup]; the switches stay as they were. */
    fun importing(backup: XtaBackup): Session = copy(accounts = backup.accounts, following = backup.following)

    companion object {
        private const val VERSION = 1

        fun encode(session: Session): String = buildJsonObject {
            put("v", VERSION)
            put("includeReplies", session.includeReplies)
            put("includeReposts", session.includeReposts)
            put(
                "accounts",
                JsonArray(
                    session.accounts.map { account ->
                        buildJsonObject {
                            put("id", account.id)
                            account.screenName?.let { put("screenName", it) }
                            put("headers", JsonObject(account.headers.mapValues { JsonPrimitive(it.value) }))
                        }
                    },
                ),
            )
            put(
                "following",
                JsonArray(
                    session.following.map { followed ->
                        buildJsonObject {
                            put("id", followed.id)
                            put("screenName", followed.screenName)
                            put("name", followed.name)
                            followed.avatarUrl?.let { put("avatar", it) }
                            put("verified", followed.verified)
                            put("inFeed", followed.inFeed)
                            put("followedAt", followed.followedAt)
                        }
                    },
                ),
            )
        }.toString()

        /** The session in [text]; null when there is none to read. */
        fun decode(text: String): Session? {
            val root = J.parse(text)
            if (root["v"].int != VERSION) return null
            return Session(
                accounts = root["accounts"].list.mapNotNull { row ->
                    val id = row["id"].string ?: return@mapNotNull null
                    val headers = row["headers"].let { h -> h.keys.mapNotNull { key -> h[key].string?.let { key to it } }.toMap() }
                    XAccount(id, row["screenName"].string, headers)
                },
                following = root["following"].list.mapNotNull { row ->
                    val id = row["id"].string ?: return@mapNotNull null
                    val screenName = row["screenName"].string ?: return@mapNotNull null
                    FollowedAccount(
                        id = id,
                        screenName = screenName,
                        name = row["name"].string ?: screenName,
                        avatarUrl = row["avatar"].string,
                        verified = row["verified"].boolean ?: false,
                        inFeed = row["inFeed"].boolean ?: true,
                        followedAt = row["followedAt"].long ?: 0,
                    )
                },
                includeReplies = root["includeReplies"].boolean ?: true,
                includeReposts = root["includeReposts"].boolean ?: true,
            )
        }
    }
}
