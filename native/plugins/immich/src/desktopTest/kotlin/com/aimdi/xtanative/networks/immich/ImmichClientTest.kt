package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The rules XTA's immich_client_test encodes, against a fake Immich answering from fixtures. */
class ImmichClientTest {
    private val createdAt = Instant.parse("2026-03-04T05:06:07Z")

    private suspend fun upload(http: FakeImmich) =
        ImmichClient(http).upload(server(), byteArrayOf(1, 2, 3), "photo.jpg", "media-1", createdAt)

    private fun failure(block: suspend () -> Unit): Throwable = runCatching { runBlocking { block() } }.exceptionOrNull()!!

    @Test
    fun anUploadGoesToApiAssetsWithTheKeyAndTheFieldsImmichNeeds() = runBlocking {
        val http = FakeImmich { json(fixture("asset_created.json"), 201) }
        val result = upload(http)

        val sent = http.sent.single()
        assertEquals("POST", sent.method)
        assertEquals("http://immich.local:2283/api/assets", sent.url.toString())
        assertEquals("key", sent.headers["x-api-key"])
        assertEquals("application/json", sent.headers["Accept"])
        val boundary = sent.contentType!!.removePrefix("multipart/form-data; boundary=")
        val field = { name: String, value: String -> "--$boundary\r\ncontent-disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n" }
        val expected = field("deviceAssetId", "media-1") + field("deviceId", "xta") +
            field("fileCreatedAt", "2026-03-04T05:06:07.000Z") + field("fileModifiedAt", "2026-03-04T05:06:07.000Z") +
            "--$boundary\r\ncontent-type: application/octet-stream\r\n" +
            "content-disposition: form-data; name=\"assetData\"; filename=\"photo.jpg\"\r\n\r\n\u0001\u0002\u0003\r\n--$boundary--\r\n"
        assertEquals(expected, sent.text)
        assertEquals(ImmichUploadResult(ImmichUploadOutcome.CREATED, "f5a3c9e2-7b1d-4e8f-a0c6-3d9b2e4f6a18"), result)
    }

    @Test
    fun aFileImmichAlreadyHadIsASuccessNotAFailure() = runBlocking {
        val result = upload(FakeImmich { json(fixture("asset_duplicate.json")) })
        assertEquals(ImmichUploadOutcome.DUPLICATE, result.outcome)
        assertEquals("0c7e5a3f-2d9b-4f1e-8a6c-b4d2e0f8a1c3", result.assetId, "the asset Immich already had, so it can still be filed")
    }

    @Test
    fun anUploadFallsBackToTheOlderEndpointWhenTheCurrentOneIsNotThere() = runBlocking {
        val http = FakeImmich { if (it.url.encodedPath == "/api/assets") json("""{"message":"Not found"}""", 404) else json(fixture("asset_created.json"), 201) }
        val result = upload(http)

        assertEquals(listOf("/api/assets", "/api/asset/upload"), http.paths)
        assertEquals(http.sent[0].text, http.sent[1].text, "the same file goes to the older path")
        assertEquals(ImmichUploadOutcome.CREATED, result.outcome)
    }

    @Test
    fun aRejectedKeyIsReportedAsUnauthorized() {
        val error = failure { upload(FakeImmich { XResponse(401, "{}") }) }
        assertEquals(401, (error as NetworkRequestException).code)
        assertEquals(ImmichClient.NETWORK, error.network)
        assertEquals(ImmichProblem.UNAUTHORIZED, ImmichProblem.of(error))
    }

    @Test
    fun anUploadAnsweredWithAPageIsABadAddressNotACreatedAsset() {
        val error = failure { upload(FakeImmich { XResponse(200, "<html>Router</html>") }) }
        assertEquals(ImmichProblem.BAD_SERVER, ImmichProblem.of(error))
    }

    @Test
    fun aServerThatKnowsTheUserIsAPass() = runBlocking {
        val http = FakeImmich { json(fixture("users_me.json")) }
        ImmichClient(http).verify(server())

        assertEquals(listOf("/api/users/me"), http.paths)
        assertEquals(mapOf("x-api-key" to "key", "Accept" to "application/json"), http.sent.single().headers)
    }

    @Test
    fun anOlderInstanceAnsweringOnThePreviousPathStillPasses() = runBlocking {
        val http = FakeImmich { if (it.url.encodedPath == "/api/users/me") json("{}", 404) else json(fixture("users_me.json")) }
        ImmichClient(http).verify(server())
        assertEquals(listOf("/api/users/me", "/api/user/me"), http.paths)
    }

    @Test
    fun anHtmlPageFromSomethingThatIsNotImmichIsABadAddress() {
        // Something is listening, but it is not Immich: a reverse proxy or a router page. A server error would send the user looking in the wrong place.
        val error = failure { ImmichClient(FakeImmich { XResponse(200, "<html>Router</html>") }).verify(server("http://192.168.1.1")) }
        assertTrue(error is NotImmichException)
        assertEquals(ImmichProblem.BAD_SERVER, ImmichProblem.of(error))
    }

    @Test
    fun anUnreachableServerIsANetworkProblemNotAWrongKey() {
        val error = failure { ImmichClient(FakeImmich { throw IOException("no route to host") }).verify(server()) }
        assertTrue(error is IOException)
        assertEquals(ImmichProblem.NETWORK, ImmichProblem.of(error))
    }

    @Test
    fun eachAnswerMeansWhatXtaTellsTheUser() {
        val cases = listOf(
            XResponse(403, "{}") to ImmichProblem.UNAUTHORIZED,
            XResponse(401, "<html>Authorization Required</html>") to ImmichProblem.UNAUTHORIZED,
            XResponse(404, "<html>nginx</html>") to ImmichProblem.BAD_SERVER,
            json("""{"message":"Internal server error","statusCode":500}""", 500) to ImmichProblem.SERVER,
            XResponse(502, "<html>Bad Gateway</html>") to ImmichProblem.BAD_SERVER,
        )
        for ((answer, problem) in cases) {
            val error = failure { ImmichClient(FakeImmich { answer }).verify(server()) }
            assertEquals(problem, ImmichProblem.of(error), "HTTP ${answer.code}: ${answer.body}")
        }
    }

    @Test
    fun anAlbumThatExistsIsUsedRatherThanMadeTwice() = runBlocking {
        val http = FakeImmich { json(fixture("albums.json")) }
        assertEquals("6a1b2c3d-0000-4000-8000-000000000003", ImmichClient(http).ensureAlbum(server(), "Art"), "the album of exactly that name")
        assertEquals(listOf("GET"), http.sent.map { it.method })
        assertEquals("/api/albums", http.paths.single())
    }

    @Test
    fun anAlbumThatDoesNotExistIsCreated() = runBlocking {
        val http = FakeImmich { if (it.method == "GET") json("[]") else json(fixture("album_created.json"), 201) }
        assertEquals("9e4d2b7a-5c1f-4a3e-8b6d-0f2a4c6e8b1d", ImmichClient(http).ensureAlbum(server(), "New"))

        val created = http.sent.last()
        assertEquals("POST", created.method)
        assertEquals("/api/albums", created.url.encodedPath)
        assertEquals("application/json", created.contentType)
        assertEquals("New", J.parse(created.text)["albumName"].string)
        assertEquals("key", created.headers["x-api-key"])
    }

    @Test
    fun aServerThatWillNotTalkAboutAlbumsGivesNullNotAThrow() = runBlocking {
        // The assets are already in the library by this point: an album that could not be had must not fail the upload.
        assertNull(ImmichClient(FakeImmich { XResponse(500, "nope") }).ensureAlbum(server(), "Art"))
        assertNull(ImmichClient(FakeImmich { throw IOException("reset") }).ensureAlbum(server(), "Art"))
    }

    @Test
    fun anAlbumListThatClaimsJsonButIsNotGivesUpRatherThanMakingASecondAlbum() = runBlocking {
        val http = FakeImmich { json("""[{"albumName": "Art", """) }
        assertNull(ImmichClient(http).ensureAlbum(server(), "Art"))
        assertEquals(listOf("GET"), http.sent.map { it.method })
    }

    @Test
    fun filingNothingDoesNotCallTheServer() = runBlocking {
        val http = FakeImmich()
        assertTrue(ImmichClient(http).addToAlbum(server(), "a", emptyList()))
        assertTrue(http.sent.isEmpty())
    }

    @Test
    fun filingAssetsSendsTheirIds() = runBlocking {
        val http = FakeImmich { json(fixture("album_assets.json")) }
        assertTrue(ImmichClient(http).addToAlbum(server(), "a", listOf("x", "y")))

        val sent = http.sent.single()
        assertEquals("PUT", sent.method)
        assertEquals("/api/albums/a/assets", sent.url.encodedPath)
        assertEquals("""{"ids":["x","y"]}""", sent.text)
        assertEquals("application/json; charset=utf-8", sent.contentType)
    }

    @Test
    fun aMultipartNameIsQuotedAsBrowsersDoAndAValueBeyondAsciiSaysItIsUtf8() {
        val form = Multipart(listOf("caption" to "café"), FilePart("assetData", "a \"b\"\nc.jpg", byteArrayOf()), boundary = "B")
        val text = form.bytes().toString(Charsets.UTF_8)
        assertTrue("name=\"caption\"\r\ncontent-type: text/plain; charset=utf-8\r\ncontent-transfer-encoding: binary\r\n\r\ncafé\r\n" in text, text)
        assertTrue("filename=\"a %22b%22%0D%0Ac.jpg\"" in text, text)
        assertEquals("multipart/form-data; boundary=B", form.contentType)
    }
}
