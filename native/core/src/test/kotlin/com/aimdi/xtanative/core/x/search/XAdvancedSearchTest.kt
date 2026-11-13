package com.aimdi.xtanative.core.x.search

import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Content
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Filter
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Inclusion
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** XTA's advanced_search_test.dart and advanced_search_form_test.dart, the model half. */
class XAdvancedSearchTest {
    @Test
    fun everyOperatorIsComposed() {
        val search = XAdvancedSearch(
            allWords = "flutter android",
            exactPhrase = "quiet signal",
            anyWords = "reader, timeline",
            noneWords = "spam ads",
            hashtags = "design #accessibility",
            fromAccounts = "@alice bob",
            toAccounts = "carol",
            mentioningAccounts = "@dave",
            minReplies = "2",
            minLikes = "10",
            minRetweets = "3",
            since = LocalDate.of(2026, 1, 2),
            until = LocalDate.of(2026, 8, 31),
        ).withOnlyMedia(true)
        assertEquals(
            "flutter android \"quiet signal\" (reader OR timeline) -spam -ads (#design OR #accessibility) (from:alice OR from:bob) to:carol " +
                "@dave min_replies:2 min_faves:10 min_retweets:3 since:2026-01-02 until:2026-08-31 filter:media",
            search.query,
        )
    }

    @Test
    fun everyOperatorHasAFixedPlace() {
        val search = XAdvancedSearch(
            allWords = "cats", exactPhrase = "quiet signal", anyWords = "reader timeline", noneWords = "spam", hashtags = "flutter",
            language = "fr", fromAccounts = "alice", toAccounts = "bob", mentioningAccounts = "carol", minReplies = "2", minLikes = "10",
            minRetweets = "3", since = LocalDate.of(2026, 1, 2), until = LocalDate.of(2026, 8, 31), content = Content.PHOTOS,
            links = Inclusion.EXCLUDE, replies = Inclusion.ONLY, includeReposts = true,
        )
        assertEquals(
            "cats \"quiet signal\" (reader OR timeline) -spam #flutter lang:fr from:alice to:bob @carol " +
                "min_replies:2 min_faves:10 min_retweets:3 since:2026-01-02 until:2026-08-31 " +
                "filter:images -filter:links filter:replies include:nativeretweets",
            search.query,
        )
    }

    @Test
    fun eachFilterWritesOneOperatorAndItsDefaultNone() {
        val base = XAdvancedSearch(allWords = "cats")
        mapOf(
            base.copy(language = "fr") to "cats lang:fr",
            base.copy(replies = Inclusion.INCLUDE, links = Inclusion.INCLUDE) to "cats",
            base.copy(replies = Inclusion.ONLY) to "cats filter:replies",
            base.copy(replies = Inclusion.EXCLUDE) to "cats -filter:replies",
            base.copy(links = Inclusion.ONLY) to "cats filter:links",
            base.copy(links = Inclusion.EXCLUDE) to "cats -filter:links",
            base.copy(includeReposts = true) to "cats include:nativeretweets",
            base.copy(minLikes = "0") to "cats",
        ).forEach { (search, query) -> assertEquals(query, search.query) }
    }

    @Test
    fun oneFilterClearsWithoutDisturbingTheRest() {
        val search = XAdvancedSearch(allWords = "flutter", hashtags = "android").withOnlyMedia(true)
        val cleared = search.cleared(Filter.HASHTAGS)
        assertEquals("flutter filter:media", cleared.query)
        assertEquals(listOf(Filter.ALL_WORDS, Filter.ONLY_MEDIA), cleared.activeFilters)
        assertEquals(XAdvancedSearch(), XAdvancedSearch(allWords = "x").cleared(Filter.ALL_WORDS), "and the whole form clears to nothing")
    }

    @Test
    fun aContentChoiceReplacesTheOneBefore() {
        val base = XAdvancedSearch(allWords = "flutter").withOnlyMedia(true)
        mapOf(Content.PHOTOS to "filter:images", Content.VIDEOS to "filter:videos", Content.MEDIA to "filter:media", Content.ALL to "").forEach { (content, operator) ->
            val search = base.copy(content = content)
            assertEquals("flutter $operator".trim(), search.query)
            assertEquals(if (content == Content.ALL) 1 else 2, search.activeFilters.size)
        }
    }

    @Test
    fun theMediaSwitchAddsAndRemovesOnlyTheMediaChoice() {
        val media = XAdvancedSearch().withOnlyMedia(true)
        assertTrue(media.onlyMedia)
        assertEquals("", media.withOnlyMedia(false).query)
        val photos = media.copy(content = Content.PHOTOS)
        assertTrue(!photos.onlyMedia)
        assertEquals("filter:images", photos.withOnlyMedia(false).query)
        assertEquals("filter:media", photos.withOnlyMedia(true).query)
    }

    @Test
    fun clearingAContentChoiceKeepsTheExclusionsAndTheOtherFields() {
        mapOf(Content.MEDIA to Filter.ONLY_MEDIA, Content.PHOTOS to Filter.PHOTOS, Content.VIDEOS to Filter.VIDEOS).forEach { (content, filter) ->
            val search = XAdvancedSearch(allWords = "flutter", fromAccounts = "alice", content = content, replies = Inclusion.EXCLUDE, excludeRetweets = true)
            val cleared = search.cleared(filter)
            assertEquals("flutter from:alice -filter:replies -filter:retweets", cleared.query)
            assertEquals(Content.ALL, cleared.content)
            assertEquals(listOf(Filter.ALL_WORDS, Filter.FROM_ACCOUNTS, Filter.REPLIES, Filter.EXCLUDE_RETWEETS), cleared.activeFilters)
            assertEquals("", search.valueOf(filter))
        }
        val videos = XAdvancedSearch(content = Content.VIDEOS)
        listOf(Filter.ONLY_MEDIA, Filter.PHOTOS, Filter.LINKS, Filter.REPLIES).forEach { assertEquals("filter:videos", videos.cleared(it).query, "$it is not the one set") }
    }

    @Test
    fun includingRepostsAndHidingRetweetsTurnEachOtherOff() {
        var search = XAdvancedSearch(allWords = "cats").withIncludeReposts(true)
        assertEquals("cats include:nativeretweets", search.query)
        search = search.withExcludeRetweets(true)
        assertEquals("cats -filter:retweets", search.query)
        search = search.withIncludeReposts(true)
        assertEquals("cats include:nativeretweets", search.query)
        assertEquals("cats", search.withIncludeReposts(false).query)
    }

    // ---- reading a query back ----

    @Test
    fun aRecentSearchReopensWithItsFilters() {
        val query = "cats from:alice filter:images -filter:replies -filter:retweets"
        val read = XAdvancedSearch.fromQuery(query)
        assertEquals("cats", read.allWords)
        assertEquals("alice", read.fromAccounts)
        assertEquals(Content.PHOTOS, read.content)
        assertEquals(Inclusion.EXCLUDE, read.replies)
        assertTrue(read.excludeRetweets)
        assertEquals(query, read.query)
        assertEquals("cats from:alice filter:videos -filter:replies -filter:retweets", read.copy(content = Content.VIDEOS).query)
    }

    @Test
    fun everyOperatorTheFormShowsIsReadWhereverItStands() {
        val read = XAdvancedSearch.fromQuery(
            "min_retweets:3 lang:fr since:2026-01-02 cats to:bob filter:replies until:2026-08-31 dogs " +
                "-filter:links min_replies:2 include:nativeretweets min_faves:10 from:alice filter:media",
        )
        assertEquals("cats dogs", read.allWords)
        assertEquals("fr", read.language)
        assertEquals("alice" to "bob", read.fromAccounts to read.toAccounts)
        assertEquals(listOf("2", "10", "3"), listOf(read.minReplies, read.minLikes, read.minRetweets))
        assertEquals(LocalDate.of(2026, 1, 2) to LocalDate.of(2026, 8, 31), read.since to read.until)
        assertEquals(Content.MEDIA, read.content)
        assertEquals(Inclusion.EXCLUDE to Inclusion.ONLY, read.links to read.replies)
        assertTrue(read.includeReposts)
        assertEquals(
            "cats dogs lang:fr from:alice to:bob min_replies:2 min_faves:10 min_retweets:3 since:2026-01-02 " +
                "until:2026-08-31 filter:media -filter:links filter:replies include:nativeretweets",
            read.query,
        )
    }

    @Test
    fun aQueryTheFormWroteReadsBackToTheSameFiltersAndText() {
        listOf(
            XAdvancedSearch(allWords = "cats", language = "fr"),
            XAdvancedSearch(fromAccounts = "alice bob", toAccounts = "carol", minLikes = "10", minRetweets = "3"),
            XAdvancedSearch(allWords = "cats OR dogs", minReplies = "2", since = LocalDate.of(2026, 1, 2), until = LocalDate.of(2026, 8, 31)),
            XAdvancedSearch(content = Content.PHOTOS, links = Inclusion.EXCLUDE, replies = Inclusion.ONLY),
            XAdvancedSearch(allWords = "\"quiet signal\" #flutter", content = Content.MEDIA, links = Inclusion.ONLY, replies = Inclusion.EXCLUDE, includeReposts = true),
            XAdvancedSearch(allWords = "news", language = "zh-cn", content = Content.VIDEOS, excludeRetweets = true),
        ).forEach { search ->
            val read = XAdvancedSearch.fromQuery(search.query)
            assertEquals(search.activeFilters.associateWith(search::valueOf), read.activeFilters.associateWith(read::valueOf), search.query)
            assertEquals(Triple(search.content, search.links, search.replies), Triple(read.content, read.links, read.replies))
            assertEquals(search.query, read.query)
        }
    }

    @Test
    fun aTypedQueryInTheFormsOrderIsRebuiltUnchanged() {
        listOf(
            "cats lang:fr",
            "from:alice filter:images -filter:links",
            "cats (from:alice OR from:bob) min_faves:10 since:2026-01-02 filter:replies",
            "news lang:en filter:videos -filter:replies include:nativeretweets",
            "\"exact phrase\" -spam #tag @dart to:bob -filter:retweets",
        ).forEach { query ->
            val read = XAdvancedSearch.fromQuery(query)
            assertTrue(read.hasOperators, query)
            assertEquals(query, read.query)
        }
    }

    @Test
    fun aTypedQueryInAnotherOrderIsRebuiltOnceIntoTheFormsOrder() {
        val read = XAdvancedSearch.fromQuery("filter:images lang:fr cats  from:alice")
        assertEquals("cats", read.allWords)
        assertEquals("cats lang:fr from:alice filter:images", read.query)
        assertEquals(read.query, XAdvancedSearch.fromQuery(read.query).query)
    }

    @Test
    fun operatorsTheFormCannotShowStayInTheWordsAsTyped() {
        listOf(
            "-lang:fr cats", "-from:alice", "lang:fr OR lang:en", "cats from:alice OR from:bob", "NOT filter:replies",
            "lang:fr lang:en", "filter:images filter:videos", "filter:replies -filter:replies", "include:nativeretweets -filter:retweets",
            "min_faves:0", "min_faves:05", "since:2026-02-30", "since:2026-01-02_10:00:00_UTC", "lang:FR", "from:@alice",
            "\"lang:fr\" cats", "(lang:fr OR lang:en)", "(from:alice OR to:bob)", "(from:alice  OR from:bob)",
            "#flutter @dart \"exact phrase\" -spam", "cats \"from:alice", "cats  dogs",
            "cats \"filter:images\"", "(cats filter:images)", "cats (filter:images OR filter:videos)", "cats (filter:images",
            "cats ) filter:images", "cats OR filter:images", "cats AND filter:videos", "cats \\ filter:images",
        ).forEach { query ->
            val read = XAdvancedSearch.fromQuery(query)
            assertEquals(query, read.allWords, query)
            assertTrue(!read.hasOperators, query)
            assertEquals(query, read.query)
        }
    }

    @Test
    fun aQuotedOrGroupedStartStaysWholeWhenATrailingFilterIsRead() {
        val read = XAdvancedSearch.fromQuery("\"filter:images\" (cats OR dogs) filter:links")
        assertEquals("\"filter:images\" (cats OR dogs)", read.allWords)
        assertEquals(Inclusion.ONLY, read.links)
        assertEquals("\"filter:images\" (cats OR dogs)", read.cleared(Filter.LINKS).query)
    }

    @Test
    fun theLanguagesAreXsCodes() {
        assertEquals(64, XAdvancedSearch.LANGUAGES.toSet().size)
        assertTrue(XAdvancedSearch.LANGUAGES.all { Regex("^[a-z]{2}$").matches(it) })
    }

    // ---- the results screen's search (XTA's SearchViewStore) ----

    @Test
    fun theFiltersStayWhileTheyStillWriteTheCommittedQuery() {
        val filters = XAdvancedSearch(allWords = "flutter", hashtags = "android").withOnlyMedia(true)
        val view = XSearchView.of("flutter").applied(filters).committed(filters.query)
        assertEquals("flutter #android filter:media", view.query)
        assertEquals(listOf(Filter.ALL_WORDS, Filter.HASHTAGS, Filter.ONLY_MEDIA), view.advanced.activeFilters)
    }

    @Test
    fun editingAQueryTheFiltersWroteDropsThem() {
        val view = XSearchView().applied(XAdvancedSearch(allWords = "flutter").withOnlyMedia(true)).drafted("flutter android").committed("flutter android")
        assertEquals("flutter android", view.query)
        assertTrue(view.advanced.activeFilters.isEmpty())
    }

    @Test
    fun oneFilterIsRemovedFromTheResultsWithoutTheOthers() {
        val view = XSearchView().applied(XAdvancedSearch(allWords = "flutter", content = Content.VIDEOS, replies = Inclusion.EXCLUDE, excludeRetweets = true))
        val without = view.without(Filter.REPLIES)
        assertEquals("flutter filter:videos -filter:retweets", without.query)
        assertEquals(listOf(Filter.ALL_WORDS, Filter.VIDEOS, Filter.EXCLUDE_RETWEETS), without.advanced.activeFilters)
    }

    @Test
    fun aTypedQueryShowsItsOperatorsAsFiltersAndKeepsItsText() {
        val typed = XSearchView().committed("lang:fr cats").drafted("lang:fr cats")
        assertEquals("lang:fr cats", typed.query)
        assertEquals(listOf(Filter.ALL_WORDS, Filter.LANGUAGE), typed.advanced.activeFilters)
        assertEquals("cats", typed.without(Filter.LANGUAGE).query)
        val edited = XSearchView().committed("cats lang:fr").drafted("cats lang:f")
        assertTrue(edited.advanced.activeFilters.isEmpty())
        assertEquals("de", edited.committed("cats lang:de").advanced.language)
    }

    @Test
    fun anOpenedSavedSearchShowsItsFiltersAndPlainWordsShowNone() {
        val saved = XSearchView.of(" from:alice filter:images ")
        assertEquals("from:alice filter:images", saved.query)
        assertEquals(listOf(Filter.FROM_ACCOUNTS, Filter.PHOTOS), saved.advanced.activeFilters)
        val words = XSearchView.of("#flutter dart")
        assertTrue(words.advanced.activeFilters.isEmpty())
        assertTrue(words.committed("cats OR from:alice").advanced.activeFilters.isEmpty())
    }
}
