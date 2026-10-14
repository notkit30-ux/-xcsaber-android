package com.xcsaber.app.browser

import com.xcsaber.app.data.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePermissionStoreTest {

    @Test fun unknownSitesAreAskedEveryTime() {
        val s = SitePermissionStore(MemoryStore())
        assertNull(s.get("example.com", SitePermission.CAMERA))
    }

    @Test fun remembersAllowAndBlockPerPermission() {
        val s = SitePermissionStore(MemoryStore())
        s.set("Example.com", SitePermission.CAMERA, true)
        s.set("example.com", SitePermission.LOCATION, false)
        assertEquals(true, s.get("example.com", SitePermission.CAMERA))
        assertEquals(false, s.get("EXAMPLE.COM", SitePermission.LOCATION))
        assertNull(s.get("example.com", SitePermission.MICROPHONE))
        assertNull(s.get("other.com", SitePermission.CAMERA))
    }

    @Test fun listsAndClearsDecisions() {
        val s = SitePermissionStore(MemoryStore())
        s.set("b.com", SitePermission.MICROPHONE, true)
        s.set("a.com", SitePermission.CAMERA, false)
        val all = s.all()
        assertEquals(listOf("a.com", "b.com"), all.map { it.host })
        assertFalse(all[0].allowed)
        assertTrue(all[1].allowed)
        s.clear("a.com", SitePermission.CAMERA)
        assertEquals(1, s.all().size)
        s.clearAll()
        assertTrue(s.all().isEmpty())
    }
}
