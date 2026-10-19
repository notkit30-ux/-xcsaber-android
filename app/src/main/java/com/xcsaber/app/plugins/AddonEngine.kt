package com.xcsaber.app.plugins

/**
 * A script add-on: a small, reviewed content script that ships inside the app (assets/addons/<id>.js).
 * Add-ons are NOT downloaded and are not Chrome extensions; they only change how a web page looks
 * or behaves inside its own tab. No add-on can reach XCSABER's data or the Android APIs.
 */
data class ScriptAddon(
    val id: String,
    val name: String,
    val description: String,
    val category: PluginCategory,
    val iconRes: Int,
    val accent: PluginAccent,
    /** Host patterns: "*" for every site, otherwise "example.com" (also matches subdomains). */
    val matches: List<String> = listOf("*"),
) {
    val scopeLabel: String get() = if (matches.contains("*")) "All websites" else matches.joinToString(", ")
}

/** Pure logic for choosing and wrapping add-on scripts. */
object AddonEngine {

    fun matches(addon: ScriptAddon, host: String?): Boolean {
        if (addon.matches.contains("*")) return true
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        return addon.matches.any { p ->
            val pat = p.lowercase()
            h == pat || h.endsWith(".$pat")
        }
    }

    /** True only for ordinary web pages: add-ons never run on error pages, about: or file: URLs. */
    fun isWebUrl(url: String) = url.startsWith("https://", true) || url.startsWith("http://", true)

    fun hostOf(url: String): String? {
        val rest = url.substringAfter("://", "")
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val noUser = authority.substringAfterLast('@')
        val host = if (noUser.startsWith("[")) noUser.substringBefore(']') + "]" else noUser.substringBefore(':')
        return host.ifBlank { null }
    }

    /** Enabled add-ons that apply to [url], in catalog order. */
    fun applicable(catalog: List<ScriptAddon>, enabled: Set<String>, url: String): List<ScriptAddon> {
        if (enabled.isEmpty() || !isWebUrl(url)) return emptyList()
        val host = hostOf(url)
        return catalog.filter { it.id in enabled && matches(it, host) }
    }

    /**
     * Wraps one add-on body so that it runs once per page, fails on its own without breaking the
     * page or the other add-ons, and has two helpers: xcCss(text) and xcReady(fn).
     */
    fun wrap(id: String, body: String): String {
        require(id.matches(Regex("[a-z0-9-]{1,40}"))) { "bad add-on id" }
        return "(function(){try{var R=(window.__xcAddons=window.__xcAddons||{});if(R['$id'])return;R['$id']=1;" +
            "function xcCss(t){var s=document.createElement('style');s.setAttribute('data-xc-addon','$id');" +
            "s.textContent=t;(document.head||document.documentElement).appendChild(s);}" +
            "function xcReady(f){if(document.readyState==='loading')" +
            "document.addEventListener('DOMContentLoaded',function(){try{f()}catch(e){}});else try{f()}catch(e){}}\n" +
            body + "\n}catch(e){}})();"
    }

    /** One script for everything that applies to [url], or null when nothing does. */
    fun scriptFor(addons: List<ScriptAddon>, bodyOf: (String) -> String?): String? {
        val parts = addons.mapNotNull { a -> bodyOf(a.id)?.let { wrap(a.id, it) } }
        return if (parts.isEmpty()) null else parts.joinToString("\n")
    }
}
