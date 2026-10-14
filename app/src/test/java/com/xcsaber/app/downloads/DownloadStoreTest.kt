package com.xcsaber.app.downloads

import com.xcsaber.app.data.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStoreTest {

    @Test fun newestFirstAndReplacesSameId() {
        var now = 0L
        val s = DownloadStore(MemoryStore()) { ++now }
        s.add(1, "a.pdf", "https://x.com/a.pdf")
        s.add(2, "b.pdf", "https://x.com/b.pdf")
        s.add(1, "a2.pdf", "https://x.com/a.pdf")
        assertEquals(listOf(1L, 2L), s.all().map { it.id })
        assertEquals("a2.pdf", s.all()[0].name)
    }

    @Test fun removeAndClear() {
        val s = DownloadStore(MemoryStore())
        s.add(1, "a", "https://x.com/a")
        s.add(2, "b", "https://x.com/b")
        s.remove(1)
        assertEquals(listOf(2L), s.all().map { it.id })
        s.clear()
        assertTrue(s.all().isEmpty())
    }

    @Test fun keepsAtMostOneHundred() {
        val s = DownloadStore(MemoryStore())
        for (i in 1L..120L) s.add(i, "f$i", "https://x.com/$i")
        assertEquals(100, s.all().size)
        assertEquals(120L, s.all().first().id)
    }
}
