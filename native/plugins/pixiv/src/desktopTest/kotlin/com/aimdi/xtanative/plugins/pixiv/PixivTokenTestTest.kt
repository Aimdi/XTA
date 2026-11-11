package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.pixiv.PixivAuthUser
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The settings' Test (pixiv_settings_account.dart's _test): whose token it is, or the cause in a failed read's words. */
class PixivTokenTestTest {
    private fun told(error: Exception): String = tokenTestMessage(Result.failure(error), "en")

    @Test
    fun aWorkingTokenNamesItsAccount() {
        val mika = PixivAuthUser(1, "Mika", "mika")
        assertEquals(Strings.signed_in.get("en", "Mika"), tokenTestMessage(Result.success(mika), "en"))
    }

    @Test
    fun aTokenPixivRefusesIsToldAsARefusal() {
        assertEquals(Strings.error_unauthorized.get("en"), told(NetworkRequestException("pixiv", 401, "refused")))
    }

    @Test
    fun noConnectionIsNotBlamedOnTheToken() {
        assertEquals(Strings.error_network.get("en"), told(IOException("no route")))
    }

    @Test
    fun aLimitAndABadAnswerAreToldAsWhatTheyAre() {
        assertEquals(Strings.error_rate_limited.get("en"), told(NetworkRequestException("pixiv", 429, "slow down")))
        assertEquals(Strings.error_response.get("en"), told(NetworkRequestException("pixiv", 500, "broken")))
    }
}
