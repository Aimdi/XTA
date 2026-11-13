package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser

/**
 * The posts and people already on screen, by id and by name: an opened post
 * or profile draws them at once while X is asked for the rest.
 */
class PostCache {
    private val posts = bounded<XPost>(2_000)
    private val users = bounded<XUser>(1_000)

    @Synchronized
    fun remember(threads: List<XThread>) {
        threads.forEach { thread -> thread.posts.forEach(::rememberPost) }
    }

    @Synchronized
    fun remember(post: XPost) = rememberPost(post)

    @Synchronized
    fun remember(user: XUser) {
        users[user.screenName.lowercase()] = user
    }

    @Synchronized
    fun post(id: String): XPost? = posts[id]

    @Synchronized
    fun user(screenName: String): XUser? = users[screenName.lowercase()]

    private fun rememberPost(post: XPost) {
        posts[post.id] = post
        users.putIfAbsent(post.author.screenName.lowercase(), post.author)
        post.quoted?.let(::rememberPost)
        post.reposted?.let(::rememberPost)
    }

    private companion object {
        fun <T> bounded(max: Int) = object : LinkedHashMap<String, T>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?): Boolean = size > max
        }
    }
}
