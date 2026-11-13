package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser

/** The two reads Discover makes of X, so its view model can be tested without the network. */
interface DiscoveryXReads {
    suspend fun posts(memberId: String): List<XThread>

    suspend fun following(memberId: String): List<XUser>
}

internal fun XClient.discoveryXReads(): DiscoveryXReads {
    val client = this
    return object : DiscoveryXReads {
        override suspend fun posts(memberId: String) = client.userPosts(memberId, count = Discovery.POSTS_PAGE).threads
        override suspend fun following(memberId: String) = client.following(memberId, null, Discovery.FOLLOWS_PAGE).users
    }
}
