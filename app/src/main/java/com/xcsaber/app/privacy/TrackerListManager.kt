package com.xcsaber.app.privacy

import android.content.Context
import com.xcsaber.app.data.Prefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the tracker list into a [TrackerBlocker]: the downloaded maintained list if present,
 * otherwise the small list bundled in assets. Updating downloads the Peter Lowe ad/tracking
 * server list over HTTPS (a long-maintained, freely available list).
 */
class TrackerListManager(
    private val context: Context,
    private val prefs: Prefs,
    val blocker: TrackerBlocker = TrackerBlocker()
) {

    private val file: File get() = File(context.filesDir, "trackers_downloaded.txt")

    /** Blocking call: reads a file. Call from a background thread at startup. */
    fun load() {
        try {
            if (file.exists() && file.length() > 0) {
                file.bufferedReader().useLines { blocker.load(it) }
                if (blocker.size > 0) return
            }
            context.assets.open("trackers.txt").bufferedReader().useLines { blocker.load(it) }
        } catch (e: Exception) {
            // Leave whatever is loaded; blocking simply stays weaker, the app keeps working.
        }
    }

    sealed class UpdateResult {
        data class Success(val count: Int) : UpdateResult()
        data class Failure(val reason: String) : UpdateResult()
    }

    /** Blocking network call: run off the main thread. */
    fun update(): UpdateResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LIST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "XCSABER/1.0")
            }
            if (conn.responseCode != 200) return UpdateResult.Failure("Server returned ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.length > MAX_BYTES) return UpdateResult.Failure("List was unexpectedly large")
            val parsed = TrackerBlocker.parse(text.lineSequence())
            if (parsed.size < MIN_ENTRIES) return UpdateResult.Failure("Downloaded list looked invalid")
            file.writeText(text)
            blocker.load(text.lineSequence())
            prefs.trackerListUpdatedAt = System.currentTimeMillis()
            prefs.trackerListSource = LIST_URL
            UpdateResult.Success(blocker.size)
        } catch (e: Exception) {
            UpdateResult.Failure(e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        const val LIST_URL = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext"
        private const val MAX_BYTES = 5_000_000
        private const val MIN_ENTRIES = 500
    }
}
