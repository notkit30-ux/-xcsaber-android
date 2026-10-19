package com.xcsaber.app.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.xcsaber.app.R

/**
 * Turns [SettingItem]s into views. Shared by the Settings screen (RecyclerView), the Privacy
 * Center and the plugin configuration sheets (plain LinearLayouts), so every list looks alike.
 */
object SettingRows {
    private const val T_HEADER = 0
    private const val T_TOGGLE = 1
    private const val T_CHOICE = 2
    private const val T_ACTION = 3
    private const val T_SLIDER = 4
    private const val T_INFO = 5

    fun typeOf(item: SettingItem): Int = when (item) {
        is SettingItem.Header -> T_HEADER
        is SettingItem.Toggle -> T_TOGGLE
        is SettingItem.Choice -> T_CHOICE
        is SettingItem.Action -> T_ACTION
        is SettingItem.Slider -> T_SLIDER
        is SettingItem.Info -> T_INFO
    }

    private fun layoutFor(type: Int): Int = when (type) {
        T_HEADER -> R.layout.item_setting_header
        T_TOGGLE -> R.layout.item_setting_toggle
        T_CHOICE -> R.layout.item_setting_choice
        T_ACTION -> R.layout.item_setting_action
        T_SLIDER -> R.layout.item_setting_slider
        else -> R.layout.item_setting_info
    }

    fun create(inflater: LayoutInflater, parent: ViewGroup, type: Int): View =
        inflater.inflate(layoutFor(type), parent, false)

    /** [onChanged] runs after a control changed a value, so the host can refresh dependent rows. */
    fun bind(view: View, item: SettingItem, onChanged: () -> Unit) {
        when (item) {
            is SettingItem.Header -> (view as TextView).text = item.title
            is SettingItem.Toggle -> bindToggle(view, item, onChanged)
            is SettingItem.Choice -> {
                setIcon(view, item.iconRes)
                view.findViewById<TextView>(R.id.settingTitle).text = item.title
                view.findViewById<TextView>(R.id.settingValue).text = item.value()
                view.setOnClickListener { item.onClick() }
            }
            is SettingItem.Action -> bindAction(view, item)
            is SettingItem.Slider -> bindSlider(view, item, onChanged)
            is SettingItem.Info -> {
                view.findViewById<TextView>(R.id.settingTitle).text = item.title
                view.findViewById<TextView>(R.id.settingValue).text = item.value
            }
        }
    }

    private fun setIcon(view: View, res: Int) {
        val icon = view.findViewById<ImageView>(R.id.settingIcon)
        if (res == 0) {
            icon.visibility = View.GONE
        } else {
            icon.visibility = View.VISIBLE
            icon.setImageResource(res)
        }
    }

    private fun setSummary(view: View, text: String) {
        val s = view.findViewById<TextView>(R.id.settingSummary)
        s.text = text
        s.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun bindToggle(view: View, item: SettingItem.Toggle, onChanged: () -> Unit) {
        setIcon(view, item.iconRes)
        view.findViewById<TextView>(R.id.settingTitle).text = item.title
        setSummary(view, item.summary)
        val sw = view.findViewById<MaterialSwitch>(R.id.settingSwitch)
        val enabled = item.enabled()
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = item.checked()
        sw.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.45f
        sw.setOnCheckedChangeListener { _, on ->
            item.onChange(on)
            onChanged()
        }
        view.isEnabled = enabled
        view.setOnClickListener { if (enabled) sw.toggle() }
    }

    private fun bindAction(view: View, item: SettingItem.Action) {
        setIcon(view, item.iconRes)
        val ctx = view.context
        val title = view.findViewById<TextView>(R.id.settingTitle)
        title.text = item.title
        setSummary(view, item.summary)
        val icon = view.findViewById<ImageView>(R.id.settingIcon)
        if (item.destructive) {
            title.setTextColor(ctx.getColor(R.color.xc_error))
            icon.setBackgroundResource(R.drawable.bg_tile_error)
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ctx.getColor(R.color.xc_error)))
        } else {
            title.setTextColor(ctx.getColor(R.color.xc_on_surface))
            icon.setBackgroundResource(R.drawable.bg_tile_violet)
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ctx.getColor(R.color.xc_violet)))
        }
        view.setOnClickListener { item.onClick() }
    }

    private fun bindSlider(view: View, item: SettingItem.Slider, onChanged: () -> Unit) {
        setIcon(view, item.iconRes)
        view.findViewById<TextView>(R.id.settingTitle).text = item.title
        setSummary(view, item.summary)
        val valueLabel = view.findViewById<TextView>(R.id.settingValue)
        val slider = view.findViewById<Slider>(R.id.settingSlider)
        slider.clearOnChangeListeners()
        slider.valueFrom = item.min.toFloat()
        slider.valueTo = item.max.toFloat()
        slider.stepSize = item.step.toFloat()
        // A Slider throws if its value is not exactly on a step, so snap before assigning.
        val raw = item.value().coerceIn(item.min, item.max)
        val snapped = (item.min + ((raw - item.min + item.step / 2) / item.step) * item.step).coerceIn(item.min, item.max)
        slider.value = snapped.toFloat()
        valueLabel.text = item.format(snapped)
        val enabled = item.enabled()
        slider.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.45f
        slider.addOnChangeListener { _, v, fromUser ->
            valueLabel.text = item.format(v.toInt())
            if (fromUser) {
                item.onChange(v.toInt())
                onChanged()
            }
        }
    }

    /** Fills a LinearLayout with rows. Used for short lists inside scrolling screens and sheets. */
    fun populate(container: LinearLayout, items: List<SettingItem>, onChanged: () -> Unit) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        for (item in items) {
            val v = create(inflater, container, typeOf(item))
            bind(v, item, onChanged)
            container.addView(v)
        }
    }
}

/** RecyclerView adapter over a settings list. */
class SettingsAdapter(private val onChanged: () -> Unit = {}) :
    RecyclerView.Adapter<SettingsAdapter.Holder>() {

    private var items: List<SettingItem> = emptyList()

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    fun submit(list: List<SettingItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = SettingRows.typeOf(items[position])

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(SettingRows.create(LayoutInflater.from(parent.context), parent, viewType))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        SettingRows.bind(holder.itemView, items[position]) { onChanged() }
    }
}
