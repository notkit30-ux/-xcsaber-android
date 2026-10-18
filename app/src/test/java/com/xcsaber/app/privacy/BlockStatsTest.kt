package com.xcsaber.app.privacy

import com.xcsaber.app.data.MemoryStore
import com.xcsaber.app.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BlockStatsTest {
    @Test fun countsSessionAndLifetime() {
        val prefs = Prefs(MemoryStore()); val s = BlockStats(prefs)
        repeat(3) { s.record(false) }
        assertEquals(3L, s.sessionCount()); assertEquals(3L, s.lifetimeCount())
        s.flush()
        assertEquals(3L, prefs.blockedTotal); assertEquals(3L, s.lifetimeCount())
        s.flush() // idempotent
        assertEquals(3L, prefs.blockedTotal)
    }

    @Test fun privateBlocksNeverPersist() {
        val prefs = Prefs(MemoryStore()); val s = BlockStats(prefs)
        repeat(5) { s.record(true) }
        s.flush()
        assertEquals(5L, s.sessionCount()); assertEquals(0L, s.lifetimeCount()); assertEquals(0L, prefs.blockedTotal)
    }

    @Test fun resetClearsLifetime() {
        val prefs = Prefs(MemoryStore()); val s = BlockStats(prefs)
        repeat(4) { s.record(false) }; s.flush(); s.record(false)
        s.resetLifetime()
        assertEquals(0L, s.lifetimeCount())
    }

    @Test fun threadSafe() {
        val s = BlockStats(Prefs(MemoryStore()))
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) { pool.execute { repeat(1000) { s.record(false) } } }
        pool.shutdown(); pool.awaitTermination(10, TimeUnit.SECONDS)
        assertEquals(8000L, s.sessionCount()); assertEquals(8000L, s.lifetimeCount())
    }
}
