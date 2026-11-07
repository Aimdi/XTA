package com.aimdi.xtanative.store.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's migrateFeedStripPins, for a backup that never hand-picked Home's timelines. */
class FeedStripMigrationTest {
    @Test
    fun aHandPickedStripIsKeptAndLaterInstallsAreNotAdded() {
        val picked = Settings()
            .with(HomePrefs.feedStripNetworks, listOf("mastodon"))
            .with(HomePrefs.feedStripHandPicked, true)
            .with(RedditPrefs.enabled, true)
        assertEquals(listOf("mastodon"), FeedStripMigration.of(picked)[HomePrefs.feedStripNetworks])
    }

    @Test
    fun aStripThatWasNeverSavedKeepsEveryTimelineItShowed() {
        val old = Settings().with(RedditPrefs.enabled, true).with(MastodonPrefs.enabled, true).with(MastodonPrefs.showTab, false)
        val migrated = FeedStripMigration.of(old)
        assertEquals(listOf("mastodon", "reddit"), migrated[HomePrefs.feedStripNetworks])
        assertTrue(migrated[HomePrefs.feedStripHandPicked])
    }

    @Test
    fun aHiddenTabNetworkHomeShowedIsWrittenIntoTheStrip() {
        val old = Settings().with(HomePrefs.feedStripNetworks, listOf("mastodon")).with(SubstackPrefs.enabled, true).with(SubstackPrefs.showTab, false)
        assertEquals(listOf("mastodon", "substack"), FeedStripMigration.of(old)[HomePrefs.feedStripNetworks])
    }

    @Test
    fun aNetworkWithNoHomeTabIsNotATimelineAndAFlagLeftFalseStillMigrates() {
        assertEquals(emptyList(), FeedStripMigration.of(Settings().with(KarakeepPrefs.enabled, true))[HomePrefs.feedStripNetworks])
        val leftFalse = Settings().with(HomePrefs.feedStripHandPicked, false).with(RedditPrefs.enabled, true)
        val migrated = FeedStripMigration.of(leftFalse)
        assertEquals(listOf("reddit"), migrated[HomePrefs.feedStripNetworks])
        assertTrue(migrated[HomePrefs.feedStripHandPicked])
        assertFalse(migrated.isSet(KarakeepPrefs.enabled))
    }
}
