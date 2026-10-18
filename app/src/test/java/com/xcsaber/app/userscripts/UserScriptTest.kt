package com.xcsaber.app.userscripts

import com.xcsaber.app.data.MemoryStore
import org.junit.Assert.*
import org.junit.Test

class UserScriptTest {
    private val sample = """
        // ==UserScript==
        // @name         Demo script
        // @namespace    https://example.org/
        // @version      1.2
        // @description  Does a thing
        // @match        https://*.example.com/*
        // @match        *://news.site/read/*
        // @exclude      https://www.example.com/private/*
        // @grant        GM_addStyle
        // @grant        GM_xmlhttpRequest
        // @run-at       document-start
        // @require      https://cdn.example/lib.js
        // ==/UserScript==
        document.title = 'x';
    """.trimIndent()

    private fun ok(src: String = sample) = (UserScriptParser.parse(src) as ParseResult.Ok)

    @Test fun parsesMetadata() {
        val r = ok(); val s = r.script
        assertEquals("Demo script", s.name); assertEquals("1.2", s.version); assertEquals("Does a thing", s.description)
        assertEquals(2, s.matches.size); assertEquals(1, s.excludes.size)
        assertEquals(RunAt.START, s.runAt)
        assertEquals(listOf("GM_addStyle", "GM_xmlhttpRequest"), s.grants)
        assertEquals(listOf("GM_xmlhttpRequest"), s.unsupportedGrants)
        assertEquals(2, r.warnings.size) // unsupported grant + @require
    }

    @Test fun rejectsBadInput() {
        assertTrue(UserScriptParser.parse("") is ParseResult.Error)
        assertTrue(UserScriptParser.parse("alert(1)") is ParseResult.Error)
        assertTrue(UserScriptParser.parse("// ==UserScript==\n// @name X\n// ==/UserScript==\n1") is ParseResult.Error) // no match rule
        assertTrue(UserScriptParser.parse("// ==/UserScript==\n// ==UserScript==") is ParseResult.Error)
        assertTrue(UserScriptParser.parse("x".repeat(UserScriptParser.MAX_CODE_CHARS + 1)) is ParseResult.Error)
    }

    @Test fun defaultsAndCleaning() {
        val s = ok("\uFEFF// ==UserScript==\n// @include *\n// ==/UserScript==\n1").script
        assertEquals("Untitled script", s.name); assertEquals(RunAt.IDLE, s.runAt)
        val long = ok("// ==UserScript==\n// @name ${"a".repeat(200)}\n// @match *://*/*\n// ==/UserScript==").script
        assertEquals(80, long.name.length)
    }

    @Test fun idIsStableAndDependsOnNameAndNamespace() {
        val a = UserScriptParser.idFor("A", "ns"); assertEquals(a, UserScriptParser.idFor("A", "ns"))
        assertNotEquals(a, UserScriptParser.idFor("B", "ns")); assertNotEquals(a, UserScriptParser.idFor("A", "other"))
        assertTrue(a.matches(Regex("us-[0-9a-f]{1,16}")))
    }

    @Test fun userScriptUrlDetection() {
        assertTrue(UserScriptParser.looksLikeUserScriptUrl("https://greasyfork.org/scripts/1/code/x.user.js"))
        assertTrue(UserScriptParser.looksLikeUserScriptUrl("https://raw.githubusercontent.com/a/b/main/x.user.js?v=2#h"))
        assertFalse(UserScriptParser.looksLikeUserScriptUrl("http://example.com/x.user.js")) // https only
        assertFalse(UserScriptParser.looksLikeUserScriptUrl("https://example.com/x.js"))
        assertFalse(UserScriptParser.looksLikeUserScriptUrl("https://example.com/page?f=x.user.js"))
    }

    @Test fun matchPatterns() {
        val m = UserScriptMatcher::matchPattern
        assertTrue(m("https://*.example.com/*", "https://example.com/"))
        assertTrue(m("https://*.example.com/*", "https://a.b.example.com/x?y=1"))
        assertFalse(m("https://*.example.com/*", "https://badexample.com/"))
        assertFalse(m("https://*.example.com/*", "http://example.com/"))
        assertTrue(m("*://example.com/a*", "http://example.com/abc"))
        assertFalse(m("*://example.com/a*", "http://example.com/b"))
        assertTrue(m("<all_urls>", "https://x.y/"))
        assertFalse(m("https://example.com/*", "https://example.com.evil.io/"))
        assertTrue(m("https://example.com:8080/*", "https://example.com:8080/x"))
        assertFalse(m("https://example.com/*", "https://user@evil.com/"))
        assertFalse(m("garbage", "https://a.com/"))
    }

    @Test fun includeGlobAndRegex() {
        val i = UserScriptMatcher::includeMatches
        assertTrue(i("*", "https://a.com/")); assertTrue(i("http*://a.com/*", "https://a.com/x"))
        assertFalse(i("http://a.com/*", "https://a.com/x"))
        assertTrue(i("/^https:\\/\\/a\\.com\\/p\\d+/", "https://a.com/p12"))
        assertFalse(i("/^https:\\/\\/a\\.com\\/p\\d+/", "https://a.com/q"))
        assertFalse(i("/([/", "https://a.com/")) // broken regex never matches, never crashes
    }

    @Test fun appliesHonoursExcludesAndSchemes() {
        val s = ok().script
        assertTrue(UserScriptMatcher.applies(s, "https://www.example.com/a"))
        assertFalse(UserScriptMatcher.applies(s, "https://www.example.com/private/x"))
        assertTrue(UserScriptMatcher.applies(s, "http://news.site/read/1#frag"))
        assertFalse(UserScriptMatcher.applies(s, "file:///sdcard/a.html"))
        assertFalse(UserScriptMatcher.applies(s, "javascript:alert(1)"))
        assertFalse(UserScriptMatcher.applies(s, "https://other.org/"))
    }

    @Test fun runsEverywhereAndScopeLabels() {
        assertTrue(ok("// ==UserScript==\n// @match <all_urls>\n// ==/UserScript==").script.runsEverywhere)
        assertTrue(ok("// ==UserScript==\n// @match *://*/*\n// ==/UserScript==").script.runsEverywhere)
        assertTrue(ok("// ==UserScript==\n// @include *\n// ==/UserScript==").script.runsEverywhere)
        assertFalse(ok().script.runsEverywhere)
        assertEquals("https://*.example.com/*, *://news.site/read/*", ok().script.scopeLabel)
    }

    @Test fun jsStringEscapes() {
        assertEquals("\"a\\\"b\\\\c\\n\\u003c/script>\"", UserScriptRuntime.jsString("a\"b\\c\n</script>"))
        assertEquals("\"\\u2028\"", UserScriptRuntime.jsString("\u2028"))
    }

    @Test fun buildInjectsCodeOnceAndGuards() {
        val js = UserScriptRuntime.build(ok().script)
        assertTrue(js.contains("window.__xcUS[ID]")); assertTrue(js.contains("document.title = 'x';"))
        assertTrue(js.contains("GM_addStyle")); assertFalse(js.contains("GM_xmlhttpRequest("))
        assertTrue(js.trimEnd().endsWith("run();})();"))
        val idle = UserScriptRuntime.build(ok().script.copy(runAt = RunAt.IDLE))
        assertTrue(idle.contains("DOMContentLoaded"))
    }

    @Test fun scriptsForRespectsMasterAndEnabled() {
        val s = ok().script
        assertEquals(1, UserScriptRuntime.scriptsFor(listOf(s), "https://www.example.com/", true).size)
        assertTrue(UserScriptRuntime.scriptsFor(listOf(s), "https://www.example.com/", false).isEmpty())
        assertTrue(UserScriptRuntime.scriptsFor(listOf(s.copy(enabled = false)), "https://www.example.com/", true).isEmpty())
        assertTrue(UserScriptRuntime.scriptsFor(listOf(s), "https://nope.org/", true).isEmpty())
    }

    @Test fun storeRoundTripUpdateKeepsEnabledAndRemoves() {
        val st = UserScriptStore(MemoryStore()); val s = ok().script
        assertEquals(UserScriptStore.Saved.ADDED, st.save(s.copy(installedAt = 5)))
        st.setEnabled(s.id, false)
        assertEquals(UserScriptStore.Saved.UPDATED, st.save(s.copy(version = "2.0", installedAt = 99)))
        val got = st.get(s.id)!!
        assertEquals("2.0", got.version); assertFalse(got.enabled); assertEquals(5L, got.installedAt)
        assertEquals(s.matches, got.matches); assertEquals(s.code, got.code); assertEquals(RunAt.START, got.runAt)
        st.remove(s.id); assertTrue(st.all().isEmpty())
    }

    @Test fun storeLimitsCount() {
        val st = UserScriptStore(MemoryStore()); val s = ok().script
        repeat(UserScriptStore.MAX_SCRIPTS) { assertEquals(UserScriptStore.Saved.ADDED, st.save(s.copy(id = "us-$it"))) }
        assertEquals(UserScriptStore.Saved.TOO_MANY, st.save(s.copy(id = "us-extra")))
        assertEquals(UserScriptStore.Saved.UPDATED, st.save(s.copy(id = "us-1")))
    }

    @Test fun corruptStoreIsEmptyNotCrash() {
        val kv = MemoryStore(); kv.putString("userscripts", "{not json")
        assertTrue(UserScriptStore(kv).all().isEmpty())
    }
}
