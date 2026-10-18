package com.xcsaber.app.data

import com.xcsaber.app.search.SearchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {

    @Test fun recordsNewestFirstAndCollapsesImmediateRepeats() {
        val h = HistoryStore(MemoryStore())
        h.record("A", "https://a.com")
        h.record("B", "https://b.com")
        h.record("B again", "https://b.com")
        assertEquals(listOf("https://b.com", "https://a.com"), h.all().map { it.url })
        assertEquals("B again", h.all()[0].title)
    }

    @Test fun ignoresInternalAndBlankUrls() {
        val h = HistoryStore(MemoryStore())
        h.record("x", "about:blank")
        h.record("x", "data:text/html,hi")
        h.record("x", "  ")
        assertTrue(h.all().isEmpty())
    }

    @Test fun isCappedAtMaxEntries() {
        val h = HistoryStore(MemoryStore(), maxEntries = 3)
        for (i in 1..5) h.record("t$i", "https://site$i.com")
        assertEquals(listOf("https://site5.com", "https://site4.com", "https://site3.com"), h.all().map { it.url })
    }

    @Test fun searchUpdateTitleRemoveAndClear() {
        val h = HistoryStore(MemoryStore())
        h.record("", "https://kotlinlang.org")
        h.record("Cats", "https://cats.example")
        assertEquals("https://kotlinlang.org", h.all()[1].title) // blank title falls back to the URL
        h.updateTitle("https://kotlinlang.org", "Kotlin")
        assertEquals(listOf("https://kotlinlang.org"), h.search("kotl").map { it.url })
        assertEquals(2, h.search("  ").size)
        h.remove("https://cats.example")
        assertEquals(1, h.all().size)
        h.clear()
        assertTrue(h.all().isEmpty())
    }
}

class BookmarkStoreTest {

    @Test fun toggleAddsThenRemoves() {
        val b = BookmarkStore(MemoryStore())
        assertTrue(b.toggle("Example", "https://example.com"))
        assertTrue(b.isBookmarked("https://example.com"))
        assertFalse(b.toggle("Example", "https://example.com"))
        assertFalse(b.isBookmarked("https://example.com"))
    }

    @Test fun newestFirstRemoveAndClear() {
        val b = BookmarkStore(MemoryStore())
        b.toggle("A", "https://a.com")
        b.toggle("", "https://b.com")
        assertEquals(listOf("https://b.com", "https://a.com"), b.all().map { it.url })
        assertEquals("https://b.com", b.all()[0].title)
        b.remove("https://a.com")
        assertEquals(1, b.all().size)
        b.clear()
        assertTrue(b.all().isEmpty())
    }
}

class RecentSearchStoreTest {

    @Test fun deduplicatesIgnoringCaseAndMovesToFront() {
        val r = RecentSearchStore(MemoryStore())
        r.add("cats")
        r.add("dogs")
        r.add("CATS")
        assertEquals(listOf("CATS", "dogs"), r.all())
    }

    @Test fun ignoresBlankAndCapsLength() {
        val r = RecentSearchStore(MemoryStore(), maxEntries = 2)
        r.add("   ")
        r.add("a"); r.add("b"); r.add("c")
        assertEquals(listOf("c", "b"), r.all())
    }

    @Test fun matchingRemoveAndClear() {
        val r = RecentSearchStore(MemoryStore())
        r.add("kotlin coroutines"); r.add("android webview")
        assertEquals(listOf("kotlin coroutines"), r.matching("COROU"))
        assertEquals(2, r.matching("").size)
        r.remove("android webview")
        assertEquals(1, r.all().size)
        r.clear()
        assertTrue(r.all().isEmpty())
    }
}

class ShortcutStoreTest {

    @Test fun firstUseGivesDefaultsAndSavesThem() {
        val store = MemoryStore()
        val s = ShortcutStore(store)
        assertEquals(ShortcutStore.DEFAULTS, s.all())
        assertTrue(store.getString("shortcuts") != null)
    }

    @Test fun addReplacesSameUrlAndRemoveWorks() {
        val s = ShortcutStore(MemoryStore())
        s.add("Mine", "https://mine.example")
        s.add("Mine 2", "https://mine.example")
        assertEquals(1, s.all().count { it.url == "https://mine.example" })
        assertEquals("Mine 2", s.all().last().title)
        s.remove("https://mine.example")
        assertEquals(ShortcutStore.DEFAULTS, s.all())
    }

    @Test fun resetRestoresDefaults() {
        val s = ShortcutStore(MemoryStore())
        s.add("X", "https://x.example")
        s.resetToDefaults()
        assertEquals(ShortcutStore.DEFAULTS, s.all())
    }

    @Test fun everyDefaultShortcutIsAnHttpsUrl() {
        assertTrue(ShortcutStore.DEFAULTS.all { it.url.startsWith("https://") })
    }
}

class PrefsTest {

    @Test fun safeDefaults() {
        val p = Prefs(MemoryStore())
        assertEquals(SearchProvider.DEFAULT_ID, p.searchProviderId)
        assertEquals("DuckDuckGo", p.searchProvider.name)
        assertTrue(p.trackerBlocking)
        assertTrue(p.blockThirdPartyCookies)
        assertTrue(p.blockPopups)
        assertTrue(p.saveHistory)
        assertFalse(p.httpsOnly)
        assertEquals(Prefs.THEME_DARK, p.theme)
        assertEquals(0L, p.trackerListUpdatedAt)
        assertNull(p.trackerListSource)
    }

    @Test fun valuesRoundTrip() {
        val p = Prefs(MemoryStore())
        p.searchProviderId = "brave"
        p.httpsOnly = true
        p.trackerBlocking = false
        p.theme = Prefs.THEME_DARK
        p.trackerListUpdatedAt = 123L
        assertEquals("Brave Search", p.searchProvider.name)
        assertTrue(p.httpsOnly)
        assertFalse(p.trackerBlocking)
        assertEquals(Prefs.THEME_DARK, p.theme)
        assertEquals(123L, p.trackerListUpdatedAt)
    }

    @Test fun unknownProviderFallsBackToDuckDuckGo() {
        val p = Prefs(MemoryStore())
        p.searchProviderId = "gone"
        assertEquals("duckduckgo", p.searchProvider.id)
    }

    @Test fun privacyGuardGatesEffectiveSettings() {
        val p = Prefs(MemoryStore())
        p.httpsOnly = true
        assertTrue(p.effectiveHttpsOnly); assertTrue(p.effectiveBlockThirdPartyCookies); assertTrue(p.effectiveSendGpc)
        p.privacyGuard = false
        assertFalse(p.effectiveHttpsOnly); assertFalse(p.effectiveBlockThirdPartyCookies); assertFalse(p.effectiveSendGpc)
        assertTrue(p.httpsOnly) // the stored choice is kept for when the guard is switched back on
    }

    @Test fun textZoomIsClamped() {
        val p = Prefs(MemoryStore())
        assertEquals(100, p.pageTextZoom)
        p.pageTextZoom = 500; assertEquals(Prefs.TEXT_ZOOM_MAX, p.pageTextZoom)
        p.pageTextZoom = 1; assertEquals(Prefs.TEXT_ZOOM_MIN, p.pageTextZoom)
    }

    @Test fun pluginDefaults() {
        val p = Prefs(MemoryStore())
        assertTrue(p.pageToolsEnabled); assertFalse(p.blockImages); assertTrue(p.pageDarkening); assertTrue(p.confirmDownloads)
        assertFalse(p.devToolsEnabled); assertFalse(p.devRemoteDebugging); assertEquals(0L, p.blockedTotal)
    }
}
