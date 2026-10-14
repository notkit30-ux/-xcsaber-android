package com.xcsaber.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentsTest {
    private val webView = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/118.0.0.0 Mobile Safari/537.36"

    @Test fun mobileLooksLikeChromeNotAWebView() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36",
            UserAgents.mobile(webView)
        )
    }

    @Test fun desktopKeepsChromeVersionAndDropsMobile() {
        val ua = UserAgents.desktop(webView)
        assertTrue(ua.contains("Chrome/118.0.0.0"))
        assertTrue(ua.contains("X11; Linux x86_64"))
        assertFalse(ua.contains("Mobile"))
        assertFalse(ua.contains("Android"))
    }

    @Test fun desktopFallsBackWhenVersionUnknown() {
        assertTrue(UserAgents.desktop("something odd").contains("Chrome/120.0.0.0"))
    }
}
