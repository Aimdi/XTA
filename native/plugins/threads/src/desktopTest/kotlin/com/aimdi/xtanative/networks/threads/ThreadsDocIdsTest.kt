package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The doc ids of threads.com's queries, read from made-up bundle text in
 * each shape Meta's build writes; no bundle was ever seen from here, so
 * every id and name below is invented. A mutation is never read, under any
 * shape.
 */
class ThreadsDocIdsTest {
    private val idModule =
        """__d("BarcelonaInlineRepliesExpansionQuery_threadsRelayOperation",[],(function(a,b,c,d,e,f){e.exports="7000000000000001"}),null);"""
    private val instagramModule =
        """__d("BarcelonaAboutThisProfileDialogQuery_instagramRelayOperation",[],(function(t,n,r,o,a,i){a.exports = "7000000000000002"}),null);"""
    private val parametersModule =
        """__d("BarcelonaSearchTrendingTopicsSectionQuery${'$'}Parameters",[],(function(a,b,c,d,e,f){"use strict";a={kind:"PreloadableConcreteRequest",""" +
            """params:{id:"7000000000000003",metadata:{},name:"BarcelonaSearchTrendingTopicsSectionQuery",operationKind:"query",text:null}};e.exports=a}),null);"""
    private val graphqlModule =
        """__d("BarcelonaPostTranslationQuery.graphql",["relay-runtime"],(function(a,b,c,d,e,f){"use strict";a=function(){""" +
            """var a=[{defaultValue:null,kind:"LocalArgument",name:"postID"},{defaultValue:null,kind:"LocalArgument",name:"targetLanguage"}],""" +
            """b=[{alias:null,args:null,concreteType:"XDTTranslation",kind:"LinkedField",name:"xdt_translate_post",plural:!1,selections:[]}];""" +
            """return{fragment:{argumentDefinitions:a,kind:"Fragment",metadata:null,name:"BarcelonaPostTranslationQuery",selections:b},kind:"Request",""" +
            """operation:{argumentDefinitions:a,kind:"Operation",name:"BarcelonaPostTranslationQuery",selections:b},""" +
            """params:{id:"7000000000000004",metadata:{is_distillery:!0,nested:{a:1}},name:"BarcelonaPostTranslationQuery",operationKind:"query",text:null,""" +
            """providedVariables:{__relay_internal__pv__BarcelonaIsLoggedInrelayprovider:b("x")}}}}();e.exports=a}),null);"""

    @Test
    fun aModuleOfTheIdAloneNamesItsQueryWhateverFollowsTheName() {
        assertEquals(
            listOf(
                ThreadsOperation("BarcelonaInlineRepliesExpansionQuery", "7000000000000001"),
                ThreadsOperation("BarcelonaAboutThisProfileDialogQuery", "7000000000000002"),
            ),
            ThreadsDocIds.operations(idModule + instagramModule),
        )
    }

    @Test
    fun aParametersModuleAndARequestWrittenOutNameTheirIdsOnce() {
        assertEquals(
            mapOf("BarcelonaSearchTrendingTopicsSectionQuery" to "7000000000000003", "BarcelonaPostTranslationQuery" to "7000000000000004"),
            ThreadsDocIds.ids(parametersModule + graphqlModule),
        )
        assertEquals(2, ThreadsDocIds.operations(parametersModule + graphqlModule).size, "the id in params is not read twice")
    }

    @Test
    fun aRequestWrittenAsJsonIsRead() {
        val json = """{"params":{"id":"7000000000000005","metadata":{},"name":"BarcelonaCustomFeedPickerPageQuery","operationKind":"query","text":null}}"""
        assertEquals(mapOf("BarcelonaCustomFeedPickerPageQuery" to "7000000000000005"), ThreadsDocIds.ids(json))
    }

    @Test
    fun aMutationIsNeverReadUnderAnyShape() {
        val writes = """__d("BarcelonaLikeMutation_threadsRelayOperation",[],(function(a,b,c,d,e,f){e.exports="8000000000000001"}),null);""" +
            """__d("BarcelonaFollowMutation${'$'}Parameters",[],(function(a,b,c,d,e,f){a={params:{id:"8000000000000002",metadata:{},name:"BarcelonaFollowMutation",operationKind:"mutation"}};e.exports=a}),null);""" +
            // A write that calls itself a query is still a write, and one whose name only holds the word is no query.
            """__d("BarcelonaSneakyQuery${'$'}Parameters",[],(function(a,b,c,d,e,f){a={params:{id:"8000000000000003",metadata:{},name:"BarcelonaSneakyQuery",operationKind:"mutation"}};e.exports=a}),null);""" +
            """__d("BarcelonaMutationQuery_threadsRelayOperation",[],(function(a,b,c,d,e,f){e.exports="8000000000000004"}),null);""" +
            """__d("BarcelonaTypingSubscription_threadsRelayOperation",[],(function(a,b,c,d,e,f){e.exports="8000000000000005"}),null);"""
        assertEquals(emptyList(), ThreadsDocIds.operations(writes))
        assertEquals(listOf("BarcelonaInlineRepliesExpansionQuery"), ThreadsDocIds.operations(writes + idModule).map { it.name })
    }

    @Test
    fun anIdIsNotTakenFromTheModuleBeforeOrFromOutsideParams() {
        val referenced = """__d("Other",[],(function(){a={id:"9000000000000001"}}),null);""" +
            """__d("BarcelonaProfileUserInterestsDialogUserQuery.graphql",["BarcelonaProfileUserInterestsDialogUserQuery_threadsRelayOperation"],(function(a,b){""" +
            """a={params:{id:b("BarcelonaProfileUserInterestsDialogUserQuery_threadsRelayOperation"),metadata:{},name:"BarcelonaProfileUserInterestsDialogUserQuery",operationKind:"query"}}}),null);"""
        assertEquals(emptyList(), ThreadsDocIds.operations(referenced), "the id is another module's to tell")
        assertEquals(emptyList(), ThreadsDocIds.operations("""{userid:"9000000000000002",name:"BarcelonaXQuery",operationKind:"query"}"""))
    }

    @Test
    fun textInAnotherShapeHasNoOperations() {
        listOf("", "<html>", "__d(\"", "__d(\"BarcelonaXQuery", "exports=\"1\"", "name:\"BarcelonaXQuery\",operationKind:\"query\"").forEach {
            assertEquals(emptyList(), ThreadsDocIds.operations(it), it)
        }
    }

    @Test
    fun aQuerysDefinitionTellsItsVariablesAndFieldsByNameOnly() {
        val js = idModule + graphqlModule + parametersModule
        assertEquals(listOf("postID", "targetLanguage"), ThreadsDocIds.variables(js, "BarcelonaPostTranslationQuery"))
        assertEquals(listOf("BarcelonaIsLoggedInrelayprovider"), ThreadsDocIds.providedVariables(js, "BarcelonaPostTranslationQuery"))
        assertEquals(listOf("xdt_translate_post"), ThreadsDocIds.fields(js, "BarcelonaPostTranslationQuery"))
        assertEquals(emptyList(), ThreadsDocIds.variables(js, "BarcelonaInlineRepliesExpansionQuery"), "an id module declares nothing")
        assertEquals(emptyList(), ThreadsDocIds.variables(js, "BarcelonaLikeMutation"))
    }

    @Test
    fun aPagesBundlesAreItsScriptsAndItsResourceMapOnMetasStaticHostsOnly() {
        val html = """<html><head>
            <link rel="preload" href="https://static.cdninstagram.com/rsrc.php/v4/yA/r/first.js?_nc_x=1&amp;v=2" as="script">
            <script src="https://static.cdninstagram.com/rsrc.php/v4/yB/r/second.js" async></script>
            <script src="https://evil.example/rsrc.php/x.js"></script>
            <link rel="stylesheet" href="https://static.cdninstagram.com/rsrc.php/v4/yC/l/style.css">
            <script type="application/json" data-sjs>{"require":[["Bootloader","handlePayload",[],[{"rsrcMap":{
              "aB1":{"type":"js","src":"https:\/\/static.cdninstagram.com\/rsrc.php\/v4\/yD\/r\/third.js?_nc_x=a&b=c"},
              "cD2":{"type":"css","src":"https:\/\/static.cdninstagram.com\/rsrc.php\/v4\/yE\/l\/other.css"},
              "eF3":{"src":"https:\/\/static.threads.com\/rsrc.php\/v4\/yF\/r\/about.js","type":"js"},
              "gH4":{"type":"js","src":"https:\/\/static.cdninstagram.com.evil.example\/x.js"}},
              "componentMap":{"BarcelonaAboutThisProfileDialog.react":{"r":["eF3","zz9"],"be":1},"BarcelonaOther.react":{"r":["aB1"],"be":1}}}]]]}</script>
            </head></html>"""
        assertEquals(
            listOf(
                "https://static.threads.com/rsrc.php/v4/yF/r/about.js",
                "https://static.cdninstagram.com/rsrc.php/v4/yA/r/first.js?_nc_x=1&v=2",
                "https://static.cdninstagram.com/rsrc.php/v4/yB/r/second.js",
                "https://static.cdninstagram.com/rsrc.php/v4/yD/r/third.js?_nc_x=a&b=c",
            ),
            ThreadsDocIds.bundles(html, listOf("BarcelonaAboutThisProfileDialogQuery")),
        )
        assertTrue(ThreadsDocIds.bundles("<html></html>").isEmpty())
    }
}
