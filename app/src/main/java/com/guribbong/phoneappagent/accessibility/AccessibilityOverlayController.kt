package com.guribbong.phoneappagent.accessibility

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.guribbong.phoneappagent.core.dsl.ScreenBounds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class AccessibilityOverlayController(
    context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val root = FrameLayout(context)
    private val waveBackdropView = InnerWaveBackdropView(context)
    private val statusView = TextView(context)
    private val detailView = TextView(context)
    private val highlightView = HighlightView(context)
    private var attached = false
    private var overlayUnavailable = false
    private var fadeAnimator: ValueAnimator? = null

    init {
        root.addView(
            waveBackdropView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

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
        if (!state.enabled && !state.active) {
            detach()
            return
        }
        if (!state.active) {
            fadeOutAndDetach()
            return
        }
        attach()
        if (overlayUnavailable) return
        cancelFadeOut()
        root.alpha = 1f
        waveBackdropView.start()
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
            root.alpha = 1f
            windowManager.addView(root, layoutParams())
            attached = true
            waveBackdropView.start()
        } catch (error: RuntimeException) {
            overlayUnavailable = true
            attached = false
            Log.w("PhoneAppAgentOverlay", "Accessibility overlay unavailable; continuing without overlay.", error)
        }
    }

    private fun detach() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        if (!attached) return
        detachFromWindow()
    }

    private fun fadeOutAndDetach() {
        if (!attached || overlayUnavailable) return
        highlightView.bounds = null
        if (fadeAnimator?.isRunning == true) return
        fadeAnimator = ValueAnimator.ofFloat(root.alpha, 0f).apply {
            duration = 2_400L
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                root.alpha = animator.animatedValue as Float
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (root.alpha <= 0.02f) {
                            fadeAnimator = null
                            if (attached) {
                                detachFromWindow()
                            }
                        }
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        root.alpha = 1f
                    }
                },
            )
            start()
        }
    }

    private fun detachFromWindow() {
        try {
            windowManager.removeView(root)
        } catch (error: RuntimeException) {
            Log.w("PhoneAppAgentOverlay", "Accessibility overlay detach failed.", error)
        } finally {
            waveBackdropView.stop()
            highlightView.bounds = null
            root.alpha = 1f
            attached = false
        }
    }

    private fun cancelFadeOut() {
        fadeAnimator?.cancel()
        fadeAnimator = null
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

    private class InnerWaveBackdropView(
        context: Context,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        private var animator: ValueAnimator? = null

        fun start() {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 9_000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    phase = animation.animatedFraction
                    invalidate()
                }
                start()
            }
        }

        fun stop() {
            animator?.cancel()
            animator = null
        }

        override fun onDetachedFromWindow() {
            stop()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width.toFloat()
            val height = height.toFloat()
            if (width <= 0f || height <= 0f) return

            val wave = (sin(phase * PI * 2.0).toFloat() + 1f) / 2f
            val drift = cos(phase * PI * 2.0).toFloat()
            canvas.drawColor(Color.argb(14, 255, 255, 255))
            drawEdgeGradients(canvas, width, height, wave)
            drawBlob(
                canvas = canvas,
                centerX = width * (-0.08f + wave * 0.18f),
                centerY = height * (0.16f + wave * 0.08f),
                radius = width * (0.58f + wave * 0.05f),
                alpha = 72,
            )
            drawBlob(
                canvas = canvas,
                centerX = width * (1.06f - wave * 0.16f),
                centerY = height * (0.32f - drift * 0.04f),
                radius = width * 0.68f,
                alpha = 56,
            )
            drawBlob(
                canvas = canvas,
                centerX = width * (0.5f + drift * 0.05f),
                centerY = height * (1.05f - wave * 0.1f),
                radius = width * 0.78f,
                alpha = 68,
            )
        }

        private fun drawEdgeGradients(
            canvas: Canvas,
            width: Float,
            height: Float,
            wave: Float,
        ) {
            paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height * 0.24f,
                Color.argb((54 + wave * 18).toInt(), 219, 234, 254),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width, height * 0.24f, paint)

            paint.shader = LinearGradient(
                0f,
                height,
                0f,
                height * 0.72f,
                Color.argb((62 + wave * 18).toInt(), 219, 234, 254),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, height * 0.72f, width, height, paint)

            paint.shader = LinearGradient(
                0f,
                0f,
                width * 0.28f,
                0f,
                Color.argb(42, 147, 197, 253),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width * 0.28f, height, paint)

            paint.shader = LinearGradient(
                width,
                0f,
                width * 0.72f,
                0f,
                Color.argb(42, 147, 197, 253),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(width * 0.72f, 0f, width, height, paint)
            paint.shader = null
        }

        private fun drawBlob(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            radius: Float,
            alpha: Int,
        ) {
            paint.shader = RadialGradient(
                centerX,
                centerY,
                radius,
                intArrayOf(
                    Color.argb(alpha, 167, 207, 255),
                    Color.argb(alpha / 3, 147, 197, 253),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(centerX, centerY, radius, paint)
            paint.shader = null
        }
    }

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
