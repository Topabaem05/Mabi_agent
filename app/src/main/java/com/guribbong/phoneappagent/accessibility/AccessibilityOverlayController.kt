package com.guribbong.phoneappagent.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.guribbong.phoneappagent.core.dsl.ScreenBounds

internal class AccessibilityOverlayController(
    context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val root = FrameLayout(context)
    private val statusView = TextView(context)
    private val detailView = TextView(context)
    private val highlightView = HighlightView(context)
    private var attached = false
    private var overlayUnavailable = false

    init {
        val pillBackground = GradientDrawable().apply {
            setColor(Color.argb(214, 20, 24, 31))
            cornerRadius = 36f
        }

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = pillBackground
            setPadding(32, 22, 32, 22)
            elevation = 12f
        }

        statusView.apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "Phone agent"
        }
        detailView.apply {
            setTextColor(Color.argb(230, 166, 214, 255))
            textSize = 12f
        }
        pill.addView(statusView)
        pill.addView(detailView)
        root.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = 56
            },
        )
        root.addView(
            highlightView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun render(state: OverlayHudState) {
        if (!state.enabled || !state.active) {
            detach()
            return
        }
        attach()
        if (overlayUnavailable) return
        statusView.text = state.statusLabel
        detailView.text = listOfNotNull(state.packageName, state.stepLabel).joinToString(" • ")
        highlightView.bounds = state.targetBounds
    }

    fun release() {
        detach()
    }

    private fun attach() {
        if (attached || overlayUnavailable) return
        try {
            windowManager.addView(root, layoutParams())
            attached = true
        } catch (error: RuntimeException) {
            overlayUnavailable = true
            attached = false
            Log.w("PhoneAppAgentOverlay", "Accessibility overlay unavailable; continuing without overlay.", error)
        }
    }

    private fun detach() {
        if (!attached) return
        try {
            windowManager.removeView(root)
        } catch (error: RuntimeException) {
            Log.w("PhoneAppAgentOverlay", "Accessibility overlay detach failed.", error)
        } finally {
            attached = false
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        )

    private class HighlightView(
        context: Context,
    ) : View(context) {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 70, 180, 255)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 70, 180, 255)
            style = Paint.Style.FILL
        }

        var bounds: ScreenBounds? = null
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val box = bounds ?: return
            if (box.isEmpty()) return
            canvas.drawRect(
                box.left.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.bottom.toFloat(),
                fillPaint,
            )
            canvas.drawRect(
                box.left.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.bottom.toFloat(),
                strokePaint,
            )
        }
    }
}
