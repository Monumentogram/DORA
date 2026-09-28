package com.monumentogram.dora.stage0.ownedcorpus

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The task scrolls independently of the only primary action, including while the IME is open. */
// Bounded native view spacing and typography use density-independent dimensions.
@Suppress("MagicNumber")
class GuidedPage(private val activity: Activity) {
    val root = column()
    val title = text("DORA · 8 записей", 21f)
    val counts = text("4 задания на русском · 4 на английском", 14f)
    val navigation = LinearLayout(activity)
    val content = column().apply { tag = "task_content" }
    val scroll =
        ScrollView(activity).apply {
            tag = "task_scroll"
            isFillViewport = true
            addView(content)
        }
    val status =
        text("", 14f).apply {
            tag = "status"
            visibility = View.GONE
        }
    val hint =
        text("", 15f).apply {
            tag = "action_hint"
            visibility = View.GONE
        }
    val primary =
        button("Ожидаем задания", "primary") {}
            .apply {
                isEnabled = false
                setTextColor(activity.getColor(R.color.owned_on_primary))
                backgroundTintList =
                    ColorStateList(
                        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                        intArrayOf(
                            activity.getColor(R.color.owned_muted),
                            activity.getColor(R.color.owned_primary),
                        ),
                    )
            }

    init {
        root.setBackgroundColor(activity.getColor(R.color.owned_background))
        val header =
            column().apply {
                setPadding(dp(16), dp(4), dp(16), 0)
                addView(title)
                addView(counts)
                addView(navigation)
            }
        content.setPadding(dp(16), dp(8), dp(16), dp(16))
        val footer =
            column().apply {
                setPadding(dp(16), dp(4), dp(16), dp(8))
                setBackgroundColor(activity.getColor(R.color.owned_surface))
                addView(status)
                addView(hint)
                addView(primary)
            }
        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(footer)
        applyInsets()
    }

    fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    fun text(value: String, size: Float = 17f): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(activity.getColor(R.color.owned_on_surface))
            setPadding(0, dp(4), 0, dp(4))
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    fun button(label: String, tag: String, action: () -> Unit): Button =
        Button(activity).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            minHeight = dp(52)
            setTextColor(activity.getColor(R.color.owned_primary))
            backgroundTintList = ColorStateList.valueOf(activity.getColor(R.color.owned_surface))
            this.tag = tag
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    private fun applyInsets() {
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val edges =
                    insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            }
            insets
        }
    }
}
