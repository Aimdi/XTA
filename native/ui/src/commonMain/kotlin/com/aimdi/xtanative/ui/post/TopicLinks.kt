package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.ui.search.FollowTarget

/**
 * A hashtag in a post's text as a topic to follow (rich_text.dart's topic
 * sheet): following one saves the search for it, `#tag`, as XTA saves a
 * search subscription.
 */
internal object TopicLinks {
    /** The topic a link's [tag] names, with its `#`; null for a link that is no hashtag. */
    fun topicOf(tag: String?): String? = tag?.takeIf { it.length > 1 && it.startsWith('#') }

    fun isFollowed(session: Session, topic: String): Boolean = FollowTarget.Search(topic).state(session).followed

    /** [topic] followed from [at] (epoch milliseconds), or no longer, as XTA's sheet toggles it. */
    fun toggled(session: Session, topic: String, at: Long): Session = FollowTarget.Search(topic).toggled(session, at)
}
