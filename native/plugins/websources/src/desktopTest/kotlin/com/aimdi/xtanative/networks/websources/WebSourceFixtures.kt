package com.aimdi.xtanative.networks.websources

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** XTA's test/support/web_source_fixtures.dart: a source of each shape, and a page for each. */
object WebSourceFixtures {
    fun text(name: String): String = WebSourceFixtures::class.java.getResource("/websources/$name")!!.readText()

    /** A links source over a JSON API, as Lobsters publishes one. */
    fun links(version: Int = 1, hosts: List<String> = listOf("lobste.rs")): JsonObject =
        json("lobsters.json").with("version", JsonPrimitive(version)).with("hosts", strings(hosts))

    /** A posts source with nested JSON, templates and a cursor, as Lemmy's API. */
    fun posts(): JsonObject = json("lemmy.json")

    /** An HTML source, as a site without an API. */
    fun html(): JsonObject = json("tildes.json")

    /** A gallery source. */
    fun gallery(): JsonObject = json("nasa.json")

    fun source(json: JsonObject): WebSourceDefinition = (WebSources.read(json.toString()) as WebSourceFile.Read).source

    fun read(json: JsonObject): WebSourceFile = WebSources.read(json.toString())

    fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))

    fun JsonObject.without(key: String): JsonObject = JsonObject(this - key)

    fun JsonObject.withFeed(feed: JsonObject): JsonObject = with("feeds", JsonArray(listOf(feed)))

    fun obj(vararg pairs: Pair<String, Any>): JsonObject = JsonObject(pairs.associate { (key, value) -> key to element(value) })

    fun strings(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

    private fun element(value: Any): JsonElement = when (value) {
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> error("not JSON: $value")
    }

    private fun json(name: String): JsonObject = Json.parseToJsonElement(text(name)).jsonObject
}
