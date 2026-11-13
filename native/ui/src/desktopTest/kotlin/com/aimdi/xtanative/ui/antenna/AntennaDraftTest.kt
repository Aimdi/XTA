package com.aimdi.xtanative.ui.antenna

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.antenna.Antenna
import com.aimdi.xtanative.store.antenna.AntennaScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntennaDraftTest {
    private val antenna = Antenna("a1", "Rockets", listOf("starship", "falcon 9"), listOf("ad"), AntennaScope.FOLLOWING, createdAt = 1)

    @Test
    fun anAntennaOpensInTheEditorAsTyped() {
        val draft = AntennaDraft.of(antenna)
        assertEquals(AntennaDraft("a1", "Rockets", "starship, falcon 9", "ad", AntennaScope.FOLLOWING), draft)
        assertFalse(draft.isNew)
        assertEquals(AntennaDraft(), AntennaDraft.of(null))
        assertTrue(AntennaDraft.of(null).isNew)
    }

    @Test
    fun theScopesSitSideBySideWhenTheyFitAndStackAtLargeText() {
        assertTrue(AntennaDraft.scopesSideBySide(widthDp = 600f, fontScale = 1f), "the compact segmented choice when it fits")
        assertFalse(AntennaDraft.scopesSideBySide(widthDp = 600f, fontScale = 2f), "at 200 percent text the choices stack")
        assertFalse(AntennaDraft.scopesSideBySide(widthDp = 320f, fontScale = 1f), "a narrow editor stacks them")
    }

    @Test
    fun okNeedsANameAndATerm() {
        assertFalse(AntennaDraft(name = "Rockets").canSave)
        assertFalse(AntennaDraft(name = "  ", include = "starship").canSave)
        assertFalse(AntennaDraft(name = "Rockets", include = " , \n ").canSave)
        val draft = AntennaDraft(name = "Rockets", include = "starship,\nfalcon 9 ", exclude = "ad, ")
        assertTrue(draft.canSave)
        assertEquals(listOf("starship", "falcon 9"), draft.includeTerms)
        assertEquals(listOf("ad"), draft.excludeTerms)
    }

    @Test
    fun aFollowingAntennaKeepsWhatFollowedAccountsPosted() {
        val followed = XUser("1", "maya", "Maya")
        val stranger = XUser("2", "jo", "Jo")
        val fromMaya = XThread("10", listOf(XPost("10", followed, null, "starship")))
        val fromJo = XThread("11", listOf(XPost("11", stranger, null, "starship")))
        // A repost counts as the reposter's.
        val repostByJo = XThread("12", listOf(XPost("12", stranger, null, "", reposted = XPost("9", followed, null, "starship"))))
        val ids = AntennaFeed.followedIds(Session(following = listOf(FollowedAccount("1", "maya", "Maya", null, false, true, 1))))
        assertEquals(setOf("1"), ids)
        assertEquals(listOf(fromMaya), AntennaFeed.kept(antenna, listOf(fromMaya, fromJo, repostByJo), ids))
        assertEquals(emptyList(), AntennaFeed.kept(antenna, listOf(fromMaya), emptySet()))
        val everyone = antenna.copy(scope = AntennaScope.SEARCH)
        assertEquals(listOf(fromMaya, fromJo), AntennaFeed.kept(everyone, listOf(fromMaya, fromJo), emptySet()))
        assertEquals(emptySet(), AntennaFeed.followedIds(null))
    }
}
