package com.xcsaber.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import androidx.lifecycle.lifecycleScope
import com.xcsaber.app.R
import com.xcsaber.app.XcApp
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.databinding.PanelPrivacyBinding
import com.xcsaber.app.privacy.TrackerListManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * The Privacy Center. Everything on it is read from the real components: [Prefs] for switches,
 * `TrackerListManager` for the list, `BlockStats` for counters. Nothing is estimated.
 */
class PrivacyCenter(
    private val activity: AppCompatActivity,
    private val xc: XcApp,
    private val bridge: PanelBridge,
    container: ViewGroup
) {
    private val b = PanelPrivacyBinding.inflate(LayoutInflater.from(activity), container, false)
    val root: View get() = b.root

    private val prefs: Prefs get() = xc.prefs
    private val nf: NumberFormat = NumberFormat.getInstance()
    private var updating = false
    private var updateMessage: String? = null
    private var updateFailed = false
    private var lastOn: Boolean? = null

    init {
        container.addView(b.root)
        b.root.visibility = View.GONE
        b.pcBack.setOnClickListener { bridge.closePanel() }
        b.pcMasterSwitch.setOnCheckedChangeListener { _, on ->
            prefs.trackerBlocking = on
            refresh()
            bridge.pluginsChanged()
            bridge.message(if (on) "Ad & tracker blocking on" else "Ad & tracker blocking off")
        }
        b.pcUpdateButton.setOnClickListener { updateList() }
        Motion.pressable(b.pcUpdateButton)
        refresh()
    }

    fun refresh() {
        refreshHero()
        refreshStats()
        refreshList()
        refreshSummary()
        buildControls()
    }

    private fun refreshHero() {
        val on = prefs.trackerBlocking
        val tint = activity.getColor(if (on) R.color.xc_blue else R.color.xc_faint)
        b.pcHero.setBackgroundResource(if (on) R.drawable.bg_hero else R.drawable.bg_hero_off)
        b.pcShield.setImageResource(if (on) R.drawable.ic_shield_check else R.drawable.ic_shield_off)
        ImageViewCompat.setImageTintList(b.pcShield, ColorStateList.valueOf(tint))
        b.pcGlow.visibility = if (on) View.VISIBLE else View.INVISIBLE
        b.pcStatusTitle.text = if (on) "Protection is on" else "Protection is off"
        b.pcStatusBody.text = if (on) {
            "Requests to " + nf.format(xc.trackers.blocker.size) + " known ad and tracker domains are blocked before they load."
        } else {
            "Ads and trackers are not being blocked. Pages may load more slowly and follow you across sites."
        }
        b.pcMasterSwitch.setOnCheckedChangeListener(null)
        b.pcMasterSwitch.isChecked = on
        b.pcMasterSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.trackerBlocking = checked
            refresh()
            bridge.pluginsChanged()
            bridge.message(if (checked) "Ad & tracker blocking on" else "Ad & tracker blocking off")
        }
        if (lastOn != null && lastOn != on) Motion.pulse(b.pcShield)
        lastOn = on
    }

    fun refreshStats() {
        b.pcStatPage.text = nf.format(bridge.blockedOnCurrentPage())
        b.pcStatSession.text = nf.format(xc.stats.sessionCount())
        b.pcStatTotal.text = nf.format(xc.stats.lifetimeCount())
        b.pcStatNote.text = "Counts only requests XCSABER actually refused. Blocks in private tabs count " +
            "for this session only and are never saved."
    }

    private fun refreshList() {
        val rows = ArrayList<Pair<String, String>>()
        val updatedAt = prefs.trackerListUpdatedAt
        rows.add("Source" to if (updatedAt > 0) "Peter Lowe's ad & tracker list" else "Built-in starter list")
        rows.add("Domains" to nf.format(xc.trackers.blocker.size))
        rows.add(
            "Last updated" to if (updatedAt > 0) {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updatedAt))
            } else {
                "Never"
            }
        )
        rows.add("Matching" to "Domain and its subdomains")
        b.pcListInfo.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        for ((k, v) in rows) {
            val row = inflater.inflate(R.layout.item_kv_row, b.pcListInfo, false)
            row.findViewById<TextView>(R.id.kvLabel).text = k
            row.findViewById<TextView>(R.id.kvValue).text = v
            b.pcListInfo.addView(row)
        }
        b.pcUpdateProgress.visibility = if (updating) View.VISIBLE else View.GONE
        b.pcUpdateButton.isEnabled = !updating
        b.pcUpdateButton.text = when {
            updating -> "Updating…"
            updateFailed -> "Try again"
            else -> activity.getString(R.string.update_tracker_list)
        }
        val msg = updateMessage
        if (msg == null) {
            b.pcUpdateStatus.visibility = View.GONE
        } else {
            b.pcUpdateStatus.visibility = View.VISIBLE
            b.pcUpdateStatus.text = msg
            b.pcUpdateStatus.setTextColor(activity.getColor(if (updateFailed) R.color.xc_error else R.color.xc_ok))
        }
    }

    private fun refreshSummary() {
        data class Line(val active: Boolean, val text: String)
        val guard = prefs.privacyGuard
        val lines = listOf(
            Line(
                prefs.trackerBlocking,
                if (prefs.trackerBlocking) "Known ad and tracker domains are blocked on every page"
                else "Ad and tracker blocking is off"
            ),
            Line(
                prefs.effectiveBlockThirdPartyCookies,
                when {
                    prefs.effectiveBlockThirdPartyCookies -> "Third-party cookies are blocked"
                    !guard -> "Third-party cookies are allowed (Privacy Guard is paused)"
                    else -> "Third-party cookies are allowed"
                }
            ),
            Line(
                prefs.effectiveHttpsOnly,
                if (prefs.effectiveHttpsOnly) "Web addresses are upgraded to HTTPS" else "HTTPS-only mode is off"
            ),
            Line(
                prefs.effectiveSendGpc,
                if (prefs.effectiveSendGpc) "Sending Global Privacy Control and Do Not Track signals"
                else "Not sending privacy signals to sites"
            ),
            Line(prefs.blockPopups, if (prefs.blockPopups) "Pop-ups opened without a tap are blocked" else "Pop-ups are allowed"),
            Line(
                true,
                if (xc.privacy.privateProfilesSupported) "Private tabs use a separate, temporary profile"
                else "Private tabs skip history, but this device cannot separate their cookies"
            ),
            Line(false, "Not hidden: your IP address is still visible to sites and your network")
        )
        b.pcSummary.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        for (l in lines) {
            val row = inflater.inflate(R.layout.item_status_row, b.pcSummary, false)
            row.findViewById<View>(R.id.statusDot).setBackgroundResource(if (l.active) R.drawable.bg_dot else R.drawable.bg_dot_off)
            row.findViewById<TextView>(R.id.statusText).text = l.text
            b.pcSummary.addView(row)
        }
    }

    private fun buildControls() {
        val items = listOf(
            SettingItem.Header("pc.h1", "Protections"),
            SettingItem.Toggle(
                "pc.guard", "Privacy Guard", "Master switch for cookies, HTTPS-only and privacy signals.",
                iconRes = R.drawable.ic_lock,
                checked = { prefs.privacyGuard },
                onChange = { prefs.privacyGuard = it; bridge.applyPageSettings(); bridge.pluginsChanged() }
            ),
            SettingItem.Toggle(
                "pc.cookies", "Block third-party cookies", "Stops embedded sites from setting cookies.",
                iconRes = R.drawable.ic_cookie,
                enabled = { prefs.privacyGuard },
                checked = { prefs.blockThirdPartyCookies },
                onChange = { prefs.blockThirdPartyCookies = it; bridge.applyPageSettings() }
            ),
            SettingItem.Toggle(
                "pc.https", "HTTPS-only mode", "Upgrades http:// links. Some old sites may not load.",
                iconRes = R.drawable.ic_lock,
                enabled = { prefs.privacyGuard },
                checked = { prefs.httpsOnly },
                onChange = { prefs.httpsOnly = it }
            ),
            SettingItem.Toggle(
                "pc.gpc", "Send Global Privacy Control", "Asks sites not to sell or share your data.",
                iconRes = R.drawable.ic_eye,
                enabled = { prefs.privacyGuard },
                checked = { prefs.sendGpc },
                onChange = { prefs.sendGpc = it }
            ),
            SettingItem.Toggle(
                "pc.popups", "Block pop-ups", "Blocks windows a page opens without you tapping.",
                iconRes = R.drawable.ic_ban,
                checked = { prefs.blockPopups },
                onChange = { prefs.blockPopups = it; bridge.applyPageSettings() }
            ),
            SettingItem.Header("pc.h2", "Your data"),
            SettingItem.Action(
                "pc.perms", "Website permissions", "Camera, microphone and location choices.",
                iconRes = R.drawable.ic_pin, onClick = { bridge.showSitePermissions() }
            ),
            SettingItem.Action(
                "pc.clear", "Clear browsing data", "Cookies, cache, history and more.",
                iconRes = R.drawable.ic_trash, onClick = { bridge.showClearDataDialog() }
            ),
            SettingItem.Action(
                "pc.private", "End private session", "Closes private tabs and deletes their data.",
                iconRes = R.drawable.ic_private, onClick = { bridge.endPrivateSessionNow() }
            ),
            SettingItem.Action(
                "pc.reset", "Reset blocked counter", "Sets the all-time count back to zero.",
                iconRes = R.drawable.ic_refresh, destructive = true,
                onClick = {
                    xc.stats.resetLifetime()
                    refreshStats()
                    bridge.message("Blocked counter reset")
                }
            ),
        )
        SettingRows.populate(b.pcControls, items) {
            // Rows that depend on another switch are re-evaluated once the toggle animation settles.
            b.pcControls.postDelayed({
                refreshHero(); refreshSummary()
                buildControls()
            }, 180)
        }
    }

    /** Downloads the maintained list off the main thread, with progress and a clear error state. */
    fun updateList() {
        if (updating) return
        updating = true
        updateMessage = null
        updateFailed = false
        refreshList()
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { xc.trackers.update() }
            updating = false
            when (result) {
                is TrackerListManager.UpdateResult.Success -> {
                    updateFailed = false
                    updateMessage = "List updated: " + nf.format(result.count) + " domains."
                }
                is TrackerListManager.UpdateResult.Failure -> {
                    updateFailed = true
                    updateMessage = "Couldn't update the list: " + result.reason + ". The previous list is still in use."
                }
            }
            refresh()
            bridge.message(if (updateFailed) "Blocklist update failed" else "Blocklist updated")
            bridge.pluginsChanged()
        }
    }

    val isUpdating: Boolean get() = updating
}
