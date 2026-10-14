package com.xcsaber.app.search

/**
 * A configurable search provider. Results are the provider's own real result pages
 * loaded in the WebView; XCSABER never invents results. Placeholders: {q} is replaced
 * by the URL-encoded query. A category with no template falls back to [fallbackWeb].
 */
data class SearchProvider(
    val id: String,
    val name: String,
    val web: String,
    val images: String,
    val videos: String,
    val news: String,
    val suggestUrl: String?,
    val home: String
) {
    fun templateFor(category: SearchCategory): String? = when (category) {
        SearchCategory.WEB -> web
        SearchCategory.IMAGES -> images
        SearchCategory.VIDEOS -> videos
        SearchCategory.NEWS -> news
        SearchCategory.MAPS, SearchCategory.SHOPPING -> null
    }

    companion object {
        const val DEFAULT_ID = "duckduckgo"

        val DUCKDUCKGO = SearchProvider(
            id = "duckduckgo",
            name = "DuckDuckGo",
            web = "https://duckduckgo.com/?q={q}&ia=web",
            images = "https://duckduckgo.com/?q={q}&iax=images&ia=images",
            videos = "https://duckduckgo.com/?q={q}&iax=videos&ia=videos",
            news = "https://duckduckgo.com/?q={q}&iar=news&ia=news",
            suggestUrl = "https://duckduckgo.com/ac/?q={q}&type=list",
            home = "https://duckduckgo.com/"
        )

        val BING = SearchProvider(
            id = "bing",
            name = "Bing",
            web = "https://www.bing.com/search?q={q}",
            images = "https://www.bing.com/images/search?q={q}",
            videos = "https://www.bing.com/videos/search?q={q}",
            news = "https://www.bing.com/news/search?q={q}",
            suggestUrl = null,
            home = "https://www.bing.com/"
        )

        val BRAVE = SearchProvider(
            id = "brave",
            name = "Brave Search",
            web = "https://search.brave.com/search?q={q}",
            images = "https://search.brave.com/images?q={q}",
            videos = "https://search.brave.com/videos?q={q}",
            news = "https://search.brave.com/news?q={q}",
            suggestUrl = null,
            home = "https://search.brave.com/"
        )

        val STARTPAGE = SearchProvider(
            id = "startpage",
            name = "Startpage",
            web = "https://www.startpage.com/do/search?q={q}",
            images = "https://www.startpage.com/do/search?q={q}&cat=pics",
            videos = "https://www.startpage.com/do/search?q={q}&cat=video",
            news = "https://www.startpage.com/do/search?q={q}&cat=news",
            suggestUrl = null,
            home = "https://www.startpage.com/"
        )

        val ALL = listOf(DUCKDUCKGO, BING, BRAVE, STARTPAGE)

        fun byId(id: String?): SearchProvider = ALL.firstOrNull { it.id == id } ?: DUCKDUCKGO
    }
}
