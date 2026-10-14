package com.xcsaber.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.xcsaber.app.R
import com.xcsaber.app.browser.Tab
import com.xcsaber.app.data.Shortcut
import com.xcsaber.app.search.UrlUtils

/** One line in a list: suggestions, recent searches, bookmarks, history, downloads, permissions. */
data class Row(
    val title: String,
    val subtitle: String? = null,
    val key: String = title,
    @DrawableRes val icon: Int = R.drawable.ic_search
)

class RowAdapter(
    private val onClick: (Row) -> Unit,
    private val onRemove: ((Row) -> Unit)? = null
) : RecyclerView.Adapter<RowAdapter.Holder>() {

    private var rows: List<Row> = emptyList()

    fun submit(list: List<Row>) {
        rows = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.rowIcon)
        val title: TextView = view.findViewById(R.id.rowTitle)
        val subtitle: TextView = view.findViewById(R.id.rowSubtitle)
        val remove: ImageButton = view.findViewById(R.id.rowRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.icon.setImageResource(row.icon)
        holder.title.text = row.title
        holder.subtitle.text = row.subtitle.orEmpty()
        holder.subtitle.visibility = if (row.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.remove.visibility = if (onRemove == null) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(row) }
        holder.remove.setOnClickListener { onRemove?.invoke(row) }
    }
}

/** Home-screen shortcut grid. The last tile is always "Add". */
class ShortcutAdapter(
    private val onOpen: (Shortcut) -> Unit,
    private val onLongPress: (Shortcut) -> Unit,
    private val onAdd: () -> Unit,
    private val onAddLongPress: () -> Unit
) : RecyclerView.Adapter<ShortcutAdapter.Holder>() {

    private var items: List<Shortcut> = emptyList()

    fun submit(list: List<Shortcut>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val initial: TextView = view.findViewById(R.id.shortcutInitial)
        val title: TextView = view.findViewById(R.id.shortcutTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_shortcut, parent, false))

    override fun getItemCount(): Int = items.size + 1

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position == items.size) {
            holder.initial.text = "+"
            holder.title.text = "Add"
            holder.itemView.setOnClickListener { onAdd() }
            holder.itemView.setOnLongClickListener { onAddLongPress(); true }
            return
        }
        val item = items[position]
        holder.initial.text = item.title.trim().firstOrNull()?.uppercase() ?: "?"
        holder.title.text = item.title
        holder.itemView.setOnClickListener { onOpen(item) }
        holder.itemView.setOnLongClickListener { onLongPress(item); true }
    }
}

/** Tab switcher cards with a page preview. */
class TabsAdapter(
    private val onOpen: (Tab) -> Unit,
    private val onClose: (Tab) -> Unit
) : RecyclerView.Adapter<TabsAdapter.Holder>() {

    private var items: List<Tab> = emptyList()
    private var currentId: Long = -1L

    fun submit(list: List<Tab>, current: Long) {
        items = list.toList()
        currentId = current
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.tabCard)
        val title: TextView = view.findViewById(R.id.tabTitle)
        val host: TextView = view.findViewById(R.id.tabHost)
        val thumb: ImageView = view.findViewById(R.id.tabThumb)
        val privateIcon: ImageView = view.findViewById(R.id.tabPrivateIcon)
        val close: ImageButton = view.findViewById(R.id.tabClose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tab = items[position]
        val ctx = holder.itemView.context
        val selected = tab.id == currentId
        val onSurface = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnSurface)
        val primary = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimary)
        val outline = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOutline)
        val surface = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorSurface)
        val privateBg = ctx.getColor(R.color.xc_private_bar)

        holder.card.setCardBackgroundColor(if (tab.isPrivate) privateBg else surface)
        holder.card.strokeColor = if (selected) primary else outline
        holder.card.strokeWidth = (if (selected) 3 else 1) * ctx.resources.displayMetrics.density.toInt().coerceAtLeast(1)
        holder.title.setTextColor(if (tab.isPrivate) 0xFFFFFFFF.toInt() else onSurface)
        holder.host.setTextColor(if (tab.isPrivate) 0xFFCCCCCC.toInt() else onSurface)
        holder.title.text = if (tab.isBlank) "New tab" else tab.displayTitle()
        holder.host.text = when {
            tab.isPrivate -> "Private tab" + if (tab.isBlank) "" else " · " + UrlUtils.displayHost(tab.url)
            tab.isBlank -> "XCSABER home"
            else -> UrlUtils.displayHost(tab.url)
        }
        holder.privateIcon.visibility = if (tab.isPrivate) View.VISIBLE else View.GONE
        holder.privateIcon.setColorFilter(0xFFFFFFFF.toInt())
        holder.close.setColorFilter(if (tab.isPrivate) 0xFFFFFFFF.toInt() else onSurface)
        val thumb = tab.thumbnail
        if (thumb != null && !tab.isPrivate) holder.thumb.setImageBitmap(thumb) else holder.thumb.setImageDrawable(null)
        holder.card.setOnClickListener { onOpen(tab) }
        holder.close.setOnClickListener { onClose(tab) }
    }
}
