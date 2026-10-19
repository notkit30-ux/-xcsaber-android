package com.xcsaber.app.plugins

import com.xcsaber.app.data.Prefs
import com.xcsaber.app.ui.SettingItem
import org.junit.Assert.*
import org.junit.Test

class PluginRegistryTest {
    private class Fake(id: String, cat: PluginCategory, debug: Boolean = false) : BrowserPlugin {
        override val info = PluginInfo(id, id, "d", cat, 0, PluginAccent.BLUE, debug)
        override fun isEnabled(prefs: Prefs) = true
        override fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost) {}
        override fun status(prefs: Prefs) = ""
        override fun settings(prefs: Prefs, host: PluginHost): List<SettingItem> = emptyList()
    }

    private val all = listOf(
        Fake("blocker", PluginCategory.PRIVACY), Fake("guard", PluginCategory.PRIVACY),
        Fake("tools", PluginCategory.PAGE), Fake("dev", PluginCategory.DEVELOPER, debug = true),
    )

    @Test fun releaseHidesDebugOnly() {
        val r = PluginRegistry(all, debugBuild = false)
        assertEquals(listOf("blocker", "guard", "tools"), r.visible().map { it.info.id })
        assertNull(r.byId("dev"))
    }

    @Test fun debugShowsAll() {
        val r = PluginRegistry(all, debugBuild = true)
        assertEquals(4, r.visible().size); assertNotNull(r.byId("dev"))
    }

    @Test fun groupedInCategoryOrderSkippingEmpty() {
        val g = PluginRegistry(all, false).grouped()
        assertEquals(listOf(PluginCategory.PRIVACY, PluginCategory.PAGE), g.map { it.first })
        assertEquals(2, g[0].second.size)
    }

    @Test fun duplicateIdsIgnored() {
        val r = PluginRegistry(all + Fake("blocker", PluginCategory.PAGE), true)
        assertEquals(4, r.visible().size)
    }
}
