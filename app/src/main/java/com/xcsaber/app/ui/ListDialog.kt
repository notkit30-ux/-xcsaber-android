package com.xcsaber.app.ui

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xcsaber.app.R

/** One reusable dialog for bookmarks, history, downloads and website permissions. */
object ListDialog {

    fun show(
        activity: Activity,
        title: String,
        emptyText: String,
        supplier: () -> List<Row>,
        onClick: (Row, AlertDialog) -> Unit,
        onRemove: ((Row) -> Unit)? = null,
        onClearAll: (() -> Unit)? = null,
        filterable: Boolean = false
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_list, null)
        val filter: EditText = view.findViewById(R.id.listFilter)
        val empty: TextView = view.findViewById(R.id.listEmpty)
        val recycler: RecyclerView = view.findViewById(R.id.listRecycler)
        empty.text = emptyText
        filter.visibility = if (filterable) View.VISIBLE else View.GONE

        var dialog: AlertDialog? = null
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
        refresh()

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.close, null)
        if (onClearAll != null) {
            builder.setNeutralButton(R.string.clear_all) { _, _ ->
                MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setMessage("Clear everything in this list?")
                    .setPositiveButton("Clear") { _, _ -> onClearAll() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        dialog = builder.show()
    }
}
