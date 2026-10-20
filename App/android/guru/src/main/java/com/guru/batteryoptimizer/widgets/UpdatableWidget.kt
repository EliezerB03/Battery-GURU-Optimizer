package com.guru.batteryoptimizer.widgets
import com.guru.batteryoptimizer.R

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.AppCompatTextView

// UpdatableWidget (Custom Widget)
class UpdatableWidget @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val widgetFrame: FrameLayout
    private val initialTextViewPaddings = mutableMapOf<AppCompatTextView, Int>()
    private var mainChild: View? = null
    private var lastAppliedReserve: Int = -1
    var onWidgetInflated: ((View) -> Unit)? = null
    var showWidget: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            widgetFrame.visibility = if (value) View.VISIBLE else View.GONE
            requestLayout()
        }
    var widgetMarginEnd: Int
        get() = (widgetFrame.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0
        set(value) {
            val lp = widgetFrame.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            if (lp.marginEnd == value) return
            lp.marginEnd = value
            widgetFrame.layoutParams = lp
            requestLayout()
        }
    init {
        var layoutResId = 0
        var initialShowWidget = true
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.UpdatableWidget)
            try {
                layoutResId = a.getResourceId(R.styleable.UpdatableWidget_widgetLayout, 0)
                initialShowWidget = a.getBoolean(R.styleable.UpdatableWidget_showWidget, true)
            } finally { a.recycle() }
        }
        widgetFrame = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL or Gravity.END
            ).apply { marginEnd = (20 * resources.displayMetrics.density).toInt() }
        }
        showWidget = initialShowWidget
        if (layoutResId != 0) setWidgetLayoutResource(layoutResId)
    }
    override fun onFinishInflate() {
        super.onFinishInflate()
        mainChild = getChildAt(0)
        addView(widgetFrame)
    }
    fun setWidgetLayoutResource(@LayoutRes layoutResId: Int) {
        widgetFrame.removeAllViews()
        if (layoutResId != 0) {
            val widgetView = LayoutInflater.from(context).inflate(layoutResId, widgetFrame, false)
            onWidgetInflated?.invoke(widgetView)
            widgetFrame.addView(widgetView)
        }
        requestLayout()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mainChild?.let { child ->
            val reserve = if (showWidget && widgetFrame.childCount > 0) {
                measureChild(widgetFrame, widthMeasureSpec, heightMeasureSpec)
                widgetFrame.measuredWidth + ((widgetFrame.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0)
            } else 0
            if (reserve != lastAppliedReserve) {
                lastAppliedReserve = reserve
                applyReserveToTextViews(child, reserve)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    private fun applyReserveToTextViews(view: View, reserve: Int) {
        if (view is AppCompatTextView) {
            val newPaddingEnd = initialTextViewPaddings.getOrPut(view) { view.paddingEnd } + reserve
            if (view.paddingEnd != newPaddingEnd) { view.setPaddingRelative(view.paddingStart, view.paddingTop, newPaddingEnd, view.paddingBottom) }
        } else if (view is ViewGroup) { for (i in 0 until view.childCount) applyReserveToTextViews(view.getChildAt(i), reserve) }
    }
}