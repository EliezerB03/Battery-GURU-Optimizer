package com.guru.batteryoptimizer.widgets
import com.guru.batteryoptimizer.R

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.animation.SeslRecoilAnimator
import androidx.appcompat.widget.SeslLinearLayoutCompat
import androidx.appcompat.widget.SwitchCompat

// ======= Widgets Main Class =======
private const val MOTION_EVENT_ACTION_PEN_DOWN = 211
private const val MOTION_EVENT_ACTION_PEN_UP = 212
open class ListItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    val container: View
    private val titleView: AppCompatTextView
    private val summaryView: AppCompatTextView
    private val topDivider: View
    private val bottomDivider: View
    protected val switchView: SwitchCompat
    private val recoilAnimator by lazy(LazyThreadSafetyMode.NONE) { SeslRecoilAnimator.Holder(context) }
    private val backgroundHolder by lazy(LazyThreadSafetyMode.NONE) { SeslLinearLayoutCompat.ItemBackgroundHolder() }
    private var viewsReady = false
    private var feedbackSkipped = false
    // Variables
    var title: CharSequence?
        get() = titleView.text
        set(value) { titleView.text = value }
    var summary: CharSequence?
        get() = summaryView.text
        set(value) {
            summaryView.text = value
            summaryView.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    var isSummaryUserUpdatable: Boolean = false
        set(value) {
            field = value
            applySummaryColor()
        }
    var showTopDivider: Boolean
        get() = topDivider.visibility == View.VISIBLE
        set(value) { topDivider.visibility = if (value) View.VISIBLE else View.GONE }
    var showBottomDivider: Boolean
        get() = bottomDivider.visibility == View.VISIBLE
        set(value) { bottomDivider.visibility = if (value) View.VISIBLE else View.GONE }
    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.list_item, this, true)
        container     = findViewById(R.id.item_container)
        titleView     = findViewById(R.id.item_title)
        summaryView   = findViewById(R.id.item_summary)
        topDivider    = findViewById(R.id.item_top_divider)
        bottomDivider = findViewById(R.id.item_bottom_divider)
        switchView    = findViewById(R.id.item_switch)
        switchView.isSaveEnabled = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.ListItemView, defStyleAttr, 0)
            try {
                title = a.getText(R.styleable.ListItemView_title)
                summary = a.getText(R.styleable.ListItemView_summary)
                showTopDivider = a.getBoolean(R.styleable.ListItemView_showTopDivider, false)
                showBottomDivider = a.getBoolean(R.styleable.ListItemView_showBottomDivider, false)
                isSummaryUserUpdatable = a.getBoolean(R.styleable.ListItemView_summaryUserUpdatable, false)
            } finally { a.recycle() }
        }
        viewsReady = true
        val p = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.enabled, android.R.attr.focusable, android.R.attr.clickable), defStyleAttr, 0)
        try {
            isEnabled = p.getBoolean(0, true)
            isFocusable = p.getBoolean(1, true)
            isClickable = p.getBoolean(2, true)
        } finally { p.recycle() }
    }
    // Attributes
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!viewsReady) return
        container.isEnabled = enabled
        switchView.isEnabled = enabled
        container.alpha = if (enabled) 1f else 0.4f
    }
    override fun setClickable(clickable: Boolean) {
        super.setClickable(clickable)
        if (!viewsReady) return
        container.isClickable = clickable
        if (!clickable) container.isHovered = false
    }
    override fun setFocusable(focusable: Boolean) {
        super.setFocusable(focusable)
        if (viewsReady) container.isFocusable = focusable
    }
    override fun setOnClickListener(l: OnClickListener?) {
        if (!viewsReady) { super.setOnClickListener(l); return }
        container.setOnClickListener { if (isEnabled) l?.onClick(this) }
    }
    override fun performClick(): Boolean = container.performClick()
    // Touch feedback
    protected open fun isFeedbackTarget(ev: MotionEvent): Boolean = true
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isEnabled && (isClickable || isFocusable)) {
            if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MOTION_EVENT_ACTION_PEN_DOWN) feedbackSkipped = !isFeedbackTarget(ev)
            if (!feedbackSkipped) animateTouchFeedback(ev)
        }
        return super.dispatchTouchEvent(ev)
    }
    private fun animateTouchFeedback(ev: MotionEvent) {
        when (ev.action) {
            MotionEvent.ACTION_DOWN, MOTION_EVENT_ACTION_PEN_DOWN -> {
                backgroundHolder.setPress(container)
                recoilAnimator.setPress(container)
            }
            MotionEvent.ACTION_UP, MOTION_EVENT_ACTION_PEN_UP -> {
                backgroundHolder.setRelease()
                recoilAnimator.setRelease()
            }
            MotionEvent.ACTION_CANCEL -> {
                backgroundHolder.setCancel()
                recoilAnimator.setRelease()
            }
        }
    }
    private fun applySummaryColor() {
        val attr = if (isSummaryUserUpdatable) androidx.appcompat.R.attr.colorPrimary else android.R.attr.textColorSecondary
        val tv = TypedValue()
        if (context.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) summaryView.setTextColor(context.getColorStateList(tv.resourceId))
            else summaryView.setTextColor(tv.data)
        }
    }
}

// ======= Widgets Class =======
// CardItemView Widget
class CardItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ListItemView(context, attrs, defStyleAttr)

// SwitchItemView Widget
class SwitchItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ListItemView(context, attrs, defStyleAttr) {
    var onCheckedChangedListener: ((SwitchItemView, Boolean) -> Unit)? = null
    private var externalClickListener: OnClickListener? = null
    var isChecked: Boolean
        get() = switchView.isChecked
        set(value) { if (switchView.isChecked != value) switchView.isChecked = value }
    init {
        switchView.visibility = View.VISIBLE
        switchView.setOnCheckedChangeListener { _, checked -> onCheckedChangedListener?.invoke(this, checked) }
        isClickable = true
        switchView.setOnClickListener { externalClickListener?.onClick(this) }
        container.setOnClickListener {
            if (isClickable) {
                switchView.toggle()
                externalClickListener?.onClick(this)
            }
        }
    }
    override fun setOnClickListener(l: OnClickListener?) { externalClickListener = l }
    override fun isFeedbackTarget(ev: MotionEvent): Boolean {
        val r = Rect(0, 0, switchView.width, switchView.height)
        offsetDescendantRectToMyCoords(switchView, r)
        return !r.contains(ev.x.toInt(), ev.y.toInt())
    }
}