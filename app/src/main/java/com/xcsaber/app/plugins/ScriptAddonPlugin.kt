package com.xcsaber.app.plugins

import com.xcsaber.app.data.Prefs
import com.xcsaber.app.ui.SettingItem

/** Shows a [ScriptAddon] in the Extensions Center and lets the user switch it on or off. */
class ScriptAddonPlugin(private val addon: ScriptAddon) : BrowserPlugin {
    override val info = PluginInfo(
        id = "addon." + addon.id,
        name = addon.name,
        description = addon.description,
        category = addon.category,
        iconRes = addon.iconRes,
        accent = addon.accent,
    )

    override fun isEnabled(prefs: Prefs) = prefs.isAddonEnabled(addon.id)

    override fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost) {
        prefs.setAddonEnabled(addon.id, enabled)
        host.reloadCurrentPage() // applies it now; switching off needs a fresh page to undo it
        host.pluginsChanged()
        host.message(if (enabled) "${addon.name} on" else "${addon.name} off")
    }

    override fun status(prefs: Prefs) = if (isEnabled(prefs)) "On · ${addon.scopeLabel}" else "Off"

    override fun settings(prefs: Prefs, host: PluginHost): List<SettingItem> = listOf(
        SettingItem.Toggle(
            id = "addon.${addon.id}.enabled", title = addon.name,
            summary = "Applies when a page loads. The current page reloads when you change this.",
            iconRes = addon.iconRes,
            checked = { prefs.isAddonEnabled(addon.id) },
            onChange = { setEnabled(prefs, it, host) },
        ),
        SettingItem.Info(id = "addon.${addon.id}.scope", title = "Works on", value = addon.scopeLabel),
        SettingItem.Info(id = "addon.${addon.id}.kind", title = "Type", value = "Built-in script, runs only inside web pages"),
    )
}
