package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCommunity
import com.aimdi.xtanative.core.x.model.XCommunityPage
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUserPage
import java.time.Instant

/**
 * X communities as their GraphQL answers describe them (XTA's
 * community_models.dart): a community's header, its posts, its members, and
 * community search.
 */
object XCommunityParser {
    /** A `Community` result, wherever X nests one; null for anything else. */
    fun community(result: J): XCommunity? {
        val id = result["rest_id"].string ?: result["id_str"].string
        if (result["__typename"].string != "Community" || id == null) return null
        return XCommunity(
            id = id,
            name = result["name"].string.orEmpty(),
            description = result["description"].string,
            memberCount = result["member_count"].long ?: 0,
            // The community's own banner wins over X's default one.
            bannerUrl = result["custom_banner_media"]["media_info"]["original_img_url"].string
                ?: result["default_banner_media"]["media_info"]["original_img_url"].string,
            topic = result["primary_community_topic"]["topic_name"].string,
            createdAt = result["created_at"].long?.let(Instant::ofEpochMilli),
            rules = result["rules"].list.mapNotNull { it["name"].string },
            memberAvatarUrls = result["members_facepile_results"].list.mapNotNull { it["result"]["avatar"]["image_url"].string },
        )
    }

    /** A `CommunityQuery` answer; null when X sent no community. */
    fun header(body: J): XCommunity? = community(body["data"]["communityResults"]["result"])

    /** A `CommunityTweetsTimeline` answer: the Top tab, its pinned post marked, chained by conversation as XTA chains it. */
    fun posts(body: J): XTimelinePage =
        XParser.conversations(XParser.timelineOf(body["data"]["communityResults"]["result"]["ranked_community_timeline"]["timeline"]["instructions"]))

    /** One page of `membersSliceTimeline_Query`. A member without a handle could not be opened, so it is left out. */
    fun members(body: J): XUserPage {
        val slice = body["data"]["communityResults"]["result"]["members_slice"]
        val users = slice["items_results"].list
            .map { it["result"] }
            .filter { it["rest_id"].exists && it["core"]["screen_name"].exists }
            .mapNotNull(XParser::user)
        return XUserPage(users, slice["slice_info"]["next_cursor"].string)
    }

    /** One page of `CommunitiesSearchQuery`. */
    fun search(body: J): XCommunityPage {
        val slice = body["data"]["communities_search_slice"]
        return XCommunityPage(slice["items_results"].list.mapNotNull { community(it["result"]) }, slice["slice_info"]["next_cursor"].string)
    }
}
