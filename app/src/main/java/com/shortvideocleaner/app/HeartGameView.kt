package com.shortvideocleaner.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*
import kotlin.random.Random

class HeartGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var backListener: (() -> Unit)? = null
    private var score = 0
    private var combo = 0
    private var maxCombo = 0
    private var heartCount = 0

    private val hearts = mutableListOf<FloatingHeart>()
    private val particles = mutableListOf<HeartParticle>()
    private val scorePopups = mutableListOf<ScorePopup>()
    private val ripples = mutableListOf<RippleRing>()
    private var animator: ValueAnimator? = null
    private var flashAlpha = 0

    private val heartPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 48f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val scorePaint = Paint().apply {
        isAntiAlias = true
        textSize = 42f
        color = Color.parseColor("#FFD700")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val comboPaint = Paint().apply {
        isAntiAlias = true
        textSize = 36f
        color = Color.parseColor("#FF69B4")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val hintPaint = Paint().apply {
        isAntiAlias = true
        textSize = 32f
        color = Color.parseColor("#99FFFFFF")
        textAlign = Paint.Align.CENTER
    }

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0A0A1A")
    }

    private val ripplePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    fun setOnBackListener(listener: () -> Unit) {
        backListener = listener
    }

    fun startGame() {
        score = 0
        combo = 0
        maxCombo = 0
        heartCount = 0
        hearts.clear()
        particles.clear()
        scorePopups.clear()
        ripples.clear()
        flashAlpha = 0
        startHeartSpawner()
        startAnimation()
    }

    private fun startHeartSpawner() {
        val spawner = object : Runnable {
            override fun run() {
                if (isAttachedToWindow) {
                    spawnHeart()
                    postDelayed(this, 600L + Random.nextInt(400))
                }
            }
        }
        postDelayed(spawner, 300)
    }

    private fun spawnHeart() {
        val w = width.toFloat()
        if (w <= 0) return

        hearts.add(FloatingHeart(
            x = Random.nextFloat() * (w - 100) + 50,
            y = -50f,
            size = Random.nextFloat() * 25 + 35,
            speed = Random.nextFloat() * 1.5f + 0.8f,
            wobbleSpeed = Random.nextFloat() * 2 + 1,
            wobbleAmount = Random.nextFloat() * 40 + 20,
            color = getRandomHeartColor(),
            alpha = 255
        ))
    }

    private fun getRandomHeartColor(): Int {
        val colors = listOf(
            Color.parseColor("#FF1493"),
            Color.parseColor("#FF69B4"),
            Color.parseColor("#FFB6C1"),
            Color.parseColor("#FF0000"),
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#FFD700"),
            Color.parseColor("#FF8C00"),
        )
        return colors[Random.nextInt(colors.size)]
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                updateHearts()
                updateParticles()
                updateScorePopups()
                updateRipples()
                if (flashAlpha > 0) flashAlpha = (flashAlpha - 20).coerceAtLeast(0)
                invalidate()
            }
            start()
        }
    }

    private fun updateHearts() {
        val h = height.toFloat()
        val iterator = hearts.iterator()
        while (iterator.hasNext()) {
            val heart = iterator.next()
            heart.y += heart.speed
            heart.wobblePhase += heart.wobbleSpeed * 0.05f
            heart.currentX = heart.x + sin(heart.wobblePhase) * heart.wobbleAmount

            if (heart.y > h + 100) {
                iterator.remove()
                combo = 0
            }
        }
    }

    private fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.1f
            p.alpha -= 5
            p.size *= 0.96f
            if (p.alpha <= 0 || p.size < 1) {
                iterator.remove()
            }
        }
    }

    private fun updateScorePopups() {
        val iterator = scorePopups.iterator()
        while (iterator.hasNext()) {
            val popup = iterator.next()
            popup.y -= 2
            popup.alpha -= 4
            popup.scale += 0.02f
            if (popup.alpha <= 0) {
                iterator.remove()
            }
        }
    }

    private fun updateRipples() {
        val iterator = ripples.iterator()
        while (iterator.hasNext()) {
            val r = iterator.next()
            r.radius += (r.maxRadius - r.radius) * 0.15f
            r.alpha -= 6
            if (r.alpha <= 0 || r.radius >= r.maxRadius * 0.98f) {
                iterator.remove()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchX = event.x
            val touchY = event.y

            var hitHeart: FloatingHeart? = null
            for (heart in hearts.reversed()) {
                val dx = touchX - heart.currentX
                val dy = touchY - heart.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance < heart.size * 1.5f) {
                    hitHeart = heart
                    break
                }
            }

            if (hitHeart != null) {
                hearts.remove(hitHeart)
                onHeartClicked(hitHeart)
            } else {
                combo = 0
            }
        }
        return true
    }

    private fun onHeartClicked(heart: FloatingHeart) {
        heartCount++
        combo++
        if (combo > maxCombo) maxCombo = combo

        val baseScore = 10
        val comboBonus = min(combo, 10) * 5
        val totalScore = baseScore + comboBonus
        score += totalScore

        // 爆炸粒子（连击越高越多）
        val particleCount = 25 + combo * 3
        spawnParticles(heart.currentX, heart.y, heart.color, particleCount)

        // 扩散波纹
        ripples.add(RippleRing(
            x = heart.currentX, y = heart.y,
            radius = heart.size * 0.5f,
            maxRadius = heart.size * 4f + combo * 10f,
            alpha = 200,
            color = heart.color
        ))

        // 连击屏幕闪光
        if (combo >= 5) {
            flashAlpha = min(combo * 15, 180)
        }

        scorePopups.add(ScorePopup(
            x = heart.currentX,
            y = heart.y,
            score = totalScore,
            combo = combo,
            alpha = 255,
            scale = 1f
        ))
    }

    private fun spawnParticles(x: Float, y: Float, color: Int, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2 * PI
            val speed = Random.nextFloat() * 12 + 3
            val particleColors = listOf(color, Color.parseColor("#FFD700"), Color.parseColor("#FFFFFF"), Color.parseColor("#FF69B4"))
            particles.add(HeartParticle(
                x = x,
                y = y,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat() - 8,
                size = Random.nextFloat() * 12 + 3,
                color = particleColors[Random.nextInt(particleColors.size)],
                alpha = 255
            ))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 绘制波纹
        for (ripple in ripples) {
            ripplePaint.strokeWidth = 3f
            ripplePaint.color = ripple.color
            ripplePaint.alpha = ripple.alpha.coerceIn(0, 255)
            canvas.drawCircle(ripple.x, ripple.y, ripple.radius, ripplePaint)
        }

        // 连击闪光
        if (flashAlpha > 0) {
            val flashPaint = Paint().apply {
                color = Color.WHITE
                alpha = flashAlpha
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, w, h, flashPaint)
        }

        // 绘制爱心
        for (heart in hearts) {
            drawHeart(canvas, heart.currentX, heart.y, heart.size, heart.color, heart.alpha)
        }

        // 绘制粒子
        for (particle in particles) {
            heartPaint.color = particle.color
            heartPaint.alpha = particle.alpha.coerceIn(0, 255)
            canvas.drawCircle(particle.x, particle.y, particle.size, heartPaint)
        }

        // 绘制得分弹出
        for (popup in scorePopups) {
            textPaint.alpha = popup.alpha.coerceIn(0, 255)
            textPaint.textSize = 36f * popup.scale

            if (popup.combo > 1) {
                canvas.drawText("+${popup.score}", popup.x, popup.y, textPaint)
                comboPaint.alpha = popup.alpha.coerceIn(0, 255)
                comboPaint.textSize = 28f * popup.scale
                canvas.drawText("x${popup.combo} COMBO!", popup.x, popup.y - 40, comboPaint)
            } else {
                canvas.drawText("+${popup.score}", popup.x, popup.y, textPaint)
            }
        }

        // 顶部分数
        scorePaint.textSize = 36f
        canvas.drawText("💕 $score 分 | 连击 x$combo", w / 2, 120f, scorePaint)

        // 底部统计
        if (heartCount > 0) {
            scorePaint.textSize = 32f
            canvas.drawText("收集: $heartCount 颗爱心", w / 2, h - 80, scorePaint)
            if (maxCombo > 1) {
                comboPaint.textSize = 28f
                canvas.drawText("最高连击: x$maxCombo", w / 2, h - 40, comboPaint)
            }
        } else {
            hintPaint.textSize = 28f
            hintPaint.alpha = (sin(System.currentTimeMillis() * 0.003) * 80 + 175).toInt()
            canvas.drawText("点击飘落的爱心收集爱意 💕", w / 2, h / 2, hintPaint)
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, alpha: Int) {
        heartPaint.color = color
        heartPaint.alpha = alpha.coerceIn(0, 255)

        val path = Path()
        val s = size / 2

        path.moveTo(cx, cy + s * 0.4f)
        path.cubicTo(cx - s, cy - s * 0.2f, cx - s * 0.8f, cy - s, cx, cy - s * 0.4f)
        path.cubicTo(cx + s * 0.8f, cy - s, cx + s, cy - s * 0.2f, cx, cy + s * 0.4f)
        path.close()
        canvas.drawPath(path, heartPaint)

        // 高光
        heartPaint.color = Color.WHITE
        heartPaint.alpha = (alpha * 0.3f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx - s * 0.2f, cy - s * 0.3f, s * 0.15f, heartPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        removeCallbacks(null)
    }

    private data class FloatingHeart(
        val x: Float,
        var y: Float,
        val size: Float,
        val speed: Float,
        val wobbleSpeed: Float,
        val wobbleAmount: Float,
        val color: Int,
        var alpha: Int,
        var currentX: Float = x,
        var wobblePhase: Float = Random.nextFloat() * 2 * PI.toFloat()
    )

    private data class HeartParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        val color: Int,
        var alpha: Int
    )

    private data class ScorePopup(
        var x: Float,
        var y: Float,
        val score: Int,
        val combo: Int,
        var alpha: Int,
        var scale: Float
    )

    private data class RippleRing(
        val x: Float,
        val y: Float,
        var radius: Float,
        val maxRadius: Float,
        var alpha: Int,
        val color: Int
    )
}
