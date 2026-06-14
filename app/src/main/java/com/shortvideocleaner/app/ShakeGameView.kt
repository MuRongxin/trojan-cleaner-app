package com.shortvideocleaner.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.*
import kotlin.random.Random

class ShakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    // 祝福语
    private val blessings = listOf(
        "愿你每天都被温柔以待 💕",
        "愿所有美好都如期而至 🌸",
        "愿你的笑容永远灿烂如花 ✨",
        "愿你被这个世界温柔相拥 🤗",
        "愿你的每一天都充满阳光 ☀️",
        "愿你心中有爱，眼里有光 💫",
        "愿你所求皆如愿，所行皆坦途 🌈",
        "愿你永远被爱包围 💝",
        "愿你的生活如诗如画 🎨",
        "愿你开心快乐每一天 🎉",
        "愿你所有的梦想都能实现 🌟",
        "愿你被温柔以待，不负深情 💖",
        "愿你眼中有星辰大海 🌊",
        "愿你前路繁花似锦 🌺",
        "愿你所到之处皆为热土 🔥",
        "愿你平安喜乐，得偿所愿 🎊",
    )

    // 状态
    private var currentBlessing = blessings[0]
    private var blessingAlpha = 0f
    private var shakeCount = 0
    private var isShaking = false
    private var lastShakeTime = 0L

    // 动画
    private var animator: ValueAnimator? = null
    private var stars = mutableListOf<ShakeStar>()
    private var sparkles = mutableListOf<Sparkle>()

    // 画笔
    private val starPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 48f
        color = Color.parseColor("#FFD700")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val hintPaint = Paint().apply {
        isAntiAlias = true
        textSize = 36f
        color = Color.parseColor("#99FFFFFF")
        textAlign = Paint.Align.CENTER
    }

    // 手机摇晃偏移
    private var offsetX = 0f
    private var offsetY = 0f
    private var shakeMagnitude = 0f

    init {
        setWillNotDraw(false)

        // 提示文字
        val hint = TextView(context).apply {
            text = "📱 摇摇手机接收祝福"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 16f
            setPadding(32, 32, 32, 32)
            gravity = android.view.Gravity.CENTER
        }
        addView(hint)

        initSensor()
        initStars()
        startAnimation()
    }

    private fun initSensor() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun initStars() {
        stars.clear()
        for (i in 0 until 100) {
            stars.add(ShakeStar(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3 + 1,
                twinkleSpeed = Random.nextFloat() * 2 + 1,
                twinkleOffset = Random.nextFloat() * 2 * PI.toFloat()
            ))
        }
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                updateShake()
                updateSparkles()
                invalidate()
            }
            start()
        }
    }

    private fun updateShake() {
        // 衰减摇晃
        shakeMagnitude *= 0.9f
        offsetX *= 0.9f
        offsetY *= 0.9f

        // 祝福文字渐入
        if (blessingAlpha > 0) {
            blessingAlpha -= 1
        }
    }

    private fun updateSparkles() {
        val iterator = sparkles.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.x += s.vx
            s.y += s.vy
            s.vy += 0.05f
            s.alpha -= 3
            s.size *= 0.98f
            if (s.alpha <= 0) {
                iterator.remove()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (magnitude > 3.0f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 500) {  // 防抖
                lastShakeTime = now
                onShake()
            }
        }

        // 根据加速度更新偏移
        if (magnitude > 1.5f) {
            offsetX = x * 5
            offsetY = y * 5
            shakeMagnitude = magnitude
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onShake() {
        shakeCount++

        // 随机选择祝福
        currentBlessing = blessings[Random.nextInt(blessings.size)]
        blessingAlpha = 255f

        // 生成星星粒子
        val cx = width / 2f
        val cy = height / 2f
        for (i in 0 until 30) {
            val angle = Random.nextFloat() * 2 * PI
            val speed = Random.nextFloat() * 8 + 2
            sparkles.add(Sparkle(
                x = cx + Random.nextFloat() * 200 - 100,
                y = cy + Random.nextFloat() * 200 - 100,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat() - 5,
                size = Random.nextFloat() * 6 + 3,
                color = getRandomStarColor(),
                alpha = 255
            ))
        }

        // 更新统计
        updateStats()
    }

    private fun getRandomStarColor(): Int {
        val colors = listOf(
            Color.parseColor("#FFD700"),
            Color.parseColor("#FF69B4"),
            Color.parseColor("#87CEEB"),
            Color.parseColor("#98FB98"),
            Color.parseColor("#DDA0DD"),
            Color.parseColor("#FFA500"),
        )
        return colors[Random.nextInt(colors.size)]
    }

    private fun updateStats() {
        val statsText = getChildAt(1) as? TextView ?: return
        statsText.text = "📱 已收到 $shakeCount 份祝福"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 透明背景 —— 星空由底层的 StarryBackgroundView 绘制

        // 绘制粒子
        for (sparkle in sparkles) {
            starPaint.color = sparkle.color
            starPaint.alpha = sparkle.alpha.coerceIn(0, 255)
            canvas.drawCircle(sparkle.x, sparkle.y, sparkle.size, starPaint)
        }

        // 绘制中央手机图标（带摇晃偏移）
        drawPhoneIcon(canvas, w / 2 + offsetX, h / 2 - 50 + offsetY)

        // 绘制祝福文字
        if (blessingAlpha > 0) {
            textPaint.alpha = blessingAlpha.toInt().coerceIn(0, 255)
            textPaint.textSize = 42f
            drawMultilineText(canvas, currentBlessing, w / 2, h / 2 + 150, textPaint)
        }

        // 绘制提示
        if (shakeCount == 0) {
            hintPaint.alpha = (sin(System.currentTimeMillis() * 0.003) * 50 + 150).toInt()
            canvas.drawText("摇摇手机，接收祝福 ✨", w / 2, h / 2 + 100, hintPaint)
        }

        // 绘制统计
        if (shakeCount > 0) {
            hintPaint.alpha = 180
            hintPaint.textSize = 28f
            canvas.drawText("已收到 $shakeCount 份祝福", w / 2, h - 80, hintPaint)
        }
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        val time = System.currentTimeMillis() * 0.001f
        for (star in stars) {
            val twinkle = sin(time * star.twinkleSpeed + star.twinkleOffset)
            val alpha = (twinkle * 50 + 150).toInt().coerceIn(100, 200)
            starPaint.color = Color.WHITE
            starPaint.alpha = alpha
            canvas.drawCircle(star.x * w, star.y * h, star.size, starPaint)
        }
    }

    private fun drawPhoneIcon(canvas: Canvas, cx: Float, cy: Float) {
        val size = 80f

        // 手机外壳
        starPaint.color = Color.parseColor("#333333")
        starPaint.alpha = 200
        val rect = RectF(cx - size / 2, cy - size, cx + size / 2, cy + size)
        canvas.drawRoundRect(rect, 12f, 12f, starPaint)

        // 屏幕
        starPaint.color = Color.parseColor("#1A1A2E")
        starPaint.alpha = 255
        val screen = RectF(cx - size / 2 + 8, cy - size + 20, cx + size / 2 - 8, cy + size - 20)
        canvas.drawRoundRect(screen, 4f, 4f, starPaint)

        // 屏幕上的爱心
        starPaint.color = Color.parseColor("#FF69B4")
        starPaint.alpha = 255
        drawSmallHeart(canvas, cx, cy - 10, 20f)

        // 摇晃指示
        if (shakeMagnitude > 2) {
            starPaint.color = Color.parseColor("#FFD700")
            starPaint.alpha = (shakeMagnitude * 20).toInt().coerceIn(0, 255)

            // 左右波浪线
            val waveY = cy + size + 20
            val path = Path()
            path.moveTo(cx - 40, waveY)
            for (i in 0 until 8) {
                val x = cx - 40 + i * 10
                val y = waveY + sin(i * 1.5f + System.currentTimeMillis() * 0.01f) * 8
                path.lineTo(x, y)
            }
            starPaint.style = Paint.Style.STROKE
            starPaint.strokeWidth = 3f
            canvas.drawPath(path, starPaint)
            starPaint.style = Paint.Style.FILL
        }
    }

    private fun drawSmallHeart(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val path = Path()
        val s = size / 2

        path.moveTo(cx, cy + s * 0.4f)
        path.cubicTo(cx - s, cy - s * 0.2f, cx - s * 0.8f, cy - s, cx, cy - s * 0.4f)
        path.cubicTo(cx + s * 0.8f, cy - s, cx + s, cy - s * 0.2f, cx, cy + s * 0.4f)
        path.close()

        canvas.drawPath(path, starPaint)
    }

    private fun drawMultilineText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val lines = text.split("\n")
        val lineHeight = paint.textSize * 1.5f
        var currentY = y - (lines.size - 1) * lineHeight / 2

        for (line in lines) {
            canvas.drawText(line, x, currentY, paint)
            currentY += lineHeight
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager?.unregisterListener(this)
        animator?.cancel()
    }

    // 数据类
    private data class ShakeStar(
        val x: Float,
        val y: Float,
        val size: Float,
        val twinkleSpeed: Float,
        val twinkleOffset: Float
    )

    private data class Sparkle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        val color: Int,
        var alpha: Int
    )
}
