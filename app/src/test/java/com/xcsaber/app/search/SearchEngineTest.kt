package com.xcsaber.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private val ddg = SearchProvider.DUCKDUCKGO

    @Test fun duckDuckGoIsTheDefaultProvider() {
        assertEquals("duckduckgo", SearchProvider.DEFAULT_ID)
        assertSame(ddg, SearchProvider.byId(null))
        assertSame(ddg, SearchProvider.byId("does-not-exist"))
        assertSame(SearchProvider.BING, SearchProvider.byId("bing"))
    }

    @Test fun everyProviderTemplateContainsTheQueryPlaceholder() {
        for (p in SearchProvider.ALL) {
            for (c in listOf(SearchCategory.WEB, SearchCategory.IMAGES, SearchCategory.VIDEOS, SearchCategory.NEWS)) {
                assertTrue("${p.id}/${c.name}", p.templateFor(c)!!.contains("{q}"))
            }
            assertTrue(p.web.startsWith("https://"))
        }
    }

    @Test fun buildsRealDuckDuckGoUrlsPerCategory() {
        assertEquals("https://duckduckgo.com/?q=hello+world&ia=web",
            SearchEngine.buildUrl(ddg, SearchCategory.WEB, "hello world"))
        assertEquals("https://duckduckgo.com/?q=cats&iax=images&ia=images",
            SearchEngine.buildUrl(ddg, SearchCategory.IMAGES, "cats"))
        assertEquals("https://duckduckgo.com/?q=cats&iax=videos&ia=videos",
            SearchEngine.buildUrl(ddg, SearchCategory.VIDEOS, "cats"))
        assertEquals("https://duckduckgo.com/?q=cats&iar=news&ia=news",
            SearchEngine.buildUrl(ddg, SearchCategory.NEWS, "cats"))
    }

    @Test fun mapsAndShoppingUseTheirOwnProviders() {
        assertEquals("https://www.openstreetmap.org/search?query=Paris",
            SearchEngine.buildUrl(ddg, SearchCategory.MAPS, "Paris"))
        assertEquals("https://www.bing.com/shop?q=red+shoes",
            SearchEngine.buildUrl(ddg, SearchCategory.SHOPPING, "red shoes"))
        assertEquals("geo:0,0?q=cafe+near+me", SearchEngine.geoUri("cafe near me"))
        assertEquals("https://www.openstreetmap.org/directions?to=Eiffel+Tower",
            SearchEngine.directionsUrl("Eiffel Tower"))
    }

    @Test fun queriesAreUrlEncoded() {
        val url = SearchEngine.buildUrl(ddg, SearchCategory.WEB, "a&b=c #1")
        assertTrue(url, url.contains("q=a%26b%3Dc+%231"))
        assertFalse(url.contains("&b=c"))
    }

    @Test fun otherProvidersWork() {
        assertEquals("https://www.bing.com/news/search?q=x",
            SearchEngine.buildUrl(SearchProvider.BING, SearchCategory.NEWS, "x"))
        assertEquals("https://search.brave.com/images?q=x",
            SearchEngine.buildUrl(SearchProvider.BRAVE, SearchCategory.IMAGES, "x"))
    }

    @Test fun resolveDecidesBetweenUrlAndSearch() {
        val url = SearchEngine.resolve("example.com", ddg, SearchCategory.WEB)
        assertEquals(SearchEngine.Target.Url("https://example.com"), url)
        val search = SearchEngine.resolve("kotlin tutorial", ddg, SearchCategory.IMAGES) as SearchEngine.Target.Search
        assertEquals("kotlin tutorial", search.query)
        assertTrue(search.url.contains("iax=images"))
        // A dangerous scheme becomes a harmless search instead of being executed.
        val js = SearchEngine.resolve("javascript:alert(1)", ddg, SearchCategory.WEB)
        assertTrue(js is SearchEngine.Target.Search)
    }

    @Test fun extractsQueryBackFromResultUrls() {
        assertEquals("hello world", SearchEngine.extractQuery("https://duckduckgo.com/?q=hello+world&ia=web"))
        assertEquals("Paris", SearchEngine.extractQuery("https://www.openstreetmap.org/search?query=Paris"))
        assertNull(SearchEngine.extractQuery("https://example.com/"))
    }

    @Test fun recognisesResultsPages() {
        assertTrue(SearchEngine.isResultsUrl("https://duckduckgo.com/?q=x"))
        assertTrue(SearchEngine.isResultsUrl("https://www.bing.com/search?q=x"))
        assertFalse(SearchEngine.isResultsUrl("https://duckduckgo.com/"))
        assertFalse(SearchEngine.isResultsUrl("https://example.com/?q=x"))
    }

    @Test fun categoryFollowsTheResultsUrl() {
        for (c in listOf(SearchCategory.WEB, SearchCategory.IMAGES, SearchCategory.VIDEOS, SearchCategory.NEWS,
            SearchCategory.MAPS, SearchCategory.SHOPPING)) {
            assertEquals(c.name, c, SearchEngine.categoryOf(SearchEngine.buildUrl(ddg, c, "x")))
        }
        assertEquals(SearchCategory.IMAGES, SearchEngine.categoryOf("https://www.bing.com/images/search?q=x"))
        assertEquals(SearchCategory.NEWS, SearchEngine.categoryOf("https://search.brave.com/news?q=x"))
        assertEquals(SearchCategory.WEB, SearchEngine.categoryOf("https://duckduckgo.com/?q=news&ia=web"))
    }

    @Test fun categoryNamesRoundTrip() {
        assertEquals(SearchCategory.NEWS, SearchCategory.fromName("NEWS"))
        assertEquals(SearchCategory.WEB, SearchCategory.fromName("nonsense"))
        assertEquals(SearchCategory.WEB, SearchCategory.fromName(null))
    }

    @Test fun suggestionParserReadsOpenSearchFormat() {
        assertEquals(listOf("hello", "help"), SuggestionClient.parse("[\"hel\",[\"hello\",\"help\",\"\"]]"))
        assertTrue(SuggestionClient.parse("not json").isEmpty())
        assertTrue(SuggestionClient.parse("[]").isEmpty())
        val many = "[\"q\",[" + (1..20).joinToString(",") { "\"s$it\"" } + "]]"
        assertEquals(8, SuggestionClient.parse(many).size)
    }
}
