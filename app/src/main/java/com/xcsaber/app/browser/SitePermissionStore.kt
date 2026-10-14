package com.xcsaber.app.browser

import com.xcsaber.app.data.KvStore
import org.json.JSONObject

enum class SitePermission(val key: String, val label: String) {
    CAMERA("camera", "Camera"),
    MICROPHONE("mic", "Microphone"),
    LOCATION("location", "Location");

    companion object {
        fun fromKey(key: String): SitePermission? = values().firstOrNull { it.key == key }
    }
}

data class SiteDecision(val host: String, val permission: SitePermission, val allowed: Boolean)

/**
 * Remembers the user's per-site Allow/Block choices for camera, microphone and location.
 * A missing entry means "ask". Private tabs never write here.
 */
class SitePermissionStore(private val store: KvStore) {

    fun get(host: String, permission: SitePermission): Boolean? {
        val o = read()
        val key = key(host, permission)
        return if (o.has(key)) o.optBoolean(key) else null
    }

    fun set(host: String, permission: SitePermission, allowed: Boolean) {
        val o = read()
        o.put(key(host, permission), allowed)
        store.putString(KEY, o.toString())
    }

    fun clear(host: String, permission: SitePermission) {
        val o = read()
        o.remove(key(host, permission))
        store.putString(KEY, o.toString())
    }

    fun clearAll() = store.remove(KEY)

    fun all(): List<SiteDecision> {
        val o = read()
        val out = ArrayList<SiteDecision>()
        for (k in o.keys()) {
            val host = k.substringBefore('|')
            val perm = SitePermission.fromKey(k.substringAfter('|')) ?: continue
            out.add(SiteDecision(host, perm, o.optBoolean(k)))
        }
        return out.sortedWith(compareBy({ it.host }, { it.permission.ordinal }))
    }

    private fun key(host: String, permission: SitePermission) = host.lowercase() + "|" + permission.key

    private fun read(): JSONObject = try {
        JSONObject(store.getString(KEY, "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private companion object { const val KEY = "site_permissions" }
}
