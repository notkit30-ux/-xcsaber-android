package com.xcsaber.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    @Test fun onlyHttpAndHttpsAreDownloadable() {
        assertTrue(DownloadPolicy.isAllowedUrl("https://example.com/a.pdf"))
        assertTrue(DownloadPolicy.isAllowedUrl("HTTP://example.com/a.pdf"))
        assertFalse(DownloadPolicy.isAllowedUrl("file:///sdcard/a.pdf"))
        assertFalse(DownloadPolicy.isAllowedUrl("content://media/1"))
        assertFalse(DownloadPolicy.isAllowedUrl("javascript:alert(1)"))
        assertFalse(DownloadPolicy.isAllowedUrl("data:text/plain,hi"))
        assertFalse(DownloadPolicy.isAllowedUrl("blob:https://example.com/x"))
    }

    @Test fun fileNamesCannotEscapeTheDownloadsFolder() {
        assertEquals("passwd", DownloadPolicy.sanitizeFileName("../../etc/passwd"))
        assertEquals("b.txt", DownloadPolicy.sanitizeFileName("a\\b.txt"))
        assertEquals("a.txt", DownloadPolicy.sanitizeFileName("  ..a.txt"))
        assertEquals("evil___name.pdf", DownloadPolicy.sanitizeFileName("evil<>:name.pdf"))
        assertEquals("download", DownloadPolicy.sanitizeFileName(""))
        assertEquals("download", DownloadPolicy.sanitizeFileName(null))
        assertEquals("download", DownloadPolicy.sanitizeFileName("..."))
    }

    @Test fun longNamesAreShortenedButKeepTheirExtension() {
        val name = DownloadPolicy.sanitizeFileName("a".repeat(300) + ".pdf")
        assertEquals(120, name.length)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test fun programsAndInstallersAreFlaggedAsRisky() {
        assertTrue(DownloadPolicy.isRisky("setup.APK", null))
        assertTrue(DownloadPolicy.isRisky("tool.exe", null))
        assertTrue(DownloadPolicy.isRisky("run.sh", "text/plain"))
        assertTrue(DownloadPolicy.isRisky("download", "application/vnd.android.package-archive"))
        assertFalse(DownloadPolicy.isRisky("photo.jpg", "image/jpeg"))
        assertFalse(DownloadPolicy.isRisky("notes.pdf", null))
    }

    @Test fun extensionParsing() {
        assertEquals("gz", DownloadPolicy.extensionOf("archive.tar.GZ"))
        assertEquals("", DownloadPolicy.extensionOf("noextension"))
        assertEquals("", DownloadPolicy.extensionOf("trailingdot."))
    }
}
