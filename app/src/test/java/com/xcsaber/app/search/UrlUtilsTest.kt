package com.xcsaber.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test fun bareDomainBecomesHttps() {
        assertEquals("https://example.com", UrlUtils.toNavigableUrl("example.com"))
        assertEquals("https://sub.example.co.uk/path?x=1", UrlUtils.toNavigableUrl("  sub.example.co.uk/path?x=1 "))
    }

    @Test fun explicitSchemeIsKept() {
        assertEquals("http://example.com/a?b=1", UrlUtils.toNavigableUrl("http://example.com/a?b=1"))
        assertEquals("https://example.com", UrlUtils.toNavigableUrl("https://example.com"))
    }

    @Test fun localhostAndIpAddressesAreUrls() {
        assertEquals("https://localhost:8080", UrlUtils.toNavigableUrl("localhost:8080"))
        assertEquals("https://192.168.1.1", UrlUtils.toNavigableUrl("192.168.1.1"))
    }

    @Test fun plainWordsAreSearchesNotUrls() {
        assertNull(UrlUtils.toNavigableUrl("cats"))
        assertNull(UrlUtils.toNavigableUrl("hello world"))
        assertNull(UrlUtils.toNavigableUrl("what is 5.5 apples"))
        assertNull(UrlUtils.toNavigableUrl("e.g."))
        assertNull(UrlUtils.toNavigableUrl(""))
        assertNull(UrlUtils.toNavigableUrl("   "))
    }

    @Test fun dangerousSchemesNeverNavigate() {
        assertNull(UrlUtils.toNavigableUrl("javascript:alert(1)"))
        assertNull(UrlUtils.toNavigableUrl("JAVASCRIPT:alert(1)"))
        assertNull(UrlUtils.toNavigableUrl("file:///etc/passwd"))
        assertNull(UrlUtils.toNavigableUrl("data:text/html,<script>alert(1)</script>"))
        assertNull(UrlUtils.toNavigableUrl("intent://x#Intent;end"))
        assertNull(UrlUtils.toNavigableUrl("ftp://example.com/file"))
        assertTrue(UrlUtils.isBlockedScheme("javascript:alert(1)"))
        assertTrue(UrlUtils.isBlockedScheme("content://media/x"))
        assertFalse(UrlUtils.isBlockedScheme("https://example.com"))
    }

    @Test fun classifySchemeSeparatesWebExternalAndBlocked() {
        assertEquals(UrlUtils.Kind.WEB_URL, UrlUtils.classifyScheme("https://a.com"))
        assertEquals(UrlUtils.Kind.WEB_URL, UrlUtils.classifyScheme("HTTP://a.com"))
        assertEquals(UrlUtils.Kind.EXTERNAL, UrlUtils.classifyScheme("mailto:me@example.com"))
        assertEquals(UrlUtils.Kind.EXTERNAL, UrlUtils.classifyScheme("tel:+123456"))
        assertEquals(UrlUtils.Kind.BLOCKED, UrlUtils.classifyScheme("intent://scan#Intent;end"))
        assertEquals(UrlUtils.Kind.BLOCKED, UrlUtils.classifyScheme("javascript:void(0)"))
        assertEquals(UrlUtils.Kind.BLOCKED, UrlUtils.classifyScheme("file:///sdcard/x"))
        assertEquals(UrlUtils.Kind.BLOCKED, UrlUtils.classifyScheme("notaurl"))
        assertEquals(UrlUtils.Kind.BLOCKED, UrlUtils.classifyScheme("weird+scheme://x"))
    }

    @Test fun validWebUrlNeedsSchemeAndHost() {
        assertTrue(UrlUtils.isValidWebUrl("https://example.com/path"))
        assertFalse(UrlUtils.isValidWebUrl("https://"))
        assertFalse(UrlUtils.isValidWebUrl("https:///nohost"))
        assertFalse(UrlUtils.isValidWebUrl("javascript:alert(1)"))
        assertFalse(UrlUtils.isValidWebUrl("not a url"))
    }

    @Test fun hostAndHttpsHelpers() {
        assertEquals("example.com", UrlUtils.hostOf("https://www.Example.com/path"))
        assertEquals("example.com", UrlUtils.displayHost("https://example.com"))
        assertTrue(UrlUtils.isHttps("https://example.com"))
        assertFalse(UrlUtils.isHttps("http://example.com"))
        assertEquals("https://a.com/x", UrlUtils.upgradeToHttps("http://a.com/x"))
        assertEquals("https://a.com/x", UrlUtils.upgradeToHttps("https://a.com/x"))
    }

    @Test fun intentFallbackOnlyAcceptsWebUrls() {
        val good = "intent://scan/#Intent;scheme=zxing;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fapp;end"
        assertEquals("https://example.com/app", UrlUtils.fallbackFromIntentUrl(good))
        val evil = "intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"
        assertNull(UrlUtils.fallbackFromIntentUrl(evil))
        assertNull(UrlUtils.fallbackFromIntentUrl("intent://x#Intent;end"))
        assertNull(UrlUtils.fallbackFromIntentUrl("https://example.com"))
    }
}
