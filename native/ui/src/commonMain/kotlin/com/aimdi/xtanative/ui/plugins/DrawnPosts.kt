package com.aimdi.xtanative.ui.plugins

/**
 * The posts a plugin handed the app's shared rows lately, the newest
 * [capacity] of them by [keyOf]: what a row's tap, bookmark and menu name by
 * its id ([NetworkPostActions]), so the plugin finds the post itself again.
 */
class DrawnPosts<T : Any>(private val capacity: Int = 400, private val keyOf: (T) -> String) {
    private val posts = LinkedHashMap<String, T>()

    /** Keeps [post] as the newest, in place of one with its key; gives it back. */
    @Synchronized
    fun put(post: T): T {
        val key = keyOf(post)
        posts.remove(key)
        posts[key] = post
        while (posts.size > capacity) posts.remove(posts.keys.first())
        return post
    }

    /** The post kept under [key]; null for one never drawn, or drawn too long ago. */
    @Synchronized
    operator fun get(key: String): T? = posts[key]
}
