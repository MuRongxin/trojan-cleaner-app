package com.shortvideocleaner.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class StarryBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stars = mutableListOf<Star>()
    private var phase = 0f
    private var animator: android.animation.ValueAnimator? = null

    // 星空配色
    private val starColors = listOf(
        0xFFE8F0FF.toInt(),  // 蓝白
        0xFFFFF4E0.toInt(),  // 暖白
        0xFFD4E4FF.toInt(),  // 淡蓝
        0xFFFFE8D0.toInt(),  // 淡橙
        0xFFC8D8FF.toInt(),  // 浅蓝
        0xFFFFF8F0.toInt(),  // 乳白
    )

    // 几团星云的中心（屏幕百分比）
    private val nebulae = listOf(
        Nebula(0.25f, 0.30f, 0.12f, 0x20A0C0FF.toInt()),
        Nebula(0.70f, 0.60f, 0.15f, 0x20C0A0FF.toInt()),
        Nebula(0.50f, 0.25f, 0.10f, 0x20FFC0A0.toInt()),
        Nebula(0.15f, 0.70f, 0.14f, 0x20A0FFC0.toInt()),
        Nebula(0.80f, 0.30f, 0.08f, 0x20FFA0C0.toInt()),
    )

    private val bgPaint = Paint().apply { color = 0xFF0A0A1A.toInt() }
    private val nebulaPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val starPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        generateStars()
    }

    private fun generateStars() {
        stars.clear()
        val count = (width.coerceAtMost(height) * 0.4f).toInt().coerceIn(80, 300)
        for (i in 0 until count) {
            val x = Random.nextFloat()
            val y = Random.nextFloat()
            val baseBrightness = Random.nextFloat() * 0.6f + 0.3f
            val radius = if (Random.nextFloat() < 0.08f) Random.nextFloat() * 2.5f + 1.5f else Random.nextFloat() * 1.2f + 0.3f
            val twinkleSpeed = Random.nextFloat() * 0.02f + 0.005f
            val twinkleOffset = Random.nextFloat() * TWO_PI
            val colorIdx = Random.nextInt(starColors.size)
            stars.add(Star(x, y, baseBrightness, radius, twinkleSpeed, twinkleOffset, colorIdx))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            generateStars()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun startAnimation() {
        stopAnimation()
        animator = android.animation.ValueAnimator.ofFloat(0f, TWO_PI).apply {
            duration = 12000L
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 深空背景
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 星云
        for (nebula in nebulae) {
            val cx = nebula.cx * w
            val cy = nebula.cy * h
            val r = nebula.radius * max(w, h)
            nebulaPaint.color = nebula.color
            nebulaPaint.alpha = (40 + (sin(phase * 0.7f + nebula.cx * 10f) * 20).toInt()).coerceIn(20, 80)
            canvas.drawCircle(cx, cy, r, nebulaPaint)
        }

        // 星星
        for (star in stars) {
            val sx = star.x * w
            val sy = star.y * h

            // 缓慢旋转（模拟天空流转）
            val angle = phase * 0.15f
            val rotatedX = w / 2 + (sx - w / 2) * cos(angle) - (sy - h / 2) * sin(angle)
            val rotatedY = h / 2 + (sx - w / 2) * sin(angle) + (sy - h / 2) * cos(angle)

            // 闪烁
            val twinkle = (sin(phase * star.twinkleSpeed * 60f + star.twinkleOffset) * 0.5f + 0.5f)
            val brightness = (star.baseBrightness + twinkle * 0.5f).coerceIn(0.2f, 1f)

            val color = starColors[star.colorIdx]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val alpha = (brightness * 255).toInt()

            starPaint.color = Color.argb(alpha, r, g, b)
            starPaint.alpha = alpha

            // 亮星带光晕
            if (star.radius > 1.8f) {
                glowPaint.color = Color.argb((alpha * 0.4f).toInt(), r, g, b)
                canvas.drawCircle(rotatedX, rotatedY, star.radius * 3f, glowPaint)
            }

            canvas.drawCircle(rotatedX, rotatedY, star.radius, starPaint)
        }
    }

    private data class Star(
        val x: Float,
        val y: Float,
        val baseBrightness: Float,
        val radius: Float,
        val twinkleSpeed: Float,
        val twinkleOffset: Float,
        val colorIdx: Int
    )

    private data class Nebula(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val color: Int
    )

    companion object {
        private const val TWO_PI = (Math.PI * 2).toFloat()
    }
}
