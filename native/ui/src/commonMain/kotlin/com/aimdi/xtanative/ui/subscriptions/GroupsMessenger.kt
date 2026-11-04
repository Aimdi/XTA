package com.aimdi.xtanative.ui.subscriptions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A message a group screen leaves for the Subscriptions tab, which says it once it shows again. */
sealed interface GroupsMessage {
    /** The ungrouped sorting placed [count] accounts. */
    data class Placed(val count: Int) : GroupsMessage
}

/**
 * Where a screen that closes itself leaves what it has to say, as XTA's
 * snackbars outlive the route that showed them: the Subscriptions tab says it
 * and takes it.
 */
object GroupsMessenger {
    private val pending = MutableStateFlow<GroupsMessage?>(null)
    val message: StateFlow<GroupsMessage?> = pending.asStateFlow()

    fun leave(message: GroupsMessage) {
        pending.value = message
    }

    /** Takes [message] once it was said; a newer one stays. */
    fun said(message: GroupsMessage) {
        pending.compareAndSet(message, null)
    }
}
