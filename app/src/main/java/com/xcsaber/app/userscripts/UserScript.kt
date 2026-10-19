package com.xcsaber.app.userscripts

import com.xcsaber.app.data.KvStore
import org.json.JSONArray
import org.json.JSONObject

enum class RunAt(val key: String) {
    START("document-start"), END("document-end"), IDLE("document-idle");
    companion object { fun from(s: String?) = values().firstOrNull { it.key == s?.trim()?.lowercase() } ?: IDLE }
}

/** A user-installed script in the Tampermonkey / Greasemonkey format. */
data class UserScript(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val matches: List<String>,
    val includes: List<String>,
    val excludes: List<String>,
    val grants: List<String>,
    val runAt: RunAt,
    val code: String,
    val enabled: Boolean = true,
    val installedAt: Long = 0L,
    val sourceUrl: String = "",
) {
    /** True if the script asks to run on every website. */
    val runsEverywhere: Boolean
        get() = (matches + includes).any { it == "*" || it == "<all_urls>" || it.matches(Regex("(\\*|https?\\*?)://\\*/?\\*?")) }

    /** Short "where it runs" text for lists. */
    val scopeLabel: String
        get() {
            val all = matches + includes
            return when {
                runsEverywhere -> "All websites"
                all.size <= 2 -> all.joinToString(", ")
                else -> all.take(2).joinToString(", ") + " +${all.size - 2} more"
            }
        }

    /** GM features the script asks for that XCSABER does not provide. */
    val unsupportedGrants: List<String>
        get() = grants.filter { g -> g in UNSUPPORTED }

    companion object {
        private val UNSUPPORTED = setOf(
            "GM_xmlhttpRequest", "GM.xmlHttpRequest", "GM_download", "GM.download", "GM_cookie", "GM.cookie",
            "GM_notification", "GM.notification", "GM_getResourceText", "GM.getResourceText",
            "GM_getResourceURL", "GM.getResourceUrl", "GM_addValueChangeListener", "GM_webRequest",
        )
    }
}

/** Result of reading a script's metadata block. */
sealed class ParseResult {
    data class Ok(val script: UserScript, val warnings: List<String>) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

object UserScriptParser {
    const val MAX_CODE_CHARS = 128 * 1024

    private val START = Regex("^\\s*//\\s*==UserScript==\\s*$")
    private val END = Regex("^\\s*//\\s*==/UserScript==\\s*$")
    private val LINE = Regex("^\\s*//\\s*@([A-Za-z][A-Za-z0-9:_-]*)\\s*(.*?)\\s*$")

    /** True for web addresses that point straight at a userscript file (Greasy Fork, GitHub raw, ...). */
    fun looksLikeUserScriptUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val path = url.substringBefore('#').substringBefore('?')
        return path.endsWith(".user.js", ignoreCase = true)
    }

    fun parse(source: String, sourceUrl: String = "", now: Long = 0L): ParseResult {
        val text = source.removePrefix("﻿")
        if (text.isBlank()) return ParseResult.Error("The script is empty.")
        if (text.length > MAX_CODE_CHARS) return ParseResult.Error("The script is too large (limit ${MAX_CODE_CHARS / 1024} KB).")
        val lines = text.lines()
        val s = lines.indexOfFirst { START.matches(it) }
        val e = lines.indexOfFirst { END.matches(it) }
        if (s < 0 || e < 0 || e < s) {
            return ParseResult.Error("This isn't a userscript: the \"// ==UserScript==\" header is missing.")
        }
        val meta = HashMap<String, MutableList<String>>()
        for (i in s + 1 until e) {
            val m = LINE.matchEntire(lines[i]) ?: continue
            meta.getOrPut(m.groupValues[1].lowercase()) { ArrayList() }.add(m.groupValues[2])
        }
        fun one(k: String) = meta[k]?.firstOrNull()?.takeIf { it.isNotBlank() }
        fun many(k: String) = (meta[k] ?: emptyList<String>()).filter { it.isNotBlank() }

        val name = clean(one("name") ?: "Untitled script", 80)
        val matches = many("match")
        val includes = many("include")
        if (matches.isEmpty() && includes.isEmpty()) {
            return ParseResult.Error("This script has no @match or @include line, so it would never run.")
        }
        val warnings = ArrayList<String>()
        val grants = many("grant")
        val tmp = UserScript(
            id = idFor(name, one("namespace") ?: ""),
            name = name,
            description = clean(one("description") ?: "", 200),
            version = clean(one("version") ?: "", 24),
            matches = matches, includes = includes,
            excludes = many("exclude") + many("exclude-match"),
            grants = grants,
            runAt = RunAt.from(one("run-at")),
            code = text,
            enabled = true,
            installedAt = now,
            sourceUrl = sourceUrl,
        )
        if (tmp.unsupportedGrants.isNotEmpty()) {
            warnings.add("Uses ${tmp.unsupportedGrants.joinToString(", ")}, which XCSABER does not provide. Parts of the script may not work.")
        }
        if (many("require").isNotEmpty()) warnings.add("Uses @require libraries, which are not loaded.")
        return ParseResult.Ok(tmp, warnings)
    }

    private fun clean(s: String, max: Int) = s.filter { !it.isISOControl() }.trim().take(max)

    /** Stable id from name + namespace, so reinstalling the same script updates it. */
    fun idFor(name: String, namespace: String): String {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (c in "$name|$namespace") { h = (h xor c.code.toLong()) * 0x100000001b3L }
        return "us-" + java.lang.Long.toHexString(h)
    }
}

/** Decides whether a script applies to a page address. */
object UserScriptMatcher {

    fun applies(script: UserScript, url: String): Boolean {
        if (!(url.startsWith("https://", true) || url.startsWith("http://", true))) return false
        val u = url.substringBefore('#')
        val included = script.matches.any { matchPattern(it, u) } || script.includes.any { includeMatches(it, u) }
        if (!included) return false
        return script.excludes.none { matchPattern(it, u) || includeMatches(it, u) }
    }

    /** Chrome-style match pattern: scheme://host/path, with * wildcards, or <all_urls>. */
    fun matchPattern(pattern: String, url: String): Boolean {
        val p = pattern.trim()
        if (p == "<all_urls>") return true
        val m = Regex("^(\\*|https?)://([^/]*)(/.*)$", RegexOption.IGNORE_CASE).matchEntire(p) ?: return false
        val (scheme, hostPat, pathPat) = m.destructured
        val um = Regex("^(https?)://([^/?#]*)(.*)$", RegexOption.IGNORE_CASE).matchEntire(url) ?: return false
        val uScheme = um.groupValues[1].lowercase()
        if (scheme != "*" && !scheme.equals(uScheme, true)) return false
        val authority = um.groupValues[2].substringAfterLast('@')
        val hostWithPort = authority.lowercase()
        val host = hostWithPort.substringBefore(':')
        val hp = hostPat.lowercase()
        val hostOk = when {
            hp == "*" -> true
            hp.startsWith("*.") -> host == hp.removePrefix("*.").substringBefore(':') || host.endsWith("." + hp.removePrefix("*.").substringBefore(':'))
            hp.contains(':') -> hostWithPort == hp
            else -> host == hp
        }
        if (!hostOk) return false
        var path = um.groupValues[3]
        if (!path.startsWith("/")) path = "/$path"
        return glob(pathPat, path, ignoreCase = false)
    }

    /** Greasemonkey @include: a glob over the whole address, or a /regex/. */
    fun includeMatches(pattern: String, url: String): Boolean {
        val p = pattern.trim()
        if (p.isEmpty() || p.length > 300) return false
        if (p == "*") return true
        if (p.length > 2 && p.startsWith("/") && p.lastIndexOf('/') > 0) {
            val last = p.lastIndexOf('/')
            val flags = p.substring(last + 1)
            if (flags.all { it == 'i' || it == 'u' || it == 'g' }) {
                return try {
                    Regex(p.substring(1, last), if ('i' in flags) setOf(RegexOption.IGNORE_CASE) else emptySet()).containsMatchIn(url)
                } catch (e: Exception) { false }
            }
        }
        return glob(p, url, ignoreCase = true)
    }

    internal fun glob(pattern: String, text: String, ignoreCase: Boolean): Boolean {
        val sb = StringBuilder()
        for (ch in pattern) if (ch == '*') sb.append(".*") else sb.append(Regex.escape(ch.toString()))
        return try {
            Regex(sb.toString(), if (ignoreCase) setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL) else setOf(RegexOption.DOT_MATCHES_ALL)).matches(text)
        } catch (e: Exception) { false }
    }
}

/** Turns a script into the JavaScript that is injected into a page. */
object UserScriptRuntime {

    fun jsString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\"); '"' -> sb.append("\\\""); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r")
            ' ' -> sb.append("\\u2028"); ' ' -> sb.append("\\u2029"); '<' -> sb.append("\\u003c")
            else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        return sb.append('"').toString()
    }

    /** Scripts that should run on [url], as ready-to-inject strings. */
    fun scriptsFor(scripts: List<UserScript>, url: String, masterEnabled: Boolean): List<String> =
        if (!masterEnabled) emptyList() else scripts.filter { it.enabled && UserScriptMatcher.applies(it, url) }.map(::build)

    /**
     * The script's own code is placed directly inside run() so a mistake in one script cannot stop
     * the others, and the page's content-security-policy cannot block it. Only a small, safe subset
     * of the GM_ API is provided; nothing here can reach XCSABER or Android.
     */
    fun build(s: UserScript): String {
        val id = jsString(s.id)
        val runner = when (s.runAt) {
            RunAt.START -> "run();"
            RunAt.END -> "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',run);else run();"
            RunAt.IDLE -> "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',run);else run();"
        }
        return "(function(){var ID=$id;window.__xcUS=window.__xcUS||{};if(window.__xcUS[ID])return;window.__xcUS[ID]=1;" +
            "var P='xcgm:'+ID+':';var unsafeWindow=window;" +
            "var GM_info={scriptHandler:'XCSABER',version:'1',script:{name:${jsString(s.name)},version:${jsString(s.version)},description:${jsString(s.description)}}};" +
            "function GM_addStyle(c){var e=document.createElement('style');e.textContent=c;(document.head||document.documentElement).appendChild(e);return e;}" +
            "function GM_log(){try{console.log.apply(console,arguments)}catch(e){}}" +
            "function GM_getValue(k,d){try{var v=localStorage.getItem(P+k);return v===null?d:JSON.parse(v)}catch(e){return d}}" +
            "function GM_setValue(k,v){try{localStorage.setItem(P+k,JSON.stringify(v))}catch(e){}}" +
            "function GM_deleteValue(k){try{localStorage.removeItem(P+k)}catch(e){}}" +
            "function GM_listValues(){var o=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k.indexOf(P)===0)o.push(k.slice(P.length))}}catch(e){}return o}" +
            "function GM_registerMenuCommand(){}" +
            "function GM_openInTab(u){try{if(/^https?:/i.test(u))location.href=u}catch(e){}}" +
            "function GM_setClipboard(t){try{navigator.clipboard.writeText(String(t))}catch(e){}}" +
            "var GM={info:GM_info,addStyle:function(c){return Promise.resolve(GM_addStyle(c))},log:GM_log," +
            "getValue:function(k,d){return Promise.resolve(GM_getValue(k,d))},setValue:function(k,v){return Promise.resolve(GM_setValue(k,v))}," +
            "deleteValue:function(k){return Promise.resolve(GM_deleteValue(k))},listValues:function(){return Promise.resolve(GM_listValues())}," +
            "openInTab:GM_openInTab,setClipboard:GM_setClipboard,registerMenuCommand:GM_registerMenuCommand};" +
            "function run(){try{\n" + s.code + "\n}catch(e){try{console.error('[XCSABER userscript '+GM_info.script.name+']',e)}catch(x){}}}" +
            runner + "})();"
    }
}

/** Installed scripts, kept in the app's encrypted store. */
class UserScriptStore(private val store: KvStore) {
    enum class Saved { ADDED, UPDATED, TOO_MANY }

    @Volatile private var cache: List<UserScript>? = null

    /** All scripts. Cached in memory, because this is read on every page load. */
    fun all(): List<UserScript> {
        cache?.let { return it }
        val raw = store.getString(KEY, null)
        val parsed = if (raw == null) emptyList() else try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
        cache = parsed
        return parsed
    }

    fun get(id: String) = all().firstOrNull { it.id == id }

    @Synchronized
    fun save(script: UserScript): Saved {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == script.id }
        return if (i >= 0) {
            list[i] = script.copy(enabled = list[i].enabled, installedAt = list[i].installedAt)
            write(list); Saved.UPDATED
        } else {
            if (list.size >= MAX_SCRIPTS) return Saved.TOO_MANY
            list.add(script); write(list); Saved.ADDED
        }
    }

    fun setEnabled(id: String, on: Boolean) = write(all().map { if (it.id == id) it.copy(enabled = on) else it })

    fun remove(id: String) = write(all().filterNot { it.id == id })

    @Synchronized
    private fun write(list: List<UserScript>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        store.putString(KEY, arr.toString())
        cache = list.toList()
    }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun toJson(s: UserScript) = JSONObject()
        .put("id", s.id).put("n", s.name).put("d", s.description).put("v", s.version)
        .put("m", JSONArray(s.matches)).put("i", JSONArray(s.includes)).put("x", JSONArray(s.excludes))
        .put("g", JSONArray(s.grants)).put("r", s.runAt.key).put("c", s.code)
        .put("e", s.enabled).put("t", s.installedAt).put("u", s.sourceUrl)

    private fun fromJson(o: JSONObject) = UserScript(
        id = o.getString("id"), name = o.optString("n"), description = o.optString("d"), version = o.optString("v"),
        matches = strings(o.optJSONArray("m")), includes = strings(o.optJSONArray("i")), excludes = strings(o.optJSONArray("x")),
        grants = strings(o.optJSONArray("g")), runAt = RunAt.from(o.optString("r")), code = o.optString("c"),
        enabled = o.optBoolean("e", true), installedAt = o.optLong("t"), sourceUrl = o.optString("u"),
    )

    companion object {
        private const val KEY = "userscripts"
        const val MAX_SCRIPTS = 40
    }
}
