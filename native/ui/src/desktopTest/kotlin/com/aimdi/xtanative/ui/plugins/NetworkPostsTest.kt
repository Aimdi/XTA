package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.services.links.LinkPostContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkPostsTest {
    private val post = NetworkPost(
        id = "at://did:plc:a/app.bsky.feed.post/1",
        authorId = "did:plc:a",
        authorName = "Alice",
        authorHandle = "alice.example",
        avatarUrl = "https://cdn.example/a.jpg",
        replies = 3,
        reposts = 5,
        quotes = 2,
        likes = 8,
        url = "https://bsky.app/profile/alice.example/post/1",
    )

    @Test
    fun aLinkOpenedFromACardCarriesItsPostAsXtasCardsHandIt() {
        assertEquals(
            LinkPostContext("bluesky", "Alice", "https://cdn.example/a.jpg", replies = 3, reposts = 5, likes = 8, postUrl = post.url),
            post.linkContext("bluesky"),
        )
    }

    @Test
    fun aWarningOverTheMediaAloneIsDrawnAtTheMediaAndOnlyWhereThereIsSome() {
        val photo = listOf(MediaItem("https://cdn.example/p.jpg"))
        assertNull(post.mediaWarning())
        assertNull(post.copy(warning = "Sensitive").mediaWarning())
        assertEquals("Sensitive", post.copy(warning = "Sensitive", media = photo).mediaWarning())
        assertNull(post.copy(warning = "Spoiler", media = photo, coversText = true).mediaWarning())
    }
}
