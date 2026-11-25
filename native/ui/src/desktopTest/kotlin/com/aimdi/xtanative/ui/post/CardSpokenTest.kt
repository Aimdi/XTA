package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals

class CardSpokenTest {
    @Test
    fun aCardOffersItsAuthorThenItsButtonsThenItsMenu() {
        assertEquals(
            listOf(CardSpoken.AUTHOR, CardSpoken.LIKE, CardSpoken.SAVE, CardSpoken.SHARE, CardSpoken.MENU),
            CardSpoken.of(showsAuthor = true),
        )
    }

    @Test
    fun aCardThatHidesItsAuthorDoesNotOfferTheProfile() {
        assertEquals(listOf(CardSpoken.LIKE, CardSpoken.SAVE, CardSpoken.SHARE, CardSpoken.MENU), CardSpoken.of(showsAuthor = false))
    }
}
