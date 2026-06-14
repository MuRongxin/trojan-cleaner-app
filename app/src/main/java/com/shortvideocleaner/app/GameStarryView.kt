package com.shortvideocleaner.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/**
 * 游戏页专属星空 —— 更绚丽、更活泼
 * 彩色星云、流光粒子、拖尾流星、极光飘带
 */
class GameStarryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var phase = 0f
    private var timeSec = 0f
    private var animator: ValueAnimator? = null

    // 三层星星
    private val dustStars = mutableListOf<GameStar>()      // 星尘背景
    private val glowStars = mutableListOf<GameStar>()       // 彩色光点
    private val jewelStars = mutableListOf<GameStar>()      // 宝石大星

    // 星云
    private val nebulae = mutableListOf<GameNebula>()

    // 流星
    private val shootingStars = mutableListOf<GameShootingStar>()

    // 飘浮光粒
    private val particles = mutableListOf<FloatParticle>()

    // 极光飘带相位
    private var auroraPhase = 0f

    // ── 画笔 ──
    private val bgPaint = Paint().apply { color = 0xFF050515.toInt() }

    private val nebulaPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL
    }

    private val starPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL; strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL
    }

    private val trailPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    private val auroraPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.FILL
    }

    // ── 色板（游戏风格，更鲜艳）──
    private val jewelColors = listOf(
        0xFFFF6B9D.toInt(), // 热粉
        0xFF00E5FF.toInt(), // 电青
        0xFFFFD740.toInt(), // 琥珀
        0xFFB388FF.toInt(), // 薰衣紫
        0xFFFF6E40.toInt(), // 珊瑚橙
        0xFF69F0AE.toInt(), // 薄荷绿
        0xFFFF4081.toInt(), // 玫红
        0xFF40C4FF.toInt(), // 天蓝
    )

    private val softColors = listOf(
        0xFFFFCDD2.toInt(), 0xFFBBDEFB.toInt(), 0xFFC8E6C9.toInt(),
        0xFFFFF9C4.toInt(), 0xFFE1BEE7.toInt(), 0xFFB2EBF2.toInt(),
        0xFFFFE0B2.toInt(), 0xFFD7CCC8.toInt(),
    )

    init {
        generateNebulae()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            generateStars(w, h)
        }
    }

    // ═══ 数据生成 ═══

    private fun generateNebulae() {
        nebulae.clear()
        nebulae.add(GameNebula(0.25f, 0.35f, 0.13f, 0xFF4A1A5E.toInt(), 0xFF1A0A2E.toInt()))
        nebulae.add(GameNebula(0.65f, 0.45f, 0.16f, 0xFF0E2A4A.toInt(), 0xFF051020.toInt()))
        nebulae.add(GameNebula(0.45f, 0.65f, 0.14f, 0xFF3A1040.toInt(), 0xFF140820.toInt()))
        nebulae.add(GameNebula(0.80f, 0.28f, 0.11f, 0xFF1A2A3E.toInt(), 0xFF081020.toInt()))
        nebulae.add(GameNebula(0.15f, 0.60f, 0.12f, 0xFF2A1A3E.toInt(), 0xFF0E0820.toInt()))
        nebulae.add(GameNebula(0.55f, 0.20f, 0.10f, 0xFF1A2840.toInt(), 0xFF081028.toInt()))
    }

    private fun generateStars(w: Int, h: Int) {
        dustStars.clear(); glowStars.clear(); jewelStars.clear()
        val count = (min(w, h) * 0.45f).toInt().coerceIn(120, 400)

        for (i in 0 until count) {
            val x = Random.nextFloat()
            val y = Random.nextFloat()
            val roll = Random.nextFloat()
            when {
                roll < 0.05f -> jewelStars.add(GameStar(
                    x, y, Random.nextFloat() * 0.35f + 0.65f,
                    Random.nextFloat() * 2f + 2.2f,
                    Random.nextFloat() * 0.5f + 0.5f,
                    Random.nextFloat() * TWO_PI,
                    jewelColors.random()
                ))
                roll < 0.30f -> glowStars.add(GameStar(
                    x, y, Random.nextFloat() * 0.3f + 0.4f,
                    Random.nextFloat() * 1.2f + 0.5f,
                    Random.nextFloat() * 0.6f + 0.3f,
                    Random.nextFloat() * TWO_PI,
                    softColors.random()
                ))
                else -> dustStars.add(GameStar(
                    x, y, Random.nextFloat() * 0.15f + 0.08f,
                    Random.nextFloat() * 0.7f + 0.2f,
                    Random.nextFloat() * 0.25f + 0.05f,
                    Random.nextFloat() * TWO_PI,
                    softColors.random()
                ))
            }
        }
    }

    // ═══ 生命周期 ═══

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    fun resumeAnimation() { startAnimation() }
    fun pauseAnimation() { stopAnimation() }

    private fun startAnimation() {
        stopAnimation()
        phase = 0f; timeSec = 0f
        particles.clear(); shootingStars.clear()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 50L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                phase += 0.011f; if (phase > 1000f) phase -= 1000f
                timeSec += 0.05f
                auroraPhase += 0.003f
                updateShootingStars()
                updateParticles()
                spawnAmbientParticles()
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel(); animator = null
    }

    // ═══ 流星 ═══

    private fun updateShootingStars() {
        // 清理已结束的
        shootingStars.removeAll { it.life > it.maxLife }

        // 随机生成新流星（概率更高，游戏页更热闹）
        if (shootingStars.size < 2 && Random.nextFloat() < 0.015f) {
            spawnShootingStar()
        }
        for (ss in shootingStars) ss.life += 0.016f
    }

    private fun spawnShootingStar() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0) return
        shootingStars.add(GameShootingStar(
            startX = Random.nextFloat() * w * 0.5f,
            startY = Random.nextFloat() * h * 0.2f,
            angle = Random.nextFloat() * 0.5f + 0.2f,
            speed = Random.nextFloat() * 0.5f + 0.6f,
            length = Random.nextFloat() * 0.05f + 0.03f,
            life = 0f, maxLife = Random.nextFloat() * 0.5f + 0.6f,
            color = jewelColors.random()
        ))
    }

    // ═══ 飘浮光粒 ═══

    private fun spawnAmbientParticles() {
        if (particles.size < 40 && Random.nextFloat() < 0.3f) {
            val w = width.toFloat(); val h = height.toFloat()
            particles.add(FloatParticle(
                x = Random.nextFloat() * w,
                y = h + 20f,
                vx = (Random.nextFloat() - 0.5f) * 0.4f,
                vy = -Random.nextFloat() * 1.2f - 0.3f,
                size = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextInt(80) + 40,
                color = softColors.random(),
                life = 0f, maxLife = Random.nextFloat() * 8f + 4f
            ))
        }
    }

    private fun updateParticles() {
        particles.removeAll { it.life > it.maxLife }
        for (p in particles) {
            p.x += p.vx; p.y += p.vy
            p.life += 0.05f
            val fadeProgress = p.life / p.maxLife
            p.alpha = ((1f - fadeProgress) * 120).toInt().coerceIn(0, 120) + 20
        }
    }

    // ═══ 绘制 ═══

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val diag = hypot(w, h)

        // 背景
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 星云
        drawNebulae(canvas, w, h)

        // 极光飘带
        drawAurora(canvas, w, h)

        // 星尘
        drawStarLayer(canvas, dustStars, w, h, withGlow = false)

        // 光点
        drawStarLayer(canvas, glowStars, w, h, withGlow = false)

        // 宝石大星
        drawStarLayer(canvas, jewelStars, w, h, withGlow = true)

        // 流星
        for (ss in shootingStars) drawShootingStar(canvas, ss, w, h, diag)

        // 飘浮粒子
        for (p in particles) {
            starPaint.color = p.color
            starPaint.alpha = p.alpha.coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.size, starPaint)
        }
    }

    private fun drawNebulae(canvas: Canvas, w: Float, h: Float) {
        val scale = max(w, h)
        for (n in nebulae) {
            val cx = n.cx * w; val cy = n.cy * h
            val r = max(n.radius * scale, 0.01f)
            val breathe = sin(phase * 0.4f + n.cx * 8f) * 0.25f + 0.7f
            val alpha = (breathe * 70f).toInt().coerceIn(15, 85)
            val cc = Color.argb(alpha, Color.red(n.color), Color.green(n.color), Color.blue(n.color))
            val ec = Color.argb(0, Color.red(n.edge), Color.green(n.edge), Color.blue(n.edge))
            nebulaPaint.shader = RadialGradient(cx, cy, r, cc, ec, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, nebulaPaint)
        }
        nebulaPaint.shader = null
    }

    /** 极光：底部飘动的半透明彩色光带 */
    private fun drawAurora(canvas: Canvas, w: Float, h: Float) {
        val baseY = h * 0.85f
        val path = Path()
        path.moveTo(0f, h)
        val steps = 8
        val stepW = w / steps
        for (i in 0..steps) {
            val x = i * stepW
            val wave = sin(auroraPhase + i * 0.8f) * h * 0.06f +
                       sin(auroraPhase * 1.7f + i * 1.3f) * h * 0.04f
            path.lineTo(x, baseY + wave)
        }
        path.lineTo(w, h)
        path.close()

        // 多层叠加
        val colors = listOf(0x3069F0AE.toInt(), 0x3040C4FF.toInt(), 0x30B388FF.toInt())
        for ((idx, color) in colors.withIndex()) {
            auroraPaint.color = color
            auroraPaint.alpha = 255
            val offsetY = idx * h * 0.015f
            canvas.save()
            canvas.translate(0f, -offsetY)
            canvas.drawPath(path, auroraPaint)
            canvas.restore()
        }
    }

    private fun drawStarLayer(canvas: Canvas, stars: List<GameStar>, w: Float, h: Float, withGlow: Boolean) {
        val cx = w / 2f; val cy = h / 2f
        val rot = phase * 0.06f
        val cosA = cos(rot); val sinA = sin(rot)

        for (s in stars) {
            val sx = s.x * w; val sy = s.y * h
            val rx = cx + (sx - cx) * cosA - (sy - cy) * sinA
            val ry = cy + (sx - cx) * sinA + (sy - cy) * cosA

            val twinkle = (sin(phase * s.twinkleSpeed * 40f + s.offset) * 0.5f + 0.5f)
            val brightness = (s.baseBrightness + twinkle * 0.4f).coerceIn(0.1f, 1f)

            val r = Color.red(s.color); val g = Color.green(s.color); val b = Color.blue(s.color)
            val alpha = (brightness * 255).toInt()
            starPaint.color = Color.argb(alpha, r, g, b)

            if (withGlow && s.radius > 1.5f && brightness > 0.5f) {
                val ga = (brightness * 0.3f * 255).toInt()
                val gr = max(s.radius * 4f, 0.01f)
                glowPaint.shader = RadialGradient(rx, ry, gr,
                    Color.argb(ga, r, g, b), Color.argb(0, r, g, b), Shader.TileMode.CLAMP)
                canvas.drawCircle(rx, ry, gr, glowPaint)
                glowPaint.shader = null
            }

            if (s.radius > 1.8f && brightness > 0.6f) {
                drawCross(canvas, rx, ry, s.radius, brightness, r, g, b)
            } else {
                canvas.drawCircle(rx, ry, s.radius, starPaint)
            }
        }
    }

    private fun drawCross(canvas: Canvas, x: Float, y: Float, rad: Float, bright: Float, r: Int, g: Int, b: Int) {
        val len = rad * 3f * bright
        val alpha = (bright * 0.6f * 255).toInt()
        trailPaint.color = Color.argb(alpha, r, g, b)
        trailPaint.strokeWidth = rad * 0.45f
        canvas.drawLine(x - len, y, x + len, y, trailPaint)
        canvas.drawLine(x, y - len, x, y + len, trailPaint)
        val dl = len * 0.45f
        canvas.drawLine(x - dl, y - dl, x + dl, y + dl, trailPaint)
        canvas.drawLine(x + dl, y - dl, x - dl, y + dl, trailPaint)
        starPaint.color = Color.argb((bright * 255).toInt(), r, g, b)
        canvas.drawCircle(x, y, rad, starPaint)
    }

    private fun drawShootingStar(canvas: Canvas, ss: GameShootingStar, w: Float, h: Float, diag: Float) {
        val progress = (ss.life / ss.maxLife).coerceIn(0f, 1f)
        val ep = sin(progress * PI.toFloat())
        val dist = ss.speed * diag * ss.life
        val tailLen = ss.length * diag
        val dx = cos(ss.angle) * dist; val dy = sin(ss.angle) * dist
        val hx = ss.startX + dx; val hy = ss.startY + dy
        val tx = hx - cos(ss.angle) * tailLen; val ty = hy - sin(ss.angle) * tailLen
        if (hx < -60 || hx > w + 60 || hy < -60 || hy > h + 60) return

        val headAlpha = (ep * 240).toInt().coerceIn(0, 240)
        val r = Color.red(ss.color); val g = Color.green(ss.color); val b = Color.blue(ss.color)
        trailPaint.shader = LinearGradient(hx, hy, tx, ty,
            Color.argb(headAlpha, r, g, b), Color.argb(0, r, g, b), Shader.TileMode.CLAMP)
        trailPaint.strokeWidth = 2f * ep + 0.5f
        canvas.drawLine(hx, hy, tx, ty, trailPaint)
        trailPaint.shader = null

        starPaint.color = Color.argb(headAlpha, 255, 255, 255)
        canvas.drawCircle(hx, hy, 1.8f * ep + 0.4f, starPaint)

        val glowR = max(10f * ep, 0.01f)
        glowPaint.shader = RadialGradient(hx, hy, glowR,
            Color.argb((headAlpha * 0.3f).toInt(), r, g, b),
            Color.argb(0, r, g, b), Shader.TileMode.CLAMP)
        canvas.drawCircle(hx, hy, glowR, glowPaint)
        glowPaint.shader = null
    }

    // ═══ 数据类 ═══

    private data class GameStar(val x: Float, val y: Float, val baseBrightness: Float,
                                 val radius: Float, val twinkleSpeed: Float,
                                 val offset: Float, val color: Int)
    private data class GameNebula(val cx: Float, val cy: Float, val radius: Float,
                                   val color: Int, val edge: Int)
    private data class GameShootingStar(val startX: Float, val startY: Float, val angle: Float,
                                         val speed: Float, val length: Float,
                                         var life: Float, val maxLife: Float, val color: Int)
    private data class FloatParticle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                                      val size: Float, var alpha: Int, val color: Int,
                                      var life: Float, val maxLife: Float)

    companion object {
        private const val TWO_PI = (Math.PI * 2).toFloat()
    }
}
