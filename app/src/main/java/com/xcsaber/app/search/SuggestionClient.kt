package com.xcsaber.app.search

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches live search suggestions from the provider's public suggestion endpoint
 * (DuckDuckGo's /ac/ endpoint needs no API key). Must be called off the main thread.
 * Returns an empty list on any failure so the UI simply shows recent searches.
 */
object SuggestionClient {

    fun fetch(provider: SearchProvider, query: String): List<String> {
        val template = provider.suggestUrl ?: SearchProvider.DUCKDUCKGO.suggestUrl ?: return emptyList()
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(template.replace("{q}", UrlUtils.encode(text)))
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "XCSABER/1.0")
            }
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /** Parses the OpenSearch suggestion format: ["query", ["s1", "s2", ...]]. */
    fun parse(body: String): List<String> = try {
        val root = JSONArray(body)
        val list = root.optJSONArray(1) ?: JSONArray()
        (0 until list.length()).mapNotNull { list.optString(it).takeIf { s -> s.isNotBlank() } }.take(8)
    } catch (e: Exception) {
        emptyList()
    }
}
