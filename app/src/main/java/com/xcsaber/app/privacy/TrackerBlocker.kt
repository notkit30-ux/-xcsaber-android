package com.xcsaber.app.privacy

import java.util.Locale

/**
 * Host-based tracker/ad blocker. A request is blocked when its host, or any parent domain of
 * it, is in the blocklist. The list is parsed from plain-domain or hosts-file format, so the
 * bundled starter list and downloaded maintained lists share one code path.
 *
 * Only sub-resources are ever blocked (the caller must not pass main-frame navigations),
 * so a site the user deliberately opens is never refused by this class.
 */
class TrackerBlocker {

    @Volatile
    private var domains: Set<String> = emptySet()

    val size: Int get() = domains.size

    fun load(lines: Sequence<String>) {
        domains = parse(lines)
    }

    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        var h = host.lowercase(Locale.ROOT).trimEnd('.')
        val set = domains
        // Check "ads.example.com", then "example.com", then "com" (never in the set).
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /**
     * Decides whether a sub-resource request should be blocked. Requests to the same site as
     * the page (first-party) are never blocked, so a tracker's own website still works.
     */
    fun shouldBlock(requestHost: String?, pageHost: String?): Boolean {
        if (requestHost.isNullOrBlank() || !isBlocked(requestHost)) return false
        val r = requestHost.lowercase(Locale.ROOT).trimEnd('.')
        val p = pageHost?.lowercase(Locale.ROOT)?.trimEnd('.')
        if (!p.isNullOrEmpty() && (r == p || r.endsWith(".$p") || p.endsWith(".$r"))) return false
        return true
    }

    companion object {
        private val HOST_PREFIXES = setOf("0.0.0.0", "127.0.0.1", "::1", "::")
        private val DOMAIN_REGEX = Regex("^[a-z0-9]([a-z0-9._-]*[a-z0-9])?\\.[a-z]{2,63}$")
        private val IGNORED_HOSTS = setOf(
            "localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost",
            "ip6-loopback", "0.0.0.0"
        )

        fun parse(lines: Sequence<String>): Set<String> {
            val out = HashSet<String>()
            for (raw in lines) {
                var line = raw.substringBefore('#').trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
                val parts = line.split(Regex("\\s+"))
                line = if (parts.size >= 2 && parts[0] in HOST_PREFIXES) parts[1] else parts[0]
                line = line.lowercase(Locale.ROOT).trimStart('.').removePrefix("*.")
                if (line in IGNORED_HOSTS) continue
                if (DOMAIN_REGEX.matches(line)) out.add(line)
            }
            return out
        }
    }
}
