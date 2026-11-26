package com.aimdi.xtanative.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.HttpUrl.Companion.toHttpUrl

class RequestTraceTest {
    @Test
    fun aUrlIsShownWithoutQueryLoginOrLongPieces() {
        val url = "https://user:secret@x.com/i/api/graphql/abcdefghijklmnopqrstuv/HomeTimeline?variables=%7B%7D&auth_token=zzz#top".toHttpUrl()
        assertEquals("https://x.com/i/api/graphql/abcdefghijklmnopqrstuv/HomeTimeline", RequestTrace.shown(url))
        val keyed = "https://api.x.com/1.1/bot0123456789abcdef0123456789abcdef/getMe".toHttpUrl()
        assertEquals("https://api.x.com/1.1/…/getMe", RequestTrace.shown(keyed))
        assertEquals("https://raw.githubusercontent.com/Aimdi/XTA/HEAD/endpoints.json", RequestTrace.shown("https://raw.githubusercontent.com/Aimdi/XTA/HEAD/endpoints.json".toHttpUrl()))
    }

    @Test
    fun anyOtherPathIsHidden() {
        val dav = "https://cloud.example/remote.php/dav/files/maya/XTA/backup.json".toHttpUrl()
        assertEquals("https://cloud.example/…", RequestTrace.shown(dav), "the reader's login and file names")
        assertEquals("http://192.168.1.5:2283/…", RequestTrace.shown("http://192.168.1.5:2283/api/assets?key=abc".toHttpUrl()))
        assertEquals("https://mastodon.social/…", RequestTrace.shown("https://mastodon.social/@maya/feed".toHttpUrl()))
        assertEquals("https://x.com/…", RequestTrace.shown("https://x.com/maya/status/1".toHttpUrl()), "a page of X names a person")
        assertEquals("https://api.github.com/…", RequestTrace.shown("https://api.github.com/repos/me/fork/issues".toHttpUrl()))
        assertEquals("https://cloud.example/", RequestTrace.shown("https://cloud.example/".toHttpUrl()))
    }

    @Test
    fun aLineSaysMethodPlaceOutcomeAndTime() {
        assertEquals("XTA Native: http GET https://x.com/ 200 in 12 ms", RequestTrace.line("GET", "https://x.com/", "200", 12))
    }
}
