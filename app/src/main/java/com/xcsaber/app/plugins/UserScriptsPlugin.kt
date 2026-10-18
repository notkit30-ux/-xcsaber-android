package com.xcsaber.app.plugins

import com.xcsaber.app.R
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.ui.SettingItem
import com.xcsaber.app.userscripts.UserScriptStore

/**
 * Tampermonkey-style userscripts. The user installs scripts from a web address, by pasting code or
 * from a file; XCSABER asks before installing and shows where the script will run. This is a native
 * script manager, not the Tampermonkey extension itself (WebView cannot load Chrome extensions).
 */
class UserScriptsPlugin(private val scripts: UserScriptStore) : BrowserPlugin {
    override val info = PluginInfo(
        id = "userscripts",
        name = "Userscripts",
        description = "Install Tampermonkey-style scripts (.user.js) from sites like Greasy Fork, or paste your own. " +
            "Scripts run inside the pages they match, so only install ones you trust.",
        category = PluginCategory.SCRIPTS,
        iconRes = R.drawable.ic_code,
        accent = PluginAccent.VIOLET,
    )

    override fun isEnabled(prefs: Prefs) = prefs.userScriptsEnabled

    override fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost) {
        prefs.userScriptsEnabled = enabled
        host.reloadCurrentPage()
        host.pluginsChanged()
        host.message(if (enabled) "Userscripts on" else "Userscripts off")
    }

    override fun status(prefs: Prefs): String {
        val all = scripts.all()
        if (!prefs.userScriptsEnabled) return "Off"
        if (all.isEmpty()) return "No scripts installed"
        return "${all.size} installed · ${all.count { it.enabled }} on"
    }

    override fun settings(prefs: Prefs, host: PluginHost): List<SettingItem> {
        val out = ArrayList<SettingItem>()
        out += SettingItem.Toggle(
            id = "us.enabled", title = "Run userscripts",
            summary = "Turn off to pause every script without deleting them.",
            iconRes = R.drawable.ic_code,
            checked = { prefs.userScriptsEnabled },
            onChange = { setEnabled(prefs, it, host) },
        )
        out += SettingItem.Header("us.h.add", "Add a script")
        out += SettingItem.Action("us.url", "From a web address", "Paste a link to a .user.js file (https only).", R.drawable.ic_globe,
            onClick = { host.addUserScript(UserScriptSource.URL) })
        out += SettingItem.Action("us.paste", "Paste code", "Paste the full script, including its ==UserScript== header.", R.drawable.ic_copy,
            onClick = { host.addUserScript(UserScriptSource.PASTE) })
        out += SettingItem.Action("us.file", "From a file", "Pick a .user.js or .js file from your phone.", R.drawable.ic_download,
            onClick = { host.addUserScript(UserScriptSource.FILE) })
        val all = scripts.all()
        out += SettingItem.Header("us.h.list", if (all.isEmpty()) "Installed scripts" else "Installed scripts (${all.size})")
        if (all.isEmpty()) {
            out += SettingItem.Info("us.none", "Nothing installed yet", "Tap a .user.js link on a website, or use the options above.")
        }
        for (s in all) {
            val ver = if (s.version.isNotBlank()) "v${s.version} · " else ""
            out += SettingItem.Toggle(
                id = "us.${s.id}", title = s.name,
                summary = ver + s.scopeLabel + if (s.unsupportedGrants.isNotEmpty()) " · limited support" else "",
                iconRes = R.drawable.ic_code,
                checked = { scripts.get(s.id)?.enabled ?: false },
                onChange = { scripts.setEnabled(s.id, it); host.reloadCurrentPage(); host.pluginsChanged() },
            )
            out += SettingItem.Action("us.${s.id}.view", "Details and code", "See where ${s.name} runs and read its code.", R.drawable.ic_info,
                onClick = { host.viewUserScript(s.id) })
            out += SettingItem.Action("us.${s.id}.del", "Remove ${s.name}", "Deletes this script from XCSABER.", R.drawable.ic_trash,
                destructive = true, onClick = { host.removeUserScript(s.id) })
        }
        return out
    }
}
