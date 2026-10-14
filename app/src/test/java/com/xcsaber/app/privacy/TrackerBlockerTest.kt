package com.xcsaber.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerBlockerTest {

    private fun blocker(vararg lines: String) = TrackerBlocker().apply { load(lines.asSequence()) }

    @Test fun parsesHostsFilePlainAndCommentedLines() {
        val parsed = TrackerBlocker.parse(
            sequenceOf(
                "# comment",
                "0.0.0.0 ads.example.com",
                "127.0.0.1 localhost",
                "tracker.net # inline comment",
                "! adblock comment",
                "[Adblock Plus 2.0]",
                "||not-a-domain^",
                "",
                "UPPER.Example.ORG"
            )
        )
        assertEquals(setOf("ads.example.com", "tracker.net", "upper.example.org"), parsed)
    }

    @Test fun blocksSubdomainsOfListedDomains() {
        val b = blocker("ads.example.com", "tracker.net")
        assertTrue(b.isBlocked("ads.example.com"))
        assertTrue(b.isBlocked("cdn.ads.example.com"))
        assertTrue(b.isBlocked("TRACKER.NET"))
        assertTrue(b.isBlocked("pixel.tracker.net."))
        assertFalse(b.isBlocked("example.com"))
        assertFalse(b.isBlocked("nottracker.net"))
        assertFalse(b.isBlocked(null))
        assertFalse(b.isBlocked(""))
    }

    @Test fun neverBlocksFirstPartyRequests() {
        val b = blocker("tracker.net")
        assertTrue(b.shouldBlock("pixel.tracker.net", "news.com"))
        assertTrue(b.shouldBlock("tracker.net", null))
        // The user visiting the tracker's own site must still work.
        assertFalse(b.shouldBlock("cdn.tracker.net", "tracker.net"))
        assertFalse(b.shouldBlock("tracker.net", "www.tracker.net"))
        assertFalse(b.shouldBlock("news.com", "news.com"))
    }

    @Test fun bundledStarterListLoadsNothingInvalid() {
        val b = blocker("doubleclick.net", "bad entry", "localhost")
        assertEquals(1, b.size)
    }
}
