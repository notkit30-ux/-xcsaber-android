package com.xcsaber.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagerTest {

    @Test fun firstTabIsSelected() {
        val m = TabManager()
        val a = m.newTab(false)
        assertEquals(a.id, m.currentId)
        assertEquals(1, m.count)
    }

    @Test fun backgroundTabDoesNotStealSelection() {
        val m = TabManager()
        val a = m.newTab(false)
        m.newTab(false, select = false)
        assertEquals(a.id, m.currentId)
    }

    @Test fun selectOnlyWorksForExistingTabs() {
        val m = TabManager()
        val a = m.newTab(false)
        val b = m.newTab(false)
        assertTrue(m.select(a.id))
        assertEquals(a.id, m.currentId)
        assertFalse(m.select(999))
        assertEquals(a.id, m.currentId)
        assertEquals(b.id, m.find(b.id)?.id)
    }

    @Test fun closingCurrentSelectsNeighbour() {
        val m = TabManager()
        val a = m.newTab(false)
        val b = m.newTab(false)
        val c = m.newTab(false)
        assertEquals(c.id, m.currentId)
        m.close(c.id)
        assertEquals(b.id, m.currentId)
        m.select(a.id)
        m.close(a.id)
        assertEquals(b.id, m.currentId)
    }

    @Test fun closingOtherTabKeepsSelection() {
        val m = TabManager()
        val a = m.newTab(false)
        val b = m.newTab(false)
        m.close(a.id)
        assertEquals(b.id, m.currentId)
    }

    @Test fun closingTabReturnsToItsOpener() {
        val m = TabManager()
        val parent = m.newTab(false)
        val child = m.newTab(false, parentId = parent.id)
        m.newTab(false, select = false)
        assertEquals(child.id, m.currentId)
        m.close(child.id)
        assertEquals(parent.id, m.currentId)
    }

    @Test fun closingTheLastTabLeavesNothingSelected() {
        val m = TabManager()
        val a = m.newTab(false)
        assertEquals(a.id, m.close(a.id)?.id)
        assertEquals(-1L, m.currentId)
        assertNull(m.current)
        assertNull(m.close(a.id))
    }

    @Test fun privateTabsAreTrackedAndClosedTogether() {
        val m = TabManager()
        val normal = m.newTab(false)
        m.newTab(true)
        m.newTab(true)
        assertTrue(m.hasPrivateTabs())
        assertEquals(2, m.privateTabs().size)
        val closed = m.closePrivateTabs()
        assertEquals(2, closed.size)
        assertFalse(m.hasPrivateTabs())
        assertEquals(normal.id, m.currentId)
    }

    @Test fun sessionNeverContainsPrivateOrBlankTabs() {
        val m = TabManager()
        m.newTab(false).apply { url = "https://example.com"; title = "Example" }
        m.newTab(true).apply { url = "https://secret.example" }
        m.newTab(false) // blank
        val json = m.sessionJson()
        assertTrue(json.contains("https://example.com"))
        assertFalse(json.contains("secret.example"))
    }

    @Test fun sessionRoundTripsAndKeepsSelection() {
        val m = TabManager()
        m.newTab(false).apply { url = "https://a.example" }
        val b = m.newTab(false).apply { url = "https://b.example"; desktop = true }
        m.newTab(false).apply { url = "https://c.example" }
        m.select(b.id)

        val restored = TabManager()
        assertEquals(3, restored.restore(m.sessionJson()))
        assertEquals(3, restored.count)
        assertEquals("https://b.example", restored.current?.url)
        assertTrue(restored.current!!.desktop)
        assertTrue(restored.tabs.all { it.needsLoad && !it.isPrivate })
    }

    @Test fun badSessionDataRestoresNothing() {
        val m = TabManager()
        assertEquals(0, m.restore(null))
        assertEquals(0, m.restore(""))
        assertEquals(0, m.restore("{not json"))
        assertEquals(0, m.count)
    }
}
