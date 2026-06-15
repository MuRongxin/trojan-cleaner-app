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

class FireworkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fireworks = mutableListOf<Firework>()
    private val sparks = mutableListOf<Spark>()
    private var animator: ValueAnimator? = null

    private val sparkPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val trailPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 32f
        color = Color.parseColor("#FFD700")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val hintPaint = Paint().apply {
        isAntiAlias = true
        textSize = 28f
        color = Color.parseColor("#99FFFFFF")
        textAlign = Paint.Align.CENTER
    }

    private var fireworkCount = 0
    private var lastTapTime = 0L

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    fun setOnBackListener(listener: (() -> Unit)?) {
        // 由系统返回键处理，不再显示返回文字
    }

    fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                updateFireworks()
                updateSparks()
                invalidate()
            }
            start()
        }
    }

    private fun updateFireworks() {
        val iterator = fireworks.iterator()
        while (iterator.hasNext()) {
            val fw = iterator.next()
            fw.y += fw.vy
            fw.vy += 0.05f
            fw.alpha -= 2
            fw.trail.add(PointF(fw.x, fw.y))
            if (fw.trail.size > 15) fw.trail.removeAt(0)

            if (fw.vy >= 0 || fw.alpha <= 100) {
                explode(fw)
                iterator.remove()
            }
        }
    }

    private fun updateSparks() {
        val iterator = sparks.iterator()
        while (iterator.hasNext()) {
            val spark = iterator.next()
            spark.x += spark.vx
            spark.y += spark.vy
            spark.vy += 0.08f
            spark.alpha -= 3
            spark.size *= 0.97f
            spark.trail.add(PointF(spark.x, spark.y))
            if (spark.trail.size > 6) spark.trail.removeAt(0)

            if (spark.alpha <= 0 || spark.size < 0.5f) {
                iterator.remove()
            }
        }
    }

    private fun explode(fw: Firework) {
        val sparkCount = Random.nextInt(40, 80)
        val colors = generateExplosionColors(fw.color)

        for (i in 0 until sparkCount) {
            val angle = Random.nextFloat() * 2 * PI
            val speed = Random.nextFloat() * 6 + 2
            sparks.add(Spark(
                x = fw.x,
                y = fw.y,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                size = Random.nextFloat() * 4 + 2,
                color = colors[Random.nextInt(colors.size)],
                alpha = 255,
                trail = mutableListOf()
            ))
        }

        // 中心闪光
        for (i in 0 until 15) {
            val angle = Random.nextFloat() * 2 * PI
            val speed = Random.nextFloat() * 2 + 0.5f
            sparks.add(Spark(
                x = fw.x,
                y = fw.y,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                size = Random.nextFloat() * 6 + 3,
                color = Color.WHITE,
                alpha = 200,
                trail = mutableListOf()
            ))
        }
    }

    private fun generateExplosionColors(base: Int): List<Int> {
        val r = Color.red(base)
        val g = Color.green(base)
        val b = Color.blue(base)
        return listOf(
            base,
            Color.rgb((r + 50).coerceIn(0, 255), g, b),
            Color.rgb(r, (g + 50).coerceIn(0, 255), b),
            Color.rgb(r, g, (b + 50).coerceIn(0, 255)),
            Color.WHITE,
            Color.rgb(255, 255, 200)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()

            if (now - lastTapTime < 80) return true
            lastTapTime = now
            launchFirework(event.x, event.y)
        }
        return true
    }

    private fun launchFirework(targetX: Float, targetY: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        // 发射点遍布底部 5/6 宽度
        val startX = w / 12f + Random.nextFloat() * (w * 5f / 6f)
        val startY = h

        // 最高可到达 4/5 屏幕高度（y = h * 0.2）
        val maxHeightY = h * 0.2f
        val cappedTargetY = min(targetY, maxHeightY)

        // 根据目标高度计算所需初速度：最高点 vy = 0 时爆炸
        // 位移公式：Δy = -10 * vy^2，vy 取负值
        val neededVy = -sqrt((startY - cappedTargetY) / 10f)
        val vy = neededVy.coerceIn(-22f, -8f)

        fireworks.add(Firework(
            x = startX,
            y = startY,
            targetX = targetX,
            targetY = cappedTargetY,
            vx = (targetX - startX) * 0.02f,
            vy = vy,
            color = getRandomFireworkColor(),
            alpha = 255,
            trail = mutableListOf()
        ))
        fireworkCount++
    }

    private fun getRandomFireworkColor(): Int {
        val colors = listOf(
            Color.parseColor("#FF0000"),
            Color.parseColor("#FF69B4"),
            Color.parseColor("#FFD700"),
            Color.parseColor("#00FF00"),
            Color.parseColor("#00BFFF"),
            Color.parseColor("#FF8C00"),
            Color.parseColor("#9370DB"),
            Color.parseColor("#FF1493"),
            Color.parseColor("#00CED1"),
            Color.parseColor("#FFA500"),
        )
        return colors[Random.nextInt(colors.size)]
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val time = System.currentTimeMillis() * 0.001f

        // 透明背景 —— 星空由底层的 StarryBackgroundView 绘制

        // 烟花轨迹
        for (fw in fireworks) {
            if (fw.trail.size >= 2) {
                trailPaint.color = fw.color
                trailPaint.strokeWidth = 3f
                for (i in 1 until fw.trail.size) {
                    val p1 = fw.trail[i - 1]
                    val p2 = fw.trail[i]
                    trailPaint.alpha = (i * 255 / fw.trail.size)
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, trailPaint)
                }
            }

            sparkPaint.color = Color.WHITE
            sparkPaint.alpha = fw.alpha
            canvas.drawCircle(fw.x, fw.y, 5f, sparkPaint)
            sparkPaint.color = fw.color
            sparkPaint.alpha = (fw.alpha * 0.7).toInt()
            canvas.drawCircle(fw.x, fw.y, 8f, sparkPaint)
        }

        // 火花
        for (spark in sparks) {
            if (spark.trail.size >= 2) {
                trailPaint.color = spark.color
                trailPaint.strokeWidth = spark.size * 0.5f
                for (i in 1 until spark.trail.size) {
                    val p1 = spark.trail[i - 1]
                    val p2 = spark.trail[i]
                    trailPaint.alpha = (spark.alpha * i / spark.trail.size * 0.5).toInt()
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, trailPaint)
                }
            }

            sparkPaint.color = spark.color
            sparkPaint.alpha = spark.alpha.coerceIn(0, 255)
            canvas.drawCircle(spark.x, spark.y, spark.size, sparkPaint)

            if (spark.size > 3) {
                sparkPaint.color = Color.WHITE
                sparkPaint.alpha = (spark.alpha * 0.5).toInt()
                canvas.drawCircle(spark.x, spark.y, spark.size * 0.3f, sparkPaint)
            }
        }

        // 顶部分数
        textPaint.textSize = 32f
        canvas.drawText("🎆 已放 $fireworkCount 个烟花", w / 2, 120f, textPaint)

        // 提示
        if (fireworkCount == 0) {
            hintPaint.textSize = 28f
            hintPaint.alpha = (sin(time * 3) * 80 + 175).toInt()
            canvas.drawText("点击屏幕放烟花 🎆", w / 2, h / 2, hintPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private data class Firework(
        var x: Float,
        var y: Float,
        val targetX: Float,
        val targetY: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        var alpha: Int,
        val trail: MutableList<PointF>
    )

    private data class Spark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        val color: Int,
        var alpha: Int,
        val trail: MutableList<PointF>
    )

}
