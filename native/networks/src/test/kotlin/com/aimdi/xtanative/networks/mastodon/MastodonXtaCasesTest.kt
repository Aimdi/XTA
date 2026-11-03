package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The port against XTA itself: xta_cases.json holds inputs run through
 * XTA's own Dart functions (copied verbatim from mastodon_models.dart,
 * mastodon_search_store.dart and plugin_url.dart) with their answers, edge
 * cases included: Mastodon's HTML, entity boundaries, Unicode, odd addresses.
 */
class MastodonXtaCasesTest {
    private val cases = J.parse(javaClass.getResource("/mastodon/xta_cases.json")!!.readText())

    private fun check(section: String, compare: (J) -> Pair<Any?, Any?>) {
        val cases = cases[section].list
        assertTrue(cases.isNotEmpty(), "no cases for $section")
        val wrong = cases.mapNotNull { case ->
            val (ours, xtas) = compare(case)
            if (ours == xtas) null else "${case.raw}\n  ours: $ours\n  XTA:  $xtas"
        }
        assertTrue(wrong.isEmpty(), "${wrong.size} of ${cases.size} $section cases differ from XTA:\n" + wrong.joinToString("\n"))
    }

    private fun strings(json: J): List<String> = json.list.mapNotNull { it.string }

    private fun encode(part: MastodonTextPart): String = when (part) {
        is MastodonTextPart.Text -> "text|${part.text}|"
        is MastodonTextPart.Link -> "link|${part.text}|${part.url}"
        is MastodonTextPart.Mention -> "mention|${part.text}|${part.acct}"
        is MastodonTextPart.Tag -> "tag|${part.text}|${part.name}"
        is MastodonTextPart.Emoji -> "emoji|${part.text}|${part.emoji.shortcode}"
    }

    @Test
    fun htmlBecomesTheTextXtaMakesOfIt() = check("html") { MastodonText.ofHtml(it["html"].string) to it["text"].string }

    @Test
    fun textIsCutIntoTheLinksMentionsAndTagsXtaFinds() = check("parts") { case ->
        val ours = MastodonText.parts(case["text"].string.orEmpty(), strings(case["mentions"])).map(::encode)
        val xtas = case["parts"].list.map { "${it["kind"].string}|${it["text"].string}|${it["value"].string}" }
        ours to xtas
    }

    @Test
    fun instancesNormaliseAsInXta() = check("instances") { case ->
        val input = case["input"].string.orEmpty()
        (MastodonInstances.normalise(input) to MastodonInstances.domain(input)) to (case["normalised"].string to case["domain"].string)
    }

    @Test
    fun acctsNormaliseAsInXta() = check("accts") { MastodonAcct.normalise(it["input"].string.orEmpty()) to it["normalised"].string }

    @Test
    fun acctsGetTheirDomainAsInXta() = check("canonical") {
        MastodonAcct.canonical(it["acct"].string.orEmpty(), it["domain"].string) to it["canonical"].string
    }

    @Test
    fun statusIdsComeOutOfAddressesAsInXta() = check("statusIds") { MastodonUrls.statusId(it["url"].string.orEmpty()) to it["id"].string }

    @Test
    fun statusAddressesMatchAsInXta() = check("sameStatus") {
        MastodonUrls.sameStatus(it["a"].string.orEmpty(), it["b"].string.orEmpty()) to it["same"].boolean
    }

    @Test
    fun postsAreKeyedAsInXta() = check("postKeys") { case ->
        val post = MastodonPost(id = case["id"].string.orEmpty(), acct = case["acct"].string.orEmpty(), authorName = "", text = "", url = case["url"].string.orEmpty())
        MastodonUrls.postKey(post) to case["key"].string
    }

    @Test
    fun webAddressesAreCheckedAsInXta() = check("httpUrls") { MastodonUrls.web(it["input"].string) to it["url"].string }

    @Test
    fun aFieldsLinkIsReadAsInXta() = check("fieldUrls") { MastodonText.singleLink(it["html"].string) to it["url"].string }

    @Test
    fun pastedStatusAddressesAreRecognisedAsInXta() = check("searchTargets") { case ->
        val ours = MastodonUrls.searchTarget(case["input"].string.orEmpty())?.let { it.instance to it.id }
        val xtas = case["target"].takeIf { it.exists }?.let { it["instance"].string to it["id"].string }
        ours to xtas
    }

    @Test
    fun linksOpenInTheAppAsInXta() = check("links") { case ->
        val ours = when (val link = MastodonUrls.link(case["url"].string.orEmpty(), strings(case["hosts"]).toSet())) {
            is MastodonLink.Profile -> listOf("profile", link.acct)
            is MastodonLink.Status -> listOf("status", link.acct, link.statusId, link.url)
            null -> listOf(null)
        }
        val xta = case["link"]
        val xtas = when (xta["kind"].string) {
            "profile" -> listOf("profile", xta["acct"].string)
            "status" -> listOf("status", xta["acct"].string, xta["statusId"].string, xta["url"].string)
            else -> listOf(null)
        }
        ours to xtas
    }

    @Test
    fun instancesAreAskedInXtasOrder() = check("candidates") { case ->
        val acct = case["acct"].string.orEmpty()
        val configured = strings(case["configured"])
        listOf(MastodonInstances.candidates(acct, configured), MastodonInstances.feedCandidates(acct, configured)) to
            listOf(strings(case["all"]), strings(case["feed"]))
    }
}
