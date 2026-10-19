package com.xcsaber.app.ui

import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Small, cheap animations. Everything here becomes instant when the user turned animations off. */
object Motion {

    /** True when the system animation scale is 0 ("Remove animations" / reduced motion). */
    fun reduced(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }

    /** Fades and lifts [views] in one after another. */
    fun enter(views: List<View>, stepMs: Long = 45L) {
        if (views.isEmpty()) return
        if (reduced(views[0].context)) {
            views.forEach { it.alpha = 1f; it.translationY = 0f }
            return
        }
        val rise = 14f * views[0].resources.displayMetrics.density
        views.forEachIndexed { i, v ->
            v.animate().cancel()
            v.alpha = 0f
            v.translationY = rise
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * stepMs)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Fades [view] in or out and ends in the right visibility. */
    fun fade(view: View, show: Boolean) {
        if (reduced(view.context)) {
            view.alpha = 1f
            view.visibility = if (show) View.VISIBLE else View.GONE
            return
        }
        view.animate().cancel()
        if (show) {
            if (view.visibility != View.VISIBLE) {
                view.alpha = 0f
                view.visibility = View.VISIBLE
            }
            view.animate().alpha(1f).setDuration(160L).start()
        } else if (view.visibility == View.VISIBLE) {
            view.animate().alpha(0f).setDuration(120L).withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
            }.start()
        }
    }

    /** Gentle press-down feedback. Does not consume the touch, so clicks keep working. */
    fun pressable(view: View) {
        view.setOnTouchListener { v, e ->
            if (!reduced(v.context)) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                }
            }
            false
        }
    }

    /** Soft pulse used once when a status changes (for example protection switched on). */
    fun pulse(view: View) {
        if (reduced(view.context)) return
        view.animate().cancel()
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.animate().scaleX(1f).scaleY(1f).setDuration(280L)
            .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
    }
}
