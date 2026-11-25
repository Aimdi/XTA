package com.aimdi.xtanative.ui.chrome

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A double-tap opens a screen once. */
class NavRepeatsTest {
    @Test
    fun theSameScreenAgainAtOnceIsLetGo() {
        val repeats = NavRepeats()
        assertTrue(repeats.allows("post/1", "home", 1_000))
        repeats.pushed("post/1", "e1", 1_000)
        assertFalse(repeats.allows("post/1", "e1", 1_200), "the second tap of a double-tap")
    }

    @Test
    fun anotherScreenOpens() {
        val repeats = NavRepeats()
        repeats.pushed("post/1", "e1", 1_000)
        assertTrue(repeats.allows("profile/maya", "e1", 1_100))
    }

    @Test
    fun afterGoingBackOrAMomentItOpensAgain() {
        val repeats = NavRepeats()
        repeats.pushed("post/1", "e1", 1_000)
        assertTrue(repeats.allows("post/1", "home", 1_200), "back on the screen below")
        assertTrue(repeats.allows("post/1", "e1", 1_000 + NavRepeats.WINDOW_MS), "a moment later")
    }
}
