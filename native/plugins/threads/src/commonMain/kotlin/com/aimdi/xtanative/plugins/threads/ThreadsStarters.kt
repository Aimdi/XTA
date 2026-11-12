package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsDiscovery
import com.aimdi.xtanative.networks.threads.ThreadsPerson
import com.aimdi.xtanative.networks.threads.ThreadsPost

/** Who an empty Home offers to follow: the public accounts every guest can read, then the authors For you showed. */
internal object ThreadsStarters {
    /** How many are offered. */
    const val CAP = 8

    fun people(forYou: List<ThreadsPost>, alreadyFollows: (String) -> Boolean): List<ThreadsPerson> {
        val starters = ThreadsDiscovery.STARTER_HANDLES.filterNot(alreadyFollows).map { ThreadsPerson(it, it, null, fromRepost = false) }
        val seen = starters.map { it.handle }.toSet()
        val authors = ThreadsDiscovery.peopleToFollow(forYou, alreadyFollows).filterNot { it.handle in seen }
        return (starters + authors).take(CAP)
    }
}
