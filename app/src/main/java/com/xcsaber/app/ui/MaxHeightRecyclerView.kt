package com.xcsaber.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** A RecyclerView that wraps its content but never grows taller than [maxHeightDp]. Used inside bottom sheets. */
class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var maxHeightDp: Int = 420

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxPx = (maxHeightDp * resources.displayMetrics.density).toInt()
        super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST))
    }
}
