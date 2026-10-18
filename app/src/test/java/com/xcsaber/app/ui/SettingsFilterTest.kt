package com.xcsaber.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFilterTest {
    private fun t(id: String, title: String, summary: String = "", kw: String = "") =
        SettingItem.Toggle(id, title, summary, keywords = kw, checked = { true }, onChange = {})

    private val items = listOf(
        SettingItem.Header("h1", "Privacy"),
        t("a", "Block trackers", "Stops known tracking domains", "ads"),
        t("b", "HTTPS only", "Upgrade connections"),
        SettingItem.Header("h2", "Appearance"),
        t("c", "Theme", "Dark or light"),
    )

    @Test fun blankReturnsEverything() = assertEquals(items, SettingsFilter.filter(items, "  "))

    @Test fun matchesTitleSummaryAndKeywords() {
        assertEquals(listOf("h1", "a"), SettingsFilter.filter(items, "tracker").map { it.id })
        assertEquals(listOf("h1", "a"), SettingsFilter.filter(items, "ADS").map { it.id })
        assertEquals(listOf("h1", "b"), SettingsFilter.filter(items, "upgrade").map { it.id })
    }

    @Test fun allWordsMustMatch() {
        assertEquals(listOf("h1", "a"), SettingsFilter.filter(items, "block known").map { it.id })
        assertEquals(emptyList<String>(), SettingsFilter.filter(items, "block theme").map { it.id })
    }

    @Test fun emptyHeadersDropped() {
        assertEquals(listOf("h2", "c"), SettingsFilter.filter(items, "dark").map { it.id })
    }
}
