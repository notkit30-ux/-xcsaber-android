package com.xcsaber.app.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.xcsaber.app.R
import com.xcsaber.app.XcApp
import com.xcsaber.app.databinding.PanelExtensionsBinding
import com.xcsaber.app.plugins.BrowserPlugin
import com.xcsaber.app.plugins.PluginAccent

/** Lists the native plugins compiled into this build, with a switch and a settings sheet each. */
class ExtensionsCenter(
    private val activity: AppCompatActivity,
    private val xc: XcApp,
    private val bridge: PanelBridge,
    container: ViewGroup
) {
    private val b = PanelExtensionsBinding.inflate(LayoutInflater.from(activity), container, false)
    val root: View get() = b.root

    init {
        container.addView(b.root)
        b.root.visibility = View.GONE
        b.exBack.setOnClickListener { bridge.closePanel() }
        refresh()
    }

    private fun tileFor(accent: PluginAccent): Pair<Int, Int> = when (accent) {
        PluginAccent.BLUE -> R.drawable.bg_tile_blue to R.color.xc_blue
        PluginAccent.CYAN -> R.drawable.bg_tile_cyan to R.color.xc_cyan
        PluginAccent.VIOLET -> R.drawable.bg_tile_violet to R.color.xc_violet
        PluginAccent.OK -> R.drawable.bg_tile_ok to R.color.xc_ok
        PluginAccent.WARN -> R.drawable.bg_tile_warn to R.color.xc_warn
    }

    /** Redraws the open plugin sheet, if any, so lists such as installed scripts stay current. */
    private var sheetRebuild: (() -> Unit)? = null

    fun refresh() {
        sheetRebuild?.invoke()
        val prefs = xc.prefs
        val groups = xc.plugins.grouped()
        val all = groups.flatMap { it.second }
        val active = all.count { it.isEnabled(prefs) }
        b.exSummary.text = "$active of ${all.size} plugins on"

        b.exList.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        for ((category, plugins) in groups) {
            val header = inflater.inflate(R.layout.item_setting_header, b.exList, false) as TextView
            header.text = category.label
            header.setPadding(header.paddingLeft - dp(20) + dp(4), header.paddingTop, header.paddingRight, header.paddingBottom)
            b.exList.addView(header)
            for (p in plugins) b.exList.addView(card(inflater, p))
        }
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun card(inflater: LayoutInflater, p: BrowserPlugin): View {
        val prefs = xc.prefs
        val v = inflater.inflate(R.layout.item_extension, b.exList, false)
        val (bg, colorRes) = tileFor(p.info.accent)
        val color = activity.getColor(colorRes)
        val icon = v.findViewById<ImageView>(R.id.extIcon)
        icon.setImageResource(p.info.iconRes)
        icon.setBackgroundResource(bg)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color))
        v.findViewById<TextView>(R.id.extName).text = p.info.name
        v.findViewById<TextView>(R.id.extDescription).text = p.info.description
        val enabled = p.isEnabled(prefs)
        val status = v.findViewById<TextView>(R.id.extStatus)
        status.text = p.status(prefs)
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (enabled) R.drawable.bg_dot else R.drawable.bg_dot_off, 0, 0, 0
        )
        val sw = v.findViewById<MaterialSwitch>(R.id.extSwitch)
        sw.contentDescription = p.info.name
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = enabled
        sw.setOnCheckedChangeListener { _, on ->
            p.setEnabled(prefs, on, bridge)
            // Redraw once the switch animation has finished.
            b.exList.postDelayed({ refresh() }, 180)
        }
        v.findViewById<MaterialButton>(R.id.extConfigure).setOnClickListener { openSettings(p) }
        return v
    }

    private fun openSettings(p: BrowserPlugin) {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.sheet_choice, null)
        root.findViewById<TextView>(R.id.choiceTitle).text = p.info.name
        val note = root.findViewById<TextView>(R.id.choiceNote)
        note.text = p.info.description
        note.visibility = View.VISIBLE
        val list = root.findViewById<LinearLayout>(R.id.choiceContainer)
        lateinit var rebuild: () -> Unit
        rebuild = {
            SettingRows.populate(list, p.settings(xc.prefs, bridge)) {
                list.postDelayed({ rebuild(); refresh() }, 180)
            }
        }
        rebuild()
        sheetRebuild = rebuild
        Sheets.show(activity, root).setOnDismissListener { sheetRebuild = null }
    }
}
