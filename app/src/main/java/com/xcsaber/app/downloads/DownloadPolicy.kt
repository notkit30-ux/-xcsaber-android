package com.xcsaber.app.downloads

import java.util.Locale

/** Pure rules for what may be downloaded and how a file name is cleaned. Unit tested. */
object DownloadPolicy {

    /** Files that can run code or install software; the user gets an extra warning for these. */
    private val RISKY_EXTENSIONS = setOf(
        "apk", "apks", "xapk", "aab", "exe", "msi", "bat", "cmd", "scr", "jar", "js", "vbs",
        "sh", "dex", "com", "pif", "dmg", "appimage"
    )

    private const val MAX_NAME = 120

    fun isAllowedUrl(url: String): Boolean {
        val u = url.trim().lowercase(Locale.ROOT)
        return u.startsWith("http://") || u.startsWith("https://")
    }

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun isRisky(fileName: String, mimeType: String?): Boolean {
        if (extensionOf(fileName) in RISKY_EXTENSIONS) return true
        return mimeType?.lowercase(Locale.ROOT) == "application/vnd.android.package-archive"
    }

    /** Removes path separators and odd characters so a server-supplied name cannot escape the Downloads folder. */
    fun sanitizeFileName(raw: String?): String {
        var name = (raw ?: "").substringAfterLast('/').substringAfterLast('\\')
        name = name.map { c -> if (c.isLetterOrDigit() || c in "._-() ") c else '_' }.joinToString("")
        name = name.trim().trimStart('.')
        if (name.isEmpty()) return "download"
        if (name.length > MAX_NAME) {
            val ext = extensionOf(name)
            val keep = if (ext.isNotEmpty() && ext.length <= 10) ext.length + 1 else 0
            name = name.substring(0, MAX_NAME - keep) + (if (keep > 0) "." + ext else "")
        }
        return name
    }
}
