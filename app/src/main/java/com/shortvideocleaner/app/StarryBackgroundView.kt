package com.shortvideocleaner.app

import android.animation.ValueAnimator
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

    // ── 动画状态 ──
    private var phase = 0f          // 0..2π，缓慢推进
    private var timeSec = 0f        // 累计秒数，用于流星调度
    private var animator: ValueAnimator? = null

    // ── 星星层级 ──
    private val dimStars = mutableListOf<Star>()       // 暗星（银河带背景）
    private val brightStars = mutableListOf<Star>()     // 亮星（闪烁）
    private val highlights = mutableListOf<Star>()      // 高亮大星（带光晕）

    // ── 星云 ──
    private val nebulae = mutableListOf<Nebula>()

    // ── 流星 ──
    private var shootingStar: ShootingStar? = null
    private var nextShootingTime = 0f   // 下次流星出现的时间

    // ═══════════════════════════════════════════
    //  画笔
    // ═══════════════════════════════════════════

    private val bgPaint = Paint().apply { color = 0xFF060612.toInt() }

    private val nebulaPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val starPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val trailPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** 柔和径向渐变光晕（复用，每帧更新坐标和颜色） */
    private var glowGradient: RadialGradient? = null

    // ═══════════════════════════════════════════
    //  初始化
    // ═══════════════════════════════════════════

    init {
        generateNebulae()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            generateStars(w, h)
        }
    }

    // ═══════════════════════════════════════════
    //  星空数据生成
    // ═══════════════════════════════════════════

    private fun generateNebulae() {
        nebulae.clear()
        nebulae.add(Nebula(0.22f, 0.28f, 0.14f, 0xFF2A3A6E.toInt(), 0xFF0E1445.toInt()))
        nebulae.add(Nebula(0.72f, 0.55f, 0.18f, 0xFF3A2050.toInt(), 0xFF1A0E28.toInt()))
        nebulae.add(Nebula(0.48f, 0.70f, 0.13f, 0xFF1E3A5A.toInt(), 0xFF0C1A30.toInt()))
        nebulae.add(Nebula(0.85f, 0.22f, 0.11f, 0xFF2A2848.toInt(), 0xFF121028.toInt()))
        nebulae.add(Nebula(0.15f, 0.65f, 0.15f, 0xFF2E1E3E.toInt(), 0xFF140C22.toInt()))
        nebulae.add(Nebula(0.55f, 0.32f, 0.09f, 0xFF1C3048.toInt(), 0xFF0A1828.toInt()))
    }

    private fun generateStars(w: Int, h: Int) {
        dimStars.clear()
        brightStars.clear()
        highlights.clear()

        val baseCount = (min(w, h) * 0.55f).toInt().coerceIn(150, 500)
        val cx = w / 2f
        val cy = h / 2f
        val diag = hypot(w.toFloat(), h.toFloat())

        // --- 银河带：沿屏幕对角线方向的高密度暗星区 ---
        val milkyWayAngle = -0.6f  // 银河倾斜角（弧度）
        val milkyWayWidth = 0.28f  // 银河带宽度（屏幕百分比）

        for (i in 0 until baseCount) {
            val isInMilkyWay = Random.nextFloat() < 0.55f

            val x: Float
            val y: Float
            if (isInMilkyWay) {
                // 沿银河方向分布
                val along = Random.nextFloat()
                val offset = (Random.nextFloat() - 0.5f) * 2f * milkyWayWidth
                val baseX = along * w
                val baseY = along * h
                x = (baseX + offset * diag * cos(milkyWayAngle + PI.toFloat() / 2)).coerceIn(0f, w.toFloat())
                y = (baseY + offset * diag * sin(milkyWayAngle + PI.toFloat() / 2)).coerceIn(0f, h.toFloat())
            } else {
                x = Random.nextFloat() * w
                y = Random.nextFloat() * h
            }

            val radius: Float
            val brightness: Float
            val twinkleSpeed: Float
            val color: Int

            val roll = Random.nextFloat()
            when {
                roll < 0.06f -> {
                    // 高亮大星
                    radius = Random.nextFloat() * 2.5f + 2.0f
                    brightness = Random.nextFloat() * 0.3f + 0.7f
                    twinkleSpeed = Random.nextFloat() * 0.4f + 0.6f
                    color = pickStarColor(brightStar = true)
                    highlights.add(Star(x / w, y / h, brightness, radius, twinkleSpeed, Random.nextFloat() * TWO_PI, color))
                }
                roll < 0.35f -> {
                    // 普通亮星
                    radius = Random.nextFloat() * 1.4f + 0.6f
                    brightness = Random.nextFloat() * 0.35f + 0.45f
                    twinkleSpeed = Random.nextFloat() * 0.7f + 0.3f
                    color = pickStarColor(brightStar = false)
                    brightStars.add(Star(x / w, y / h, brightness, radius, twinkleSpeed, Random.nextFloat() * TWO_PI, color))
                }
                else -> {
                    // 银河暗星
                    radius = Random.nextFloat() * 0.9f + 0.2f
                    brightness = if (isInMilkyWay) Random.nextFloat() * 0.25f + 0.15f else Random.nextFloat() * 0.2f + 0.08f
                    twinkleSpeed = Random.nextFloat() * 0.3f + 0.05f
                    color = pickStarColor(brightStar = false)
                    dimStars.add(Star(x / w, y / h, brightness, radius, twinkleSpeed, Random.nextFloat() * TWO_PI, color))
                }
            }
        }

        // 初始流星时间
        nextShootingTime = 3f + Random.nextFloat() * 6f
    }

    private fun pickStarColor(brightStar: Boolean): Int {
        return when (Random.nextInt(if (brightStar) 10 else 14)) {
            0 -> 0xFFE8F0FF.toInt()  // 蓝白
            1 -> 0xFFFFF4E0.toInt()  // 暖白
            2 -> 0xFFD4E4FF.toInt()  // 淡蓝
            3 -> 0xFFFFE8D0.toInt()  // 淡橙
            4, 5 -> 0xFFC8D8FF.toInt()  // 浅蓝
            6, 7 -> 0xFFFFF8F0.toInt()  // 乳白
            8 -> 0xFFFFD4C0.toInt()  // 桃白
            9 -> 0xFFD0E0FF.toInt()  // 冷蓝
            10 -> 0xFFFFF0D0.toInt() // 淡金
            11 -> 0xFFE0D8FF.toInt() // 淡紫
            12 -> 0xFFFFD8E0.toInt() // 粉白
            else -> 0xFFF0F0FF.toInt()
        }
    }

    // ═══════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 不自动启动，由 Activity 通过 resumeAnimation() 统一控制
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun startAnimation() {
        stopAnimation()
        phase = 0f
        timeSec = 0f
        nextShootingTime = 3f + Random.nextFloat() * 6f
        shootingStar = null
        // 每 50ms 推进一帧，phase 连续递增绝不跳变
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 50L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                phase += 0.013f
                if (phase > 1000f) phase -= 1000f
                timeSec += 0.05f
                updateShootingStar()
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    fun pauseAnimation() {
        stopAnimation()
    }

    fun resumeAnimation() {
        startAnimation()
    }

    /** 流星调度 */
    private fun updateShootingStar() {
        if (shootingStar != null) {
            shootingStar!!.life += 0.016f
            if (shootingStar!!.life > shootingStar!!.maxLife) {
                shootingStar = null
                nextShootingTime = timeSec + 4f + Random.nextFloat() * 10f  // 4-14 秒后下一颗
            }
        } else if (timeSec >= nextShootingTime) {
            spawnShootingStar()
        }
    }

    private fun spawnShootingStar() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 从屏幕左/上边缘出现，向右/下飞行
        val angle = Random.nextFloat() * 0.6f + 0.25f  // ~15°-50° 倾斜
        val startX: Float
        val startY: Float
        if (Random.nextBoolean()) {
            startX = Random.nextFloat() * w * 0.4f
            startY = Random.nextFloat() * h * 0.15f
        } else {
            startX = Random.nextFloat() * w * 0.2f
            startY = Random.nextFloat() * h * 0.3f
        }

        shootingStar = ShootingStar(
            startX = startX,
            startY = startY,
            angle = angle,
            speed = Random.nextFloat() * 0.6f + 0.8f,  // 屏幕对角线比例/秒
            length = Random.nextFloat() * 0.06f + 0.04f, // 尾迹长度
            life = 0f,
            maxLife = Random.nextFloat() * 0.6f + 0.7f   // 0.7-1.3 秒
        )
    }

    // ═══════════════════════════════════════════
    //  绘制
    // ═══════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val diag = hypot(w, h)

        // ── 1. 深空背景 ──
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ── 2. 星云（柔和渐变光团）──
        drawNebulae(canvas, w, h)

        // ── 3. 银河暗星（密密麻麻的背景）──
        drawStarLayer(canvas, dimStars, w, h, withGlow = false)

        // ── 4. 普通亮星 ──
        drawStarLayer(canvas, brightStars, w, h, withGlow = false)

        // ── 5. 高亮大星（带光晕）──
        drawStarLayer(canvas, highlights, w, h, withGlow = true)

        // ── 6. 流星 ──
        shootingStar?.let { drawShootingStar(canvas, it, w, h, diag) }
    }

    /** 星云：用多层 RadialGradient 叠加出柔和光团 */
    private fun drawNebulae(canvas: Canvas, w: Float, h: Float) {
        val scale = max(w, h)

        for (nebula in nebulae) {
            val cx = nebula.cx * w
            val cy = nebula.cy * h
            val radius = nebula.radius * scale

            // 缓慢呼吸
            val breathe = sin(phase * 0.5f + nebula.cx * 10f) * 0.3f + 0.7f
            val alpha = (breathe * 65f).toInt().coerceIn(18, 80)

            val centerColor = Color.argb(alpha, Color.red(nebula.color), Color.green(nebula.color), Color.blue(nebula.color))
            val edgeColor = Color.argb(0, Color.red(nebula.edgeColor), Color.green(nebula.edgeColor), Color.blue(nebula.edgeColor))

            val safeR = max(radius, 0.01f)
            glowGradient = RadialGradient(cx, cy, safeR, centerColor, edgeColor, Shader.TileMode.CLAMP)
            nebulaPaint.shader = glowGradient
            canvas.drawCircle(cx, cy, safeR, nebulaPaint)
        }
        nebulaPaint.shader = null
    }

    /** 绘制一层星星 */
    private fun drawStarLayer(canvas: Canvas, stars: List<Star>, w: Float, h: Float, withGlow: Boolean) {
        val cx = w / 2f
        val cy = h / 2f
        // 非常缓慢的旋转
        val rotAngle = phase * 0.08f
        val cosA = cos(rotAngle)
        val sinA = sin(rotAngle)

        for (star in stars) {
            val sx = star.x * w
            val sy = star.y * h

            // 绕中心旋转
            val rx = cx + (sx - cx) * cosA - (sy - cy) * sinA
            val ry = cy + (sx - cx) * sinA + (sy - cy) * cosA

            // 闪烁：用两个不同频率的正弦叠加，产生更自然的闪烁
            val twinkle1 = sin(phase * star.twinkleSpeed * 45f + star.twinkleOffset)
            val twinkle2 = sin(phase * star.twinkleSpeed * 71f + star.twinkleOffset + 1.7f) * 0.3f
            val twinkle = (twinkle1 * 0.7f + twinkle2 + 0.7f) / 2f
            val brightness = (star.baseBrightness + twinkle * 0.45f).coerceIn(0.1f, 1f)

            val color = star.color
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val alpha = (brightness * 255).toInt()

            starPaint.color = Color.argb(alpha, r, g, b)

            if (withGlow && star.radius > 1.5f && brightness > 0.5f) {
                // 柔和光晕
                val glowAlpha = (brightness * 0.25f * 255).toInt()
                val starGlowR = max(star.radius * 4f, 0.01f)
                glowPaint.shader = RadialGradient(
                    rx, ry, starGlowR,
                    Color.argb(glowAlpha, r, g, b),
                    Color.argb(0, r, g, b),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(rx, ry, starGlowR, glowPaint)
                glowPaint.shader = null
            }

            // 大于 1.8px 的星画成十字星芒
            if (star.radius > 1.8f && brightness > 0.6f) {
                drawStarCross(canvas, rx, ry, star.radius, brightness, r, g, b)
            } else {
                canvas.drawCircle(rx, ry, star.radius, starPaint)
            }
        }
    }

    /** 十字星芒 */
    private fun drawStarCross(canvas: Canvas, x: Float, y: Float, radius: Float, brightness: Float, r: Int, g: Int, b: Int) {
        val len = radius * 3.5f * brightness
        val crossAlpha = (brightness * 0.7f * 255).toInt()
        trailPaint.color = Color.argb(crossAlpha, r, g, b)
        trailPaint.strokeWidth = radius * 0.55f

        // 水平芒
        canvas.drawLine(x - len, y, x + len, y, trailPaint)
        // 垂直芒
        canvas.drawLine(x, y - len, x, y + len, trailPaint)
        // 对角芒（更短）
        val diagLen = len * 0.5f
        canvas.drawLine(x - diagLen, y - diagLen, x + diagLen, y + diagLen, trailPaint)
        canvas.drawLine(x + diagLen, y - diagLen, x - diagLen, y + diagLen, trailPaint)

        // 中心亮点
        starPaint.color = Color.argb((brightness * 255).toInt(), r, g, b)
        canvas.drawCircle(x, y, radius, starPaint)
    }

    /** 流星：拖尾逐渐变细变淡 */
    private fun drawShootingStar(canvas: Canvas, ss: ShootingStar, w: Float, h: Float, diag: Float) {
        val progress = (ss.life / ss.maxLife).coerceIn(0f, 1f)
        // 缓入缓出
        val easedProgress = sin(progress * PI.toFloat())

        val distance = ss.speed * diag * ss.life
        val tailLength = ss.length * diag

        val dx = cos(ss.angle) * distance
        val dy = sin(ss.angle) * distance

        val headX = ss.startX + dx
        val headY = ss.startY + dy

        val tailX = headX - cos(ss.angle) * tailLength
        val tailY = headY - sin(ss.angle) * tailLength

        // 超出屏幕则不画
        if (headX < -50 || headX > w + 50 || headY < -50 || headY > h + 50) return

        // 头部白色，尾部渐淡
        val headAlpha = (easedProgress * 230).toInt().coerceIn(0, 230)
        val headColor = Color.argb(headAlpha, 255, 255, 255)
        val tailColor = Color.argb(0, 200, 210, 255)

        // 主线（粗→细）
        trailPaint.shader = LinearGradient(headX, headY, tailX, tailY, headColor, tailColor, Shader.TileMode.CLAMP)
        trailPaint.strokeWidth = 2.5f * easedProgress + 0.5f
        trailPaint.alpha = 255
        canvas.drawLine(headX, headY, tailX, tailY, trailPaint)
        trailPaint.shader = null

        // 头部光点
        val dotAlpha = (easedProgress * 255).toInt()
        starPaint.color = Color.argb(dotAlpha, 255, 255, 255)
        canvas.drawCircle(headX, headY, 2f * easedProgress + 0.5f, starPaint)

        // 外层柔光（radius 必须 > 0，否则 RadialGradient 抛异常）
        val glowRadius = max(12f * easedProgress, 0.01f)
        glowPaint.shader = RadialGradient(
            headX, headY, glowRadius,
            Color.argb((dotAlpha * 0.35f).toInt(), 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(headX, headY, glowRadius, glowPaint)
        glowPaint.shader = null
    }

    // ═══════════════════════════════════════════
    //  数据类
    // ═══════════════════════════════════════════

    /** @param x,y 屏幕百分比坐标 (0-1) */
    private data class Star(
        val x: Float,
        val y: Float,
        val baseBrightness: Float,
        val radius: Float,
        val twinkleSpeed: Float,
        val twinkleOffset: Float,
        val color: Int
    )

    private data class Nebula(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val color: Int,
        val edgeColor: Int
    )

    private data class ShootingStar(
        val startX: Float,
        val startY: Float,
        val angle: Float,
        val speed: Float,
        val length: Float,
        var life: Float,
        val maxLife: Float
    )

    companion object {
        private const val TWO_PI = (Math.PI * 2).toFloat()
    }
}
