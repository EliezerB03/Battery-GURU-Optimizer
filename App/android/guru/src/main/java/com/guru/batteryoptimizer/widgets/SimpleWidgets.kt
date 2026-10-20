package com.guru.batteryoptimizer.widgets
import com.guru.batteryoptimizer.R

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SeslSeekBar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.widget.TextViewCompat

// ======= Widgets Class =======
fun View.semSetToolTipText(text: CharSequence?) = TooltipCompat.setTooltipText(this, text)

// SeekBarPlus Widget
class SeekBarPlus @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.seekBarStyle
) : SeslSeekBar(context, attrs, defStyleAttr) {
    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.min), 0, 0)
            try { if (a.hasValue(0)) min = a.getInt(0, 0) } finally { a.recycle() }
        }
    }
}

// SectionSeparator Widget
class SectionSeparator @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {
    init {
        TextViewCompat.setTextAppearance(this, R.style.OneUI_Separator)
        val d = resources.displayMetrics.density
        setPaddingRelative((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
        minHeight = (20 * d).toInt()
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
    }
}