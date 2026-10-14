package com.xcsaber.app.search

/**
 * Builds real provider URLs for a query. Everything here is a plain URL builder:
 * results are rendered by the provider's own pages in the WebView.
 *
 * Maps use OpenStreetMap (free, no API key). Shopping uses Bing Shopping, because
 * DuckDuckGo has no shopping vertical; the app says so in Settings.
 */
object SearchEngine {

    const val MAPS_HOME = "https://www.openstreetmap.org/"
    private const val MAPS_SEARCH = "https://www.openstreetmap.org/search?query={q}"
    private const val SHOPPING = "https://www.bing.com/shop?q={q}"

    fun buildUrl(provider: SearchProvider, category: SearchCategory, query: String): String {
        val q = UrlUtils.encode(query.trim())
        val template = when (category) {
            SearchCategory.MAPS -> MAPS_SEARCH
            SearchCategory.SHOPPING -> SHOPPING
            else -> provider.templateFor(category) ?: provider.web
        }
        return template.replace("{q}", q)
    }

    /** geo: URI that opens any installed maps app with directions/search for the place. */
    fun geoUri(query: String): String = "geo:0,0?q=" + UrlUtils.encode(query.trim())

    /** What the address bar should do with the text the user typed. */
    sealed class Target {
        data class Url(val url: String) : Target()
        data class Search(val url: String, val query: String) : Target()
    }

    fun resolve(input: String, provider: SearchProvider, category: SearchCategory): Target {
        val nav = UrlUtils.toNavigableUrl(input)
        return if (nav != null) Target.Url(nav)
        else Target.Search(buildUrl(provider, category, input), input.trim())
    }

    /** Extracts the search query back out of a result URL, so the search bar can show it. */
    fun extractQuery(url: String): String? {
        val idx = url.indexOf("?")
        if (idx < 0) return null
        val params = url.substring(idx + 1).substringBefore('#').split('&')
        for (key in listOf("q", "query")) {
            val hit = params.firstOrNull { it.startsWith("$key=") } ?: continue
            return try {
                java.net.URLDecoder.decode(hit.substringAfter('='), "UTF-8")
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private val SEARCH_HOSTS = setOf(
        "duckduckgo.com", "bing.com", "search.brave.com", "startpage.com", "openstreetmap.org"
    )

    /** True when [url] is a results page of one of the supported providers. */
    fun isResultsUrl(url: String): Boolean {
        val host = UrlUtils.hostOf(url) ?: return false
        val known = SEARCH_HOSTS.any { host == it || host.endsWith(".$it") }
        return known && extractQuery(url) != null
    }

    /** Works out which category tab a results page belongs to, so the tab strip can follow it. */
    fun categoryOf(url: String): SearchCategory {
        val u = url.lowercase()
        val host = UrlUtils.hostOf(u) ?: ""
        return when {
            host.endsWith("openstreetmap.org") -> SearchCategory.MAPS
            host.endsWith("bing.com") && u.contains("/shop") -> SearchCategory.SHOPPING
            u.contains("iax=images") || u.contains("/images") || u.contains("cat=pics") -> SearchCategory.IMAGES
            u.contains("iax=videos") || u.contains("/videos") || u.contains("cat=video") -> SearchCategory.VIDEOS
            u.contains("iar=news") || u.contains("ia=news") || u.contains("/news") || u.contains("cat=news") -> SearchCategory.NEWS
            else -> SearchCategory.WEB
        }
    }

    /** OpenStreetMap directions page with the destination filled in. */
    fun directionsUrl(destination: String): String =
        "https://www.openstreetmap.org/directions?to=" + UrlUtils.encode(destination.trim())
}
