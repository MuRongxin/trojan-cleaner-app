package com.shortvideocleaner.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class GeometricLinesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodes = mutableListOf<Node>()
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var mesh: List<Pair<Int, Int>> = emptyList()

    // 触摸吸引力
    private var touchX = -1f
    private var touchY = -1f
    private var touchStrength = 0f  // 0 → 1 → 0 衰减

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 不自动启动，由 Activity 控制
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            generateNodes(w, h)
        }
    }

    private fun generateNodes(w: Int, h: Int) {
        nodes.clear()
        val count = 12 + Random.nextInt(8)
        val margin = 60f
        for (i in 0 until count) {
            val x = margin + Random.nextFloat() * (w - 2 * margin)
            val y = margin + Random.nextFloat() * (h - 2 * margin)
            val driftAngle = Random.nextFloat() * TWO_PI
            val driftSpeed = Random.nextFloat() * 0.3f + 0.1f
            val driftAmp = Random.nextFloat() * 30f + 10f
            nodes.add(Node(x, y, driftAngle, driftSpeed, driftAmp))
        }

        // 建立 Delaunay 式的近邻连线（简单距离阈值）
        val pairs = mutableListOf<Pair<Int, Int>>()
        val maxDist = min(w, h) * 0.45f
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val dx = nodes[i].baseX - nodes[j].baseX
                val dy = nodes[i].baseY - nodes[j].baseY
                if (sqrt(dx * dx + dy * dy) < maxDist) {
                    pairs.add(i to j)
                }
            }
        }
        mesh = pairs
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        handleTouch(event.x, event.y, event.action == android.view.MotionEvent.ACTION_DOWN)
        return true
    }

    /** 供外部转发触摸事件 */
    fun handleTouch(x: Float, y: Float, isDown: Boolean) {
        if (isDown) {
            touchX = x
            touchY = y
            touchStrength = 1f
            invalidate()
        }
    }

    fun pauseAnimation() {
        animator?.cancel()
    }

    fun resumeAnimation() {
        animator?.cancel()
        phase = 0f
        animator = ValueAnimator.ofFloat(0f, TWO_PI).apply {
            duration = 24000L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase += 0.006f
                if (phase > 1000f) phase -= 1000f
                // 触摸吸引力衰减
                if (touchStrength > 0.01f) {
                    touchStrength *= 0.96f
                } else {
                    touchStrength = 0f
                }
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

        // 计算当前节点位置（基底 + 正弦漂移 + 触摸吸引）
        val positions = nodes.map { node ->
            val dx = sin(phase * node.driftSpeed + node.driftAngle) * node.driftAmp
            val dy = cos(phase * node.driftSpeed * 1.3f + node.driftAngle + 1f) * node.driftAmp * 0.7f
            var px = node.baseX + dx
            var py = node.baseY + dy

            // 触摸吸引力：节点向触摸点靠拢
            if (touchStrength > 0f && touchX >= 0f) {
                val toTouchX = touchX - px
                val toTouchY = touchY - py
                val distToTouch = sqrt(toTouchX * toTouchX + toTouchY * toTouchY)
                val maxPull = min(w, h) * 0.5f
                if (distToTouch < maxPull) {
                    val pullFactor = (1f - distToTouch / maxPull) * touchStrength * 0.6f
                    px += toTouchX * pullFactor
                    py += toTouchY * pullFactor
                }
            }

            px to py
        }

        // 连线
        for ((i, j) in mesh) {
            val (x1, y1) = positions[i]
            val (x2, y2) = positions[j]
            val dist = sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
            val maxDist = min(w, h) * 0.45f
            val alpha = ((1f - dist / maxDist) * 0.5f).coerceIn(0.03f, 0.5f)
            if (alpha <= 0) continue

            linePaint.color = Color.argb((alpha * 255).toInt(), 229, 90, 43) // primary_dark
            linePaint.strokeWidth = (0.8f + alpha * 3f).coerceIn(0.5f, 2f)
            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }

        // 节点
        for ((x, y) in positions) {
            dotPaint.color = Color.argb(80, 229, 90, 43)
            canvas.drawCircle(x, y, 2.5f, dotPaint)
        }
    }

    private data class Node(
        val baseX: Float,
        val baseY: Float,
        val driftAngle: Float,
        val driftSpeed: Float,
        val driftAmp: Float
    )

    companion object {
        private const val TWO_PI = (Math.PI * 2).toFloat()
    }
}
