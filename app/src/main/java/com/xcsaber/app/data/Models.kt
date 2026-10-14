package com.xcsaber.app.data

import org.json.JSONObject

data class Bookmark(val title: String, val url: String, val addedAt: Long) {
    fun toJson(): JSONObject = JSONObject().put("t", title).put("u", url).put("a", addedAt)

    companion object {
        fun fromJson(o: JSONObject) = Bookmark(o.optString("t"), o.optString("u"), o.optLong("a"))
    }
}

data class HistoryEntry(val title: String, val url: String, val visitedAt: Long) {
    fun toJson(): JSONObject = JSONObject().put("t", title).put("u", url).put("v", visitedAt)

    companion object {
        fun fromJson(o: JSONObject) = HistoryEntry(o.optString("t"), o.optString("u"), o.optLong("v"))
    }
}

data class Shortcut(val title: String, val url: String) {
    fun toJson(): JSONObject = JSONObject().put("t", title).put("u", url)

    companion object {
        fun fromJson(o: JSONObject) = Shortcut(o.optString("t"), o.optString("u"))
    }
}
