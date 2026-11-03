package com.aimdi.xtanative.services.ai

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.services.FakeHttp
import com.aimdi.xtanative.services.response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

class AiClientTest {
    private val grok = AiConfig(AiProvider.GROK.baseUrl, "secret", AiProvider.GROK.model)

    @Test
    fun anEmptyFieldKeepsAiOff() {
        assertFalse(AiConfig("", "k", "grok-4").isConfigured)
        assertFalse(AiConfig().isConfigured)
        assertTrue(grok.isConfigured)
    }

    @Test
    fun aGrokEndpointOrModelIsRecognised() {
        assertTrue(AiConfig(AiProvider.GROK.baseUrl, "k", "anything").isGrok)
        assertTrue(AiConfig(AiProvider.OPENAI.baseUrl, "k", "grok-4").isGrok)
        assertFalse(AiConfig(AiProvider.OPENAI.baseUrl, "k", AiProvider.OPENAI.model).isGrok)
        assertEquals(AiProvider.OPENROUTER, AiConfig("https://openrouter.ai/api/v1/", "k", "m").provider)
        assertEquals("api.x.ai", grok.host)
    }

    @Test
    fun aPresetForAnotherProviderClearsTheKeyAndTheSameOneKeepsIt() {
        val openAi = AiConfig(AiProvider.OPENAI.baseUrl, "sk-1", "gpt-4.1")
        assertEquals(AiConfig(AiProvider.GROK.baseUrl, "", "grok-4"), openAi.withPreset(AiProvider.GROK))
        assertEquals(openAi.copy(baseUrl = AiProvider.OPENAI.baseUrl), openAi.withPreset(AiProvider.OPENAI))
        assertEquals("gpt-4o-mini", openAi.copy(model = " ").withPreset(AiProvider.OPENAI).model)
    }

    @Test
    fun aChatCompletionIsPostedAndItsTextRead() = runBlocking {
        val http = FakeHttp { response(200, """{"choices":[{"message":{"content":"  hello  "}}]}""") }
        val text = AiClient(http).complete(grok.copy(baseUrl = "${AiProvider.GROK.baseUrl}/"), "ping")
        assertEquals("hello", text)
        val asked = http.asked.single()
        assertEquals("https://api.x.ai/v1/chat/completions", asked.url.toString())
        assertEquals("Bearer secret", asked.headers["Authorization"])
        assertEquals("application/json", asked.contentType)
        val body = J.parse(asked.body!!)
        assertEquals("grok-4", body["model"].string)
        assertEquals("user", body["messages"][0]["role"].string)
        assertEquals("ping", body["messages"][0]["content"].string)
    }

    @Test
    fun openRouterKeepsProviderModelIdsAndPostsToItsDocumentedEndpoint() = runBlocking {
        val http = FakeHttp { response(200, """{"choices":[{"message":{"content":"ranked"}}]}""") }
        val router = AiConfig(AiProvider.OPENROUTER.baseUrl, "router-key", AiProvider.OPENROUTER.model)
        assertEquals("ranked", AiClient(http).complete(router, "Rank these accounts"))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", http.asked.single().url.toString())
        assertEquals("openrouter/free", J.parse(http.asked.single().body!!)["model"].string)
    }

    @Test
    fun aPictureTravelsBesideTheTextAsADataUrl() {
        val body = AiClient.request("m", "Read this", "data:image/png;base64,AAAA")
        val content = J(body)["messages"][0]["content"]
        assertEquals("text", content[0]["type"].string)
        assertEquals("Read this", content[0]["text"].string)
        assertEquals("image_url", content[1]["type"].string)
        assertEquals("data:image/png;base64,AAAA", content[1]["image_url"]["url"].string)
    }

    @Test
    fun fullEndpointsAndCustomPathsGetNoSecondChatCompletions() {
        assertEquals(
            "https://custom.example/proxy/v1/chat/completions?api-version=1",
            AiClient.chatUrl("https://custom.example/proxy/v1/chat/completions/?api-version=1").toString(),
        )
        assertEquals("http://localhost:8080/v1/chat/completions", AiClient.chatUrl("http://localhost:8080/v1").toString())
        assertEquals(AiClient.chatUrl(AiProvider.OPENROUTER.baseUrl), AiClient.chatUrl("https://openrouter.ai/"))
        assertEquals(AiException.Reason.INVALID_ADDRESS, assertFailsWith<AiException> { AiClient.chatUrl("not a URL") }.reason)
        assertEquals(AiException.Reason.INVALID_ADDRESS, assertFailsWith<AiException> { AiClient.chatUrl("https://user:pw@api.x.ai/v1") }.reason)
    }

    @Test
    fun aStalledProviderTimesOut() = runBlocking {
        val http = FakeHttp { awaitCancellation() }
        val error = assertFailsWith<AiException> { AiClient(http).complete(grok, "ping", timeoutMillis = 10) }
        assertEquals(AiException.Reason.TIMEOUT, error.reason)
    }

    @Test
    fun aRefusalOrAMissingConfigurationIsAnAiException() = runBlocking {
        val error = assertFailsWith<AiException> { AiClient(FakeHttp { response(401, "nope") }).complete(grok, "ping") }
        assertEquals(AiException.Reason.HTTP, error.reason)
        assertEquals(401, error.status)
        val off = FakeHttp { error("nothing is sent while AI is off") }
        assertEquals(AiException.Reason.NOT_CONFIGURED, assertFailsWith<AiException> { AiClient(off).complete(AiConfig(), "ping") }.reason)
    }

    @Test
    fun theCompletionTextSurvivesAReshapedBody() {
        assertEquals("", AiClient.completionText("[]"))
        assertEquals("", AiClient.completionText("<html>Bad gateway</html>"))
        assertEquals("", AiClient.completionText("""{"choices":[{"message":{"content":123}}]}"""))
        assertEquals("", AiClient.completionText("""{"choices":[{"text":{}}]}"""))
        assertEquals(
            "first\nsecond",
            AiClient.completionText(
                """{"choices":[{"message":{"content":[{"type":"text","text":"first"},{"type":"text","text":42},""" +
                    """{"type":"image_url","text":"ignored"},{"type":"text","text":"second"}]}}]}""",
            ),
        )
        assertEquals("", AiClient.completionText("""{"choices":[]}"""))
        assertEquals("legacy", AiClient.completionText("""{"choices":[{"text":"legacy"}]}"""))
    }

    @Test
    fun onlyAPngOrAJpegOfAtMostEightMegabytesIsSentForTranscription() {
        assertEquals("data:image/png;base64,iVBORw==", AiPrompts.imageDataUrl(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertTrue(AiPrompts.imageDataUrl(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0))!!.startsWith("data:image/jpeg;base64,"))
        assertNull(AiPrompts.imageDataUrl(byteArrayOf(0x47, 0x49, 0x46, 0x38)), "a GIF")
        assertNull(AiPrompts.imageDataUrl(byteArrayOf(0x89.toByte(), 0x50)), "too short to be a picture")
        assertNull(AiPrompts.imageDataUrl(ByteArray(AiPrompts.MAX_IMAGE_BYTES + 1).also { it[0] = 0x89.toByte(); it[1] = 0x50 }))
    }

    @Test
    fun theRankingPromptCarriesEachCandidatesSignalsAndCutsItsSample() {
        val prompt = AiPrompts.ranking("Künstlerin 🎨", listOf(RankingCandidate("x:1", "Ada", "ada", 3, listOf("repost", "quote", "repost"), "a".repeat(600))))
        assertTrue(prompt.startsWith("Rank only these real account ids"))
        val payload = J.parse(prompt.substringAfter('\n'))
        assertEquals("Künstlerin 🎨", payload["group"].string)
        val account = payload["accounts"][0]
        assertEquals(listOf("id", "name", "handle", "members", "signals", "sample"), account.keys.toList())
        assertEquals(2, account["signals"]["repost"].int)
        assertEquals(1, account["signals"]["quote"].int)
        assertEquals(500, account["sample"].string!!.length)
        val many = (1..70).map { RankingCandidate("x:$it", "n", "h", 1, emptyList(), "") }
        assertEquals(60, J.parse(AiPrompts.ranking("g", many).substringAfter('\n'))["accounts"].list.size)
    }

    @Test
    fun inventedIdsAreRejectedAndCandidatesTheModelLeftOutKeepTheirPlace() {
        val candidates = listOf("x:1", "x:2", "x:3")
        assertEquals(listOf("x:2", "x:1", "x:3"), AiPrompts.ranked("""Sure: {"ids":["x:2","invented:9","x:2"]}""", candidates))
        assertNull(AiPrompts.ranked("""{"ids":["invented:9"]}""", candidates))
        assertNull(AiPrompts.ranked("""{"ids":"x:1"}""", candidates))
        assertNull(AiPrompts.ranked("not json", candidates))
    }

    @Test
    fun theGroupingPromptShowsAFewMembersOfEachGroup() {
        val members = (1..20).map { GroupingAccount("m$it", "handle$it", "Name $it") }
        val prompt = AiPrompts.grouping(listOf(GroupingAccount("1", "nasa", "NASA")), listOf(GroupingGroup("space", "Space", members)))
        assertTrue(prompt.startsWith("Sort these read-only subscriptions into groups."))
        val payload = J.parse(prompt.substringAfter('\n'))
        assertEquals(12, payload["groups"][0]["members"].list.size)
        assertEquals("handle1 Name 1", payload["groups"][0]["members"][0].string)
        assertEquals("nasa", payload["ungrouped"][0]["handle"].string)
    }

    @Test
    fun aGroupingReplyIsReadAndUnknownIdsAreDropped() {
        val plan = AiPrompts.groupingPlan(
            "Sure.\n{\"assign\":[{\"id\":\"1\",\"groupId\":\"space\"},{\"id\":\"nope\",\"groupId\":\"space\"}]," +
                "\"suggest\":[{\"name\":\"Games\",\"ids\":[\"2\",\"3\",\"ghost\"]},{\"name\":\"Solo\",\"ids\":[\"4\"]}]}\n",
            accountIds = setOf("1", "2", "3", "4"),
            groupIds = setOf("space"),
        )
        assertTrue(plan.usedAi)
        assertEquals(listOf(GroupingPlan.Assignment("1", "space")), plan.assign)
        assertEquals(listOf(GroupingPlan.Suggestion("Games", listOf("2", "3"))), plan.suggest)
        assertEquals(listOf("4"), plan.leftoverIds)
        assertFalse(AiPrompts.groupingPlan("no json here", setOf("1"), setOf("g")).usedAi)
    }

    @Test
    fun theModelsAssignmentsWinAndTheHeuristicFillsTheRest() {
        val heuristic = GroupingPlan(
            assign = listOf(GroupingPlan.Assignment("1", "old"), GroupingPlan.Assignment("2", "space")),
            suggest = listOf(GroupingPlan.Suggestion("Dev", listOf("3", "4", "5"))),
            leftoverIds = listOf("6", "4"),
        )
        val ai = GroupingPlan(assign = listOf(GroupingPlan.Assignment("1", "space"), GroupingPlan.Assignment("4", "space")), leftoverIds = listOf("6"), usedAi = true)
        val merged = heuristic.mergedWith(ai)
        assertTrue(merged.usedAi)
        assertEquals(listOf("1:space", "4:space", "2:space"), merged.assign.map { "${it.accountId}:${it.groupId}" })
        assertEquals(listOf(GroupingPlan.Suggestion("Dev", listOf("3", "5"))), merged.suggest)
        assertEquals(listOf("6"), merged.leftoverIds)
    }
}
