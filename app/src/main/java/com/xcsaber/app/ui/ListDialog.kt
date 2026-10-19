package com.xcsaber.app.ui

import android.app.Activity
import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xcsaber.app.R

/** One reusable bottom sheet for bookmarks, history, downloads and website permissions. */
object ListDialog {

    fun show(
        activity: Activity,
        title: String,
        emptyText: String,
        supplier: () -> List<Row>,
        onClick: (Row, Dialog) -> Unit,
        onRemove: ((Row) -> Unit)? = null,
        onClearAll: (() -> Unit)? = null,
        filterable: Boolean = false
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_list, null)
        view.findViewById<TextView>(R.id.sheetTitle).text = title
        val filter: EditText = view.findViewById(R.id.listFilter)
        val empty: View = view.findViewById(R.id.listEmptyState)
        empty.findViewById<TextView>(R.id.emptyTitle).text = title
        empty.findViewById<TextView>(R.id.emptyMessage).text = emptyText
        empty.findViewById<ImageView>(R.id.emptyIcon).setImageResource(R.drawable.ic_list)
        val recycler: MaxHeightRecyclerView = view.findViewById(R.id.listRecycler)
        val clearAll: MaterialButton = view.findViewById(R.id.sheetClearAll)
        filter.visibility = if (filterable) View.VISIBLE else View.GONE

        var dialog: BottomSheetDialog? = null
        lateinit var adapter: RowAdapter

        fun refresh() {
            val needle = filter.text.toString().trim().lowercase()
            val all = supplier()
            val shown = if (needle.isEmpty()) all else all.filter {
                it.title.lowercase().contains(needle) || (it.subtitle ?: "").lowercase().contains(needle)
            }
            adapter.submit(shown)
            empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
            clearAll.visibility = if (onClearAll != null && all.isNotEmpty()) View.VISIBLE else View.GONE
        }

        adapter = RowAdapter(
            onClick = { row -> dialog?.let { onClick(row, it) } },
            onRemove = onRemove?.let { remove -> { row: Row -> remove(row); refresh() } }
        )
        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = adapter
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        clearAll.setOnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage("Clear everything in this list?")
                .setPositiveButton("Clear") { _, _ ->
                    onClearAll?.invoke()
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        refresh()
        dialog = Sheets.show(activity, view)
    }
}
