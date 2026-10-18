package com.xcsaber.app.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.xcsaber.app.R

/** Bottom sheets used across the app: one look for menus, choices and details. */
object Sheets {

    /** Shows [content] in a bottom sheet that opens fully expanded when it is tall. */
    fun show(activity: Activity, content: View, expand: Boolean = true): BottomSheetDialog {
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(content)
        if (expand) {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
        dialog.show()
        return dialog
    }

    /** Single-choice sheet. [onPick] gets the chosen index; the sheet closes first. */
    fun choose(
        activity: Activity,
        title: String,
        labels: List<String>,
        selected: Int,
        note: String? = null,
        onPick: (Int) -> Unit
    ) {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.sheet_choice, null)
        root.findViewById<TextView>(R.id.choiceTitle).text = title
        val noteView = root.findViewById<TextView>(R.id.choiceNote)
        if (note.isNullOrBlank()) {
            noteView.visibility = View.GONE
        } else {
            noteView.text = note
            noteView.visibility = View.VISIBLE
        }
        val container = root.findViewById<LinearLayout>(R.id.choiceContainer)
        var dialog: BottomSheetDialog? = null
        labels.forEachIndexed { i, label ->
            val row = inflater.inflate(R.layout.item_choice_row, container, false)
            row.findViewById<TextView>(R.id.choiceLabel).text = label
            row.findViewById<View>(R.id.choiceCheck).visibility = if (i == selected) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener {
                dialog?.dismiss()
                onPick(i)
            }
            container.addView(row)
        }
        dialog = show(activity, root, expand = false)
    }

    data class MenuEntry(
        val id: Int,
        val group: Int,
        val title: CharSequence,
        val icon: Drawable?,
        val enabled: Boolean,
        val checkable: Boolean,
        val checked: Boolean
    )

    /** Reads visible items out of an inflated [Menu] (including enabled/checked state set in code). */
    fun entriesOf(menu: Menu): List<MenuEntry> {
        val out = ArrayList<MenuEntry>()
        for (i in 0 until menu.size()) {
            val m = menu.getItem(i)
            if (!m.isVisible) continue
            out.add(MenuEntry(m.itemId, m.groupId, m.title ?: "", m.icon, m.isEnabled, m.isCheckable, m.isChecked))
        }
        return out
    }

    /** The overflow menu: groups are separated by hairlines. */
    fun menu(activity: Activity, entries: List<MenuEntry>, onPick: (Int) -> Unit) {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.sheet_menu, null)
        val container = root.findViewById<LinearLayout>(R.id.menuContainer)
        var dialog: BottomSheetDialog? = null
        var lastGroup = Int.MIN_VALUE
        val dp = activity.resources.displayMetrics.density
        for (e in entries) {
            if (lastGroup != Int.MIN_VALUE && e.group != lastGroup) {
                val line = View(activity)
                line.setBackgroundColor(activity.getColor(R.color.xc_outline_soft))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1))
                lp.setMargins((20 * dp).toInt(), (6 * dp).toInt(), (20 * dp).toInt(), (6 * dp).toInt())
                container.addView(line, lp)
            }
            lastGroup = e.group
            val row = inflater.inflate(R.layout.item_menu_row, container, false)
            val icon = row.findViewById<ImageView>(R.id.menuIcon)
            if (e.icon != null) icon.setImageDrawable(e.icon) else icon.visibility = View.INVISIBLE
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(activity.getColor(R.color.xc_muted)))
            row.findViewById<TextView>(R.id.menuTitle).text = e.title
            val sw = row.findViewById<MaterialSwitch>(R.id.menuSwitch)
            if (e.checkable) {
                sw.visibility = View.VISIBLE
                sw.isChecked = e.checked
            }
            row.alpha = if (e.enabled) 1f else 0.38f
            row.isEnabled = e.enabled
            row.setOnClickListener {
                if (!e.enabled) return@setOnClickListener
                dialog?.dismiss()
                onPick(e.id)
            }
            container.addView(row)
        }
        dialog = show(activity, root)
    }
}
