package com.aimdi.xtanative.ui.profile

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser

/** The reads a profile makes of X, so its view model can be tested without the network (the feed's [com.aimdi.xtanative.core.feed.FeedReads]). */
internal interface ProfileReads {
    suspend fun profile(screenName: String): Pair<XUser, List<String>>

    suspend fun profileById(userId: String): Pair<XUser, List<String>>

    suspend fun userPosts(userId: String, cursor: String?): XTimelinePage

    suspend fun userReposts(userId: String, cursor: String?): XTimelinePage

    suspend fun userReplies(userId: String, cursor: String?): XTimelinePage

    suspend fun userMedia(userId: String, cursor: String?): XTimelinePage
}

internal fun XClient.profileReads(): ProfileReads {
    val client = this
    return object : ProfileReads {
        override suspend fun profile(screenName: String) = client.profile(screenName)
        override suspend fun profileById(userId: String) = client.profileById(userId)
        override suspend fun userPosts(userId: String, cursor: String?) = client.userPosts(userId, cursor)
        override suspend fun userReposts(userId: String, cursor: String?) = client.userReposts(userId, cursor)
        override suspend fun userReplies(userId: String, cursor: String?) = client.userReplies(userId, cursor)
        override suspend fun userMedia(userId: String, cursor: String?) = client.userMedia(userId, cursor)
    }
}
