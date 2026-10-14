package com.xcsaber.app.search

/** The category tabs shown under the search bar. */
enum class SearchCategory(val label: String) {
    WEB("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    NEWS("News"),
    MAPS("Maps"),
    SHOPPING("Shopping");

    companion object {
        fun fromName(name: String?): SearchCategory =
            values().firstOrNull { it.name == name } ?: WEB
    }
}
