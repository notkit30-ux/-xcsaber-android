package com.xcsaber.app.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil

/** Thin wrapper around Android's DownloadManager (system-managed, resumable, with notifications). */
class DownloadHelper(private val context: Context) {

    data class Status(val text: String, val completed: Boolean)

    private val manager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun guessName(url: String, contentDisposition: String?, mimeType: String?): String =
        DownloadPolicy.sanitizeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType))

    /** Queues the download and returns its id. May throw if the system refuses the request. */
    fun enqueue(url: String, name: String, mimeType: String?, userAgent: String?, cookie: String?): Long {
        require(DownloadPolicy.isAllowedUrl(url)) { "Only http and https downloads are supported" }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription(Uri.parse(url).host ?: "")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
        return manager.enqueue(request)
    }

    fun status(id: Long): Status {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
            ?: return Status("Unknown", false)
        cursor.use { c ->
            if (!c.moveToFirst()) return Status("Removed from device", false)
            val state = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            return when (state) {
                DownloadManager.STATUS_SUCCESSFUL -> Status("Completed", true)
                DownloadManager.STATUS_FAILED -> Status("Failed", false)
                DownloadManager.STATUS_PAUSED -> Status("Paused", false)
                DownloadManager.STATUS_PENDING -> Status("Waiting to start", false)
                else -> Status(
                    if (total > 0) "Downloading ${(soFar * 100 / total).toInt()}%" else "Downloading",
                    false
                )
            }
        }
    }

    /** An intent that opens the finished file in another app, or null if it is not available. */
    fun viewIntent(id: Long): Intent? {
        val uri = manager.getUriForDownloadedFile(id) ?: return null
        val mime = manager.getMimeTypeForDownloadedFile(id) ?: "*/*"
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Name of the finished file, used to decide whether to warn before opening. */
    fun fileName(id: Long): String? {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val title = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            return if (title >= 0) c.getString(title) else null
        }
    }
}
