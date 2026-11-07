package com.aimdi.xtanative.networks.pixiv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's pixiv_client_transport_test, where native's client tests did not check it yet. */
class PixivTransportTest {
    @Test
    fun postFormSendsAFormAndReadsAnEmptyAnswerAsNothing() = runBlocking {
        val http = FakePixivHttp { answer("") }
        val answered = pixiv(http).postForm("/v1/user/follow/add", mapOf("user_id" to "9", "restrict" to "public"))
        val asked = http.asked.single()
        assertEquals("POST" to "/v1/user/follow/add", asked.method to asked.url.encodedPath)
        assertEquals(mapOf("user_id" to "9", "restrict" to "public"), asked.form)
        assertTrue(asked.contentType.orEmpty().startsWith("application/x-www-form-urlencoded"))
        assertTrue(answered.exists && answered.keys.isEmpty())
    }
}
