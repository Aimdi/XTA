package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One member of a pack: an account (`user`, with its handle) or a saved search (`search`, its query as id). */
data class PackMember(val type: String, val id: String, val screenName: String? = null) {
    companion object {
        const val USER = "user"
        const val SEARCH = "search"
    }
}

/**
 * A group's members to hand to someone else, as XTA exports them
 * (lib/group/subscription_pack.dart): a name and who is in it, no sign-in.
 */
data class GroupPack(val name: String, val members: List<PackMember>) {
    companion object {
        const val FORMAT = "xta-pack"
        const val VERSION = 1

        fun encode(pack: GroupPack): String = buildJsonObject {
            put("format", FORMAT)
            put("v", VERSION)
            put("name", pack.name)
            put(
                "members",
                JsonArray(
                    pack.members.map { member ->
                        buildJsonObject {
                            put("type", member.type)
                            put("id", member.id)
                            member.screenName?.let { put("screen_name", it) }
                        }
                    },
                ),
            )
        }.toString()

        /** The pack in [text]; null when it is not one, or of another format or version. */
        fun decode(text: String): GroupPack? {
            val root = J.parse(text)
            if (root["format"].string != FORMAT || root["v"].int != VERSION) return null
            return GroupPack(
                name = root["name"].string.orEmpty(),
                members = root["members"].list.filter { it.raw is JsonObject }.map { row ->
                    PackMember(row["type"].string.orEmpty(), row["id"].string.orEmpty(), row["screen_name"].string)
                },
            )
        }
    }
}
