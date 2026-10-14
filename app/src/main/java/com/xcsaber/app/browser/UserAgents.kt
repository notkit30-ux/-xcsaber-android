package com.xcsaber.app.browser

/** Builds the user-agent strings used for mobile and "desktop site" mode. Pure functions. */
object UserAgents {

    /** The default WebView UA contains "; wv" and "Version/4.0", which make some sites serve broken pages. */
    fun mobile(defaultUa: String): String =
        defaultUa.replace("; wv)", ")").replace(" Version/4.0", "")

    fun desktop(defaultUa: String): String {
        val chrome = Regex("Chrome/([0-9.]+)").find(defaultUa)?.groupValues?.get(1) ?: "120.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chrome Safari/537.36"
    }
}
