package com.xcsaber.app.search

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Pure-Kotlin helpers for deciding whether text typed in the address bar is a
 * URL or a search query, and for validating URL schemes. No Android classes are
 * used here so everything can be unit tested on the JVM.
 */
object UrlUtils {

    /** Schemes the in-app WebView is allowed to load directly. */
    private val WEB_SCHEMES = setOf("http", "https")

    /** Schemes that are handed to other apps through a safe external intent. */
    private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "geo", "market")

    /** Schemes that must never be opened, loaded or forwarded. */
    private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "intent", "blob", "ftp")

    private val DOMAIN_REGEX =
        Regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(:\\d{1,5})?([/?#].*)?$", RegexOption.IGNORE_CASE)
    private val IPV4_REGEX = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?([/?#].*)?$")
    private val LOCALHOST_REGEX = Regex("^localhost(:\\d{1,5})?([/?#].*)?$", RegexOption.IGNORE_CASE)

    enum class Kind { WEB_URL, EXTERNAL, BLOCKED, SEARCH }

    fun schemeOf(input: String): String? {
        val trimmed = input.trim()
        val idx = trimmed.indexOf(':')
        if (idx <= 0) return null
        val scheme = trimmed.substring(0, idx).lowercase(Locale.ROOT)
        if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
        return scheme
    }

    /** Classifies a URL that came from a link or an intent (not from the address bar). */
    fun classifyScheme(url: String): Kind {
        val scheme = schemeOf(url) ?: return Kind.BLOCKED
        return when {
            scheme in WEB_SCHEMES -> Kind.WEB_URL
            scheme in EXTERNAL_SCHEMES -> Kind.EXTERNAL
            else -> Kind.BLOCKED
        }
    }

    fun isBlockedScheme(url: String): Boolean {
        val scheme = schemeOf(url) ?: return false
        return scheme in BLOCKED_SCHEMES
    }

    /** True if the text looks like something the user wants to navigate to rather than search for. */
    fun looksLikeUrl(input: String): Boolean {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return false
        val scheme = schemeOf(text)
        if (scheme != null && text.startsWith("$scheme://")) return scheme in WEB_SCHEMES
        return DOMAIN_REGEX.matches(text) || IPV4_REGEX.matches(text) || LOCALHOST_REGEX.matches(text)
    }

    /**
     * Turns address-bar text into either a navigable web URL or null (meaning "search for it").
     * Dangerous schemes such as javascript: and file: return null so they become a plain search
     * rather than being executed.
     */
    fun toNavigableUrl(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (isBlockedScheme(text)) return null
        if (!looksLikeUrl(text)) return null
        val withScheme = if (text.contains("://")) text else "https://$text"
        return if (isValidWebUrl(withScheme)) withScheme else null
    }

    fun isValidWebUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            scheme in WEB_SCHEMES && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
    } catch (e: Exception) {
        null
    }

    fun isHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

    /** Upgrades http:// to https:// (used by HTTPS-only mode). */
    fun upgradeToHttps(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    /** Pulls the https fallback address out of an "intent://...#Intent;S.browser_fallback_url=..." link. */
    fun fallbackFromIntentUrl(url: String): String? {
        if (!url.startsWith("intent:", ignoreCase = true)) return null
        val marker = "S.browser_fallback_url="
        val i = url.indexOf(marker)
        if (i < 0) return null
        val raw = url.substring(i + marker.length).substringBefore(';')
        val decoded = try {
            java.net.URLDecoder.decode(raw, "UTF-8")
        } catch (e: Exception) {
            return null
        }
        return if (isValidWebUrl(decoded)) decoded else null
    }

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    /** Short title-like label for a URL, e.g. "example.com". */
    fun displayHost(url: String): String = hostOf(url) ?: url
}
