package com.xcsaber.app.privacy

import com.xcsaber.app.data.Prefs
import java.util.concurrent.atomic.AtomicLong

/**
 * Honest counters for blocked requests.
 *
 * Every count comes from a request that [TrackerBlocker] really refused inside
 * `shouldInterceptRequest`. Blocks from private tabs are counted for the running session only and
 * are never written to disk, so private browsing leaves no lifetime trace.
 *
 * Thread-safe: `shouldInterceptRequest` runs on WebView's IO threads.
 */
class BlockStats(private val prefs: Prefs) {
    private val session = AtomicLong(0)
    private val pending = AtomicLong(0)

    /** Call once per blocked request. */
    fun record(privateTab: Boolean) {
        session.incrementAndGet()
        if (!privateTab) pending.incrementAndGet()
    }

    /** Blocked since the app process started (all tabs). */
    fun sessionCount(): Long = session.get()

    /** Lifetime total from normal tabs, including blocks not yet flushed to storage. */
    fun lifetimeCount(): Long = prefs.blockedTotal + pending.get()

    /** Persist pending blocks. Cheap; call from onStop. */
    @Synchronized
    fun flush() {
        val n = pending.getAndSet(0)
        if (n > 0) prefs.blockedTotal = prefs.blockedTotal + n
    }

    /** Reset the lifetime counter (user action in the Privacy Center). */
    @Synchronized
    fun resetLifetime() {
        pending.set(0)
        prefs.blockedTotal = 0L
    }
}
