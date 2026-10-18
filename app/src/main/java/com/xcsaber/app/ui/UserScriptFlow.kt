package com.xcsaber.app.ui

import android.net.Uri
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xcsaber.app.R
import com.xcsaber.app.XcApp
import com.xcsaber.app.plugins.UserScriptSource
import com.xcsaber.app.userscripts.ParseResult
import com.xcsaber.app.userscripts.ScriptFetcher
import com.xcsaber.app.userscripts.UserScript
import com.xcsaber.app.userscripts.UserScriptParser
import com.xcsaber.app.userscripts.UserScriptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the user sees when adding, inspecting or removing a userscript. Nothing is installed
 * without an explicit confirmation that shows where the script will run.
 */
class UserScriptFlow(
    private val activity: AppCompatActivity,
    private val xc: XcApp,
    private val message: (String) -> Unit,
    private val pickFile: () -> Unit,
    private val changed: () -> Unit,
) {
    fun add(source: UserScriptSource) {
        when (source) {
            UserScriptSource.URL -> askText("Install from a web address", "https://example.com/script.user.js", false, "Download") {
                fetchAndOffer(it.trim())
            }
            UserScriptSource.PASTE -> askText("Paste a script", "// ==UserScript==\n// @name ...", true, "Continue") {
                offer(it, "")
            }
            UserScriptSource.FILE -> pickFile()
        }
    }

    /** Download [url] in the background, then show the install confirmation. */
    fun fetchAndOffer(url: String) {
        if (url.isBlank()) return
        message("Downloading script...")
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ScriptFetcher.fetch(url) }
            when (result) {
                is ScriptFetcher.Result.Text -> offer(result.body, url)
                is ScriptFetcher.Result.Failed -> message(result.reason)
            }
        }
    }

    fun onFilePicked(uri: Uri) {
        activity.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { ScriptFetcher.readLimited(it) }
                } catch (e: Exception) { null }
            }
            if (text == null) message("Couldn't read that file, or it is larger than ${UserScriptParser.MAX_CODE_CHARS / 1024} KB.")
            else offer(text, "")
        }
    }

    private fun offer(text: String, sourceUrl: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        when (val r = UserScriptParser.parse(text, sourceUrl, System.currentTimeMillis())) {
            is ParseResult.Error -> message(r.message)
            is ParseResult.Ok -> confirm(r.script, r.warnings)
        }
    }

    private fun confirm(s: UserScript, warnings: List<String>) {
        val existing = xc.userScripts.get(s.id)
        val sb = StringBuilder()
        sb.append(s.name)
        if (s.version.isNotBlank()) sb.append("  v").append(s.version)
        if (s.description.isNotBlank()) sb.append("\n").append(s.description)
        sb.append("\n\nRuns on:\n")
        (s.matches + s.includes).take(6).forEach { sb.append("  ").append(it).append('\n') }
        if (s.matches.size + s.includes.size > 6) sb.append("  and ${s.matches.size + s.includes.size - 6} more\n")
        if (s.runsEverywhere) sb.append("\nThis script runs on EVERY website you visit.\n")
        warnings.forEach { sb.append("\n").append(it).append('\n') }
        sb.append("\nScripts can read and change the pages they run on, including pages where you are signed in. ")
        sb.append("Only install scripts from authors you trust.")
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing != null) "Update script?" else "Install script?")
            .setMessage(sb.toString())
            .setPositiveButton(if (existing != null) "Update" else "Install") { _, _ -> install(s) }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton("View code") { _, _ -> showCode(s) { confirm(s, warnings) } }
            .show()
    }

    private fun install(s: UserScript) {
        when (xc.userScripts.save(s)) {
            UserScriptStore.Saved.ADDED -> message("Installed ${s.name}")
            UserScriptStore.Saved.UPDATED -> message("Updated ${s.name}")
            UserScriptStore.Saved.TOO_MANY -> {
                message("You can keep up to ${UserScriptStore.MAX_SCRIPTS} scripts. Remove one first.")
                return
            }
        }
        changed()
    }

    fun view(id: String) {
        val s = xc.userScripts.get(id) ?: return
        showCode(s, null)
    }

    private fun showCode(s: UserScript, back: (() -> Unit)?) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val tv = TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setTextColor(activity.getColor(R.color.xc_on_surface))
            setPadding(pad, pad / 2, pad, pad / 2)
            val head = "Runs on: ${s.scopeLabel}\nRun at: ${s.runAt.key}\n" +
                (if (s.grants.isNotEmpty()) "Grants: ${s.grants.joinToString(", ")}\n" else "") +
                (if (s.sourceUrl.isNotBlank()) "Source: ${s.sourceUrl}\n" else "") + "\n"
            val code = if (s.code.length > MAX_SHOWN) s.code.take(MAX_SHOWN) + "\n\n... (${s.code.length - MAX_SHOWN} more characters)" else s.code
            text = head + code
        }
        val scroll = ScrollView(activity).apply {
            addView(tv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(s.name)
            .setView(scroll)
            .setPositiveButton(if (back != null) "Back" else activity.getString(android.R.string.ok)) { _, _ -> back?.invoke() }
            .show()
    }

    fun remove(id: String) {
        val s = xc.userScripts.get(id) ?: return
        MaterialAlertDialogBuilder(activity)
            .setTitle("Remove ${s.name}?")
            .setMessage("The script is deleted from XCSABER. Pages that already loaded keep running it until you reload them.")
            .setPositiveButton("Remove") { _, _ ->
                xc.userScripts.remove(id)
                message("Removed ${s.name}")
                changed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askText(title: String, hint: String, multiline: Boolean, action: String, onText: (String) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_script_input, null)
        val field = view.findViewById<EditText>(R.id.scriptInputField)
        field.hint = hint
        if (!multiline) {
            field.maxLines = 1
            field.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        } else {
            field.minLines = 5
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(action) { _, _ -> onText(field.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object { const val MAX_SHOWN = 20_000 }
}
