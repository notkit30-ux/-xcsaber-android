package com.xcsaber.app.ui

/**
 * One row in a settings-style list. The same model drives the Settings screen, the Privacy Center
 * detail list and plugin configuration sheets, so they all look and behave alike.
 *
 * State is read through lambdas so a row always reflects the persisted value when it is bound.
 * The model is Android-free so it can be unit-tested.
 */
sealed class SettingItem {
    abstract val id: String
    open val title: String get() = ""
    open val summary: String get() = ""
    /** Extra words that should match the settings search but are not displayed. */
    open val keywords: String get() = ""

    data class Header(override val id: String, override val title: String) : SettingItem()

    data class Toggle(
        override val id: String,
        override val title: String,
        override val summary: String,
        val iconRes: Int = 0,
        override val keywords: String = "",
        val enabled: () -> Boolean = { true },
        val checked: () -> Boolean,
        val onChange: (Boolean) -> Unit,
    ) : SettingItem()

    /** A row that shows its current value and opens a chooser when tapped. */
    data class Choice(
        override val id: String,
        override val title: String,
        val iconRes: Int = 0,
        override val keywords: String = "",
        val value: () -> String,
        val onClick: () -> Unit,
    ) : SettingItem() {
        override val summary: String get() = ""
    }

    data class Action(
        override val id: String,
        override val title: String,
        override val summary: String,
        val iconRes: Int = 0,
        override val keywords: String = "",
        val destructive: Boolean = false,
        val onClick: () -> Unit,
    ) : SettingItem()

    data class Slider(
        override val id: String,
        override val title: String,
        override val summary: String = "",
        val min: Int,
        val max: Int,
        val step: Int,
        val iconRes: Int = 0,
        override val keywords: String = "",
        val enabled: () -> Boolean = { true },
        val value: () -> Int,
        val format: (Int) -> String = { it.toString() },
        val onChange: (Int) -> Unit,
    ) : SettingItem()

    /** Read-only label/value row (About page). */
    data class Info(
        override val id: String,
        override val title: String,
        val value: String,
        override val keywords: String = "",
    ) : SettingItem() {
        override val summary: String get() = ""
    }
}

/** Search over a settings list. */
object SettingsFilter {
    /**
     * Keeps rows whose title, summary or keywords contain every word of [query] (case-insensitive).
     * A header is kept only when at least one row under it matched. A blank query returns [items].
     */
    fun filter(items: List<SettingItem>, query: String): List<SettingItem> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return items
        val out = ArrayList<SettingItem>()
        var pendingHeader: SettingItem.Header? = null
        for (item in items) {
            if (item is SettingItem.Header) { pendingHeader = item; continue }
            val hay = (item.title + " " + item.summary + " " + item.keywords).lowercase()
            if (words.all { hay.contains(it) }) {
                pendingHeader?.let { out.add(it); pendingHeader = null }
                out.add(item)
            }
        }
        return out
    }
}
