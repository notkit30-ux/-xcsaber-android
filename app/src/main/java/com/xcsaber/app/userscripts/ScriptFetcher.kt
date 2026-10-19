package com.xcsaber.app.userscripts

import java.net.HttpURLConnection
import java.net.URL

/** Downloads a userscript over HTTPS. Call off the main thread. */
object ScriptFetcher {
    sealed class Result {
        data class Text(val body: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun fetch(address: String): Result {
        val url = address.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            return Result.Failed("Only https:// addresses can be installed, because scripts are code.")
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true // the JVM never follows https -> http redirects
                setRequestProperty("User-Agent", "XCSABER/1.1")
                setRequestProperty("Accept", "text/javascript, text/plain, */*")
            }
            if (conn.responseCode != 200) return Result.Failed("The server answered ${conn.responseCode}.")
            val text = readLimited(conn.inputStream.bufferedReader())
                ?: return Result.Failed("That file is too large (limit ${UserScriptParser.MAX_CODE_CHARS / 1024} KB).")
            Result.Text(text)
        } catch (e: Exception) {
            Result.Failed("Couldn't download it. Check your connection and the address.")
        } finally {
            conn?.disconnect()
        }
    }

    /** Reads at most the allowed size; null if the source is bigger. */
    fun readLimited(reader: java.io.Reader): String? {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            sb.append(buf, 0, n)
            if (sb.length > UserScriptParser.MAX_CODE_CHARS) return null
        }
        return sb.toString()
    }
}
