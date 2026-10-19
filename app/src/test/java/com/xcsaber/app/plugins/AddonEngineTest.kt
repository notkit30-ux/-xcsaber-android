package com.xcsaber.app.plugins

import com.xcsaber.app.data.MemoryStore
import com.xcsaber.app.data.Prefs
import org.junit.Assert.*
import org.junit.Test

class AddonEngineTest {
    private fun a(id: String, vararg m: String) =
        ScriptAddon(id, id, "d", PluginCategory.PAGE, 0, PluginAccent.BLUE, if (m.isEmpty()) listOf("*") else m.toList())

    private val catalog = listOf(a("dark"), a("yt", "youtube.com"), a("zoom"))

    @Test fun hostMatching() {
        val yt = a("yt", "youtube.com")
        assertTrue(AddonEngine.matches(yt, "youtube.com"))
        assertTrue(AddonEngine.matches(yt, "m.youtube.com"))
        assertTrue(AddonEngine.matches(yt, "WWW.YouTube.com."))
        assertFalse(AddonEngine.matches(yt, "notyoutube.com"))
        assertFalse(AddonEngine.matches(yt, "youtube.com.evil.io"))
        assertFalse(AddonEngine.matches(yt, null))
        assertTrue(AddonEngine.matches(a("x"), null))
    }

    @Test fun hostOfParsesUrls() {
        assertEquals("example.com", AddonEngine.hostOf("https://example.com/a?b=c#d"))
        assertEquals("example.com", AddonEngine.hostOf("https://user:pw@example.com:8080/x"))
        assertEquals("m.youtube.com", AddonEngine.hostOf("http://m.youtube.com"))
        assertNull(AddonEngine.hostOf("about:blank"))
    }

    @Test fun onlyWebPagesAndEnabledAddonsApply() {
        assertTrue(AddonEngine.applicable(catalog, emptySet(), "https://a.com").isEmpty())
        assertTrue(AddonEngine.applicable(catalog, setOf("dark"), "file:///sdcard/a.html").isEmpty())
        assertTrue(AddonEngine.applicable(catalog, setOf("dark"), "about:blank").isEmpty())
        assertTrue(AddonEngine.applicable(catalog, setOf("dark"), "javascript:alert(1)").isEmpty())
        assertEquals(listOf("dark", "zoom"), AddonEngine.applicable(catalog, setOf("zoom", "dark", "yt"), "https://a.com").map { it.id })
        assertEquals(listOf("dark", "yt"), AddonEngine.applicable(catalog, setOf("dark", "yt"), "https://m.youtube.com/watch").map { it.id })
    }

    @Test fun wrapIsGuardedAndSafe() {
        val s = AddonEngine.wrap("dark", "xcCss('a{}');")
        assertTrue(s.startsWith("(function(){try{"))
        assertTrue(s.endsWith("}catch(e){}})();"))
        assertTrue(s.contains("R['dark']"))
        assertTrue(s.contains("xcCss('a{}');"))
    }

    @Test fun wrapRejectsBadIds() {
        assertThrows(IllegalArgumentException::class.java) { AddonEngine.wrap("a'b", "") }
        assertThrows(IllegalArgumentException::class.java) { AddonEngine.wrap("A", "") }
        assertThrows(IllegalArgumentException::class.java) { AddonEngine.wrap("", "") }
    }

    @Test fun scriptForSkipsMissingBodies() {
        val list = listOf(a("dark"), a("zoom"))
        assertNull(AddonEngine.scriptFor(list) { null })
        val s = AddonEngine.scriptFor(list) { if (it == "zoom") "var z=1;" else null }!!
        assertTrue(s.contains("R['zoom']")); assertFalse(s.contains("R['dark']"))
    }

    @Test fun prefsStoreEnabledAddons() {
        val p = Prefs(MemoryStore())
        assertTrue(p.enabledAddons.isEmpty())
        p.setAddonEnabled("dark", true); p.setAddonEnabled("zoom", true); p.setAddonEnabled("dark", true)
        assertEquals(setOf("dark", "zoom"), p.enabledAddons)
        p.setAddonEnabled("dark", false)
        assertFalse(p.isAddonEnabled("dark")); assertTrue(p.isAddonEnabled("zoom"))
    }

    @Test fun prefsDropInvalidAddonIds() {
        val p = Prefs(MemoryStore())
        p.enabledAddons = setOf("ok-1", "Bad Id", "x,y", "")
        assertEquals(setOf("ok-1"), p.enabledAddons)
    }

    @Test fun pluginReflectsPrefs() {
        val p = Prefs(MemoryStore())
        val plugin = ScriptAddonPlugin(a("dark"))
        assertEquals("addon.dark", plugin.info.id)
        assertFalse(plugin.isEnabled(p)); assertEquals("Off", plugin.status(p))
        p.setAddonEnabled("dark", true)
        assertTrue(plugin.isEnabled(p)); assertEquals("On · All websites", plugin.status(p))
    }
}
