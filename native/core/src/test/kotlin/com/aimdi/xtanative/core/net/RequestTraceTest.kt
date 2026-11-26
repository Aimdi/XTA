package com.aimdi.xtanative.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.HttpUrl.Companion.toHttpUrl

class RequestTraceTest {
    @Test
    fun aUrlIsShownWithoutQueryLoginOrLongPieces() {
        val url = "https://user:secret@x.com/i/api/graphql/abcdefghijklmnopqrstuv/HomeTimeline?variables=%7B%7D&auth_token=zzz#top".toHttpUrl()
        assertEquals("https://x.com/i/api/graphql/abcdefghijklmnopqrstuv/HomeTimeline", RequestTrace.shown(url))
        val keyed = "http://127.0.0.1:18183/bot0123456789abcdef0123456789abcdef/getMe".toHttpUrl()
        assertEquals("http://127.0.0.1:18183/…/getMe", RequestTrace.shown(keyed))
    }

    @Test
    fun aLineSaysMethodPlaceOutcomeAndTime() {
        assertEquals("XTA Native: http GET https://x.com/ 200 in 12 ms", RequestTrace.line("GET", "https://x.com/", "200", 12))
    }
}
