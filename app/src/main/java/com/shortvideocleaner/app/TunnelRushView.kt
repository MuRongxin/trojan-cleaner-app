package com.shortvideocleaner.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class TunnelRushView(context: Context) : View(context) {

    // ── 数据类 ──
    data class Obstacle(
        var lane: Int,
        var z: Float,
        var type: ObstacleType,
        var widthScale: Float = 1f,
        var passed: Boolean = false,
        var moveDir: Float = 0f
    )

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float, var maxLife: Float,
        var color: Int, var size: Float
    )

    data class SpeedLine(
        var x: Float, var y: Float,
        var length: Float, var speed: Float,
        var alpha: Int
    )

    enum class ObstacleType { BLOCK, PILLAR, MOVING }

    // ── 游戏状态 ──
    enum class GameState { READY, PLAYING, GAME_OVER }

    private var state = GameState.READY
    private var score = 0
    private var highScore = 0

    // ── 透视参数 ──
    private var focalLength = 0f
    private var vanishingY = 0f
    private var tunnelWidth = 0f

    // ── 车道 ──
    private val laneCount = 3
    private var playerLane = 1
    private var targetLane = 1
    private var playerX = 0f

    // ── 速度 ──
    private var baseSpeed = 3f
    private val maxSpeed = 12f
    private var currentSpeed = baseSpeed

    // ── 障碍物 ──
    private val obstacles = mutableListOf<Obstacle>()
    private var spawnTimer = 0f
    private val spawnInterval = 1.5f
    private val maxZ = 2000f
    private val collisionDepth = 30f

    // ── 粒子 ──
    private val particles = mutableListOf<Particle>()
    private val speedLines = mutableListOf<SpeedLine>()
    private val maxParticles = 150
    private val maxSpeedLines = 30

    // ── 隧道流动 ──
    private var tunnelOffset = 0f

    // ── 触摸控制 ──
    private var isTouchingLeft = false
    private var isTouchingRight = false
    private var touchX = 0f
    private var isDragging = false

    // ── 动画 ──
    private var animator: ValueAnimator? = null
    private var lastTime = 0L

    // ── 闪光效果 ──
    private var flashAlpha = 0f
    private var flashColor = 0

    // ── 震动效果 ──
    private var shakeX = 0f
    private var shakeY = 0f
    private var shakeTime = 0f

    // ── 回调 ──
    private var onBackListener: (() -> Unit)? = null

    fun setOnBackListener(listener: () -> Unit) {
        onBackListener = listener
    }

    // ── 画笔 ──
    private val tunnelLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF00E5FF.toInt()
    }

    private val tunnelGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = 0x8000E5FF.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
    }

    private val groundLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x60FFFFFF.toInt()
    }

    private val vanishingGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val shipPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0xFFFF4081.toInt()
    }

    private val shipCorePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val obstaclePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val obstacleFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val particlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val speedLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val scorePaint = Paint().apply {
        isAntiAlias = true
        textSize = 48f
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }

    private val hintPaint = Paint().apply {
        isAntiAlias = true
        textSize = 36f
        color = 0xCCFFFFFF.toInt()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val flashPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val warningPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // ── 霓虹色板 ──
    private val neonColors = listOf(
        0xFF00E5FF.toInt(),  // 电青
        0xFFFF4081.toInt(),  // 玫红
        0xFF7C4DFF.toInt(),  // 深紫
        0xFF69F0AE.toInt(),  // 薄荷绿
        0xFFFFD740.toInt(),  // 琥珀
        0xFF40C4FF.toInt()   // 天蓝
    )

    private var tunnelColorIndex = 0
    private val tunnelColors = listOf(
        Pair(0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt()),  // 青+紫
        Pair(0xFFFF4081.toInt(), 0xFFFFD740.toInt()),   // 粉+金
        Pair(0xFF69F0AE.toInt(), 0xFF40C4FF.toInt()),   // 绿+蓝
        Pair(0xFFFF6E40.toInt(), 0xFFB388FF.toInt())    // 橙+薰衣紫
    )

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            focalLength = w * 0.6f
            vanishingY = h * 0.25f
            tunnelWidth = w * 0.8f
            playerX = w / 2f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopGame()
    }

    // ═══ 游戏生命周期 ═══

    fun startGame() {
        state = GameState.PLAYING
        score = 0
        currentSpeed = baseSpeed
        playerLane = 1
        targetLane = 1
        playerX = width / 2f
        obstacles.clear()
        particles.clear()
        speedLines.clear()
        tunnelOffset = 0f
        flashAlpha = 0f
        shakeTime = 0f
        spawnTimer = 0f
        tunnelColorIndex = 0
        lastTime = System.currentTimeMillis()

        updateTunnelColors()
        startAnimator()
    }

    private fun startAnimator() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                update()
                invalidate()
            }
            start()
        }
    }

    fun stopGame() {
        animator?.cancel()
        animator = null
    }

    fun pause() {
        stopGame()
    }

    fun resume() {
        if (state == GameState.PLAYING) {
            lastTime = System.currentTimeMillis()
            startAnimator()
        }
    }

    // ═══ 更新逻辑 ═══

    private fun update() {
        val now = System.currentTimeMillis()
        val delta = ((now - lastTime) / 1000f).coerceIn(0f, 0.05f)
        lastTime = now

        if (state != GameState.PLAYING) return

        // 加速
        currentSpeed = (currentSpeed + 0.0005f * delta * 60).coerceAtMost(maxSpeed)

        // 隧道流动
        tunnelOffset += currentSpeed * delta * 100

        // 更新玩家位置
        updatePlayerPosition(delta)

        // 生成障碍物
        spawnTimer += delta
        val adjustedInterval = spawnInterval / (1f + (currentSpeed - baseSpeed) * 0.1f)
        if (spawnTimer >= adjustedInterval) {
            spawnTimer = 0f
            spawnObstacle()
        }

        // 更新障碍物
        updateObstacles(delta)

        // 更新粒子
        updateParticles(delta)
        updateSpeedLines(delta)

        // 更新闪光
        if (flashAlpha > 0) {
            flashAlpha = (flashAlpha - delta * 5f).coerceAtLeast(0f)
        }

        // 更新震动
        if (shakeTime > 0) {
            shakeTime -= delta
            shakeX = (Random.nextFloat() - 0.5f) * 20f
            shakeY = (Random.nextFloat() - 0.5f) * 20f
        } else {
            shakeX = 0f
            shakeY = 0f
        }

        // 碰撞检测
        checkCollisions()

        // 计分
        score += (currentSpeed * delta * 10).toInt()

        // 速度阈值变色
        checkSpeedThreshold()

        // 生成尾焰粒子
        spawnTrailParticles()
    }

    private fun updatePlayerPosition(delta: Float) {
        val targetX = if (isTouchingLeft) {
            laneX(0)
        } else if (isTouchingRight) {
            laneX(laneCount - 1)
        } else {
            laneX(targetLane)
        }

        // 平滑插值
        playerX += (targetX - playerX) * 8f * delta
        playerX = playerX.coerceIn(laneX(0), laneX(laneCount - 1))

        // 确定当前车道
        playerLane = when {
            playerX < laneX(0) + (laneX(1) - laneX(0)) * 0.5f -> 0
            playerX < laneX(1) + (laneX(2) - laneX(1)) * 0.5f -> 1
            else -> 2
        }
    }

    private fun spawnObstacle() {
        val type = when {
            Random.nextFloat() < 0.2f -> ObstacleType.MOVING
            Random.nextFloat() < 0.3f -> ObstacleType.PILLAR
            else -> ObstacleType.BLOCK
        }

        val blockedLanes = mutableSetOf<Int>()
        val numBlocked = if (Random.nextFloat() < 0.3f) 2 else 1

        repeat(numBlocked) {
            var lane: Int
            do {
                lane = Random.nextInt(laneCount)
            } while (blockedLanes.contains(lane))
            blockedLanes.add(lane)
        }

        // 确保至少有一条通道
        if (blockedLanes.size >= laneCount) {
            blockedLanes.remove(blockedLanes.first())
        }

        for (lane in blockedLanes) {
            obstacles.add(Obstacle(
                lane = lane,
                z = maxZ,
                type = type,
                widthScale = if (type == ObstacleType.BLOCK) Random.nextFloat() * 1.5f + 0.5f else 1f,
                moveDir = if (type == ObstacleType.MOVING) (if (Random.nextFloat() < 0.5f) 1f else -1f) else 0f
            ))
        }
    }

    private fun updateObstacles(delta: Float) {
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            obs.z -= currentSpeed * delta * 200

            // 移动障碍物左右摆动
            if (obs.type == ObstacleType.MOVING) {
                obs.lane = (obs.lane + obs.moveDir * delta * 2f).toInt().coerceIn(0, laneCount - 1)
                if (obs.lane <= 0 || obs.lane >= laneCount - 1) {
                    obs.moveDir = -obs.moveDir
                }
            }

            // 检查是否通过
            if (!obs.passed && obs.z < 0) {
                obs.passed = true
                onObstaclePassed()
            }

            // 移除远处的障碍物
            if (obs.z < -200) {
                iterator.remove()
            }
        }
    }

    private fun onObstaclePassed() {
        // 短促霓虹闪光
        flashAlpha = 0.3f
        flashColor = neonColors[Random.nextInt(neonColors.size)]
    }

    private fun checkCollisions() {
        for (obs in obstacles) {
            if (obs.passed) continue
            if (obs.z in -collisionDepth..collisionDepth) {
                if (obs.lane == playerLane) {
                    gameOver()
                    return
                }
            }
        }
    }

    private fun gameOver() {
        state = GameState.GAME_OVER
        highScore = maxOf(highScore, score)

        // 爆炸粒子
        spawnExplosion(width / 2f, height * 0.8f)

        // 屏幕震动
        shakeTime = 0.5f

        stopGame()

        // 延迟显示结果
        postDelayed({
            invalidate()
        }, 100)
    }

    private fun spawnExplosion(cx: Float, cy: Float) {
        repeat(50) {
            if (particles.size < maxParticles) {
                val angle = Random.nextFloat() * PI.toFloat() * 2f
                val speed = Random.nextFloat() * 800f + 200f
                particles.add(Particle(
                    x = cx, y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 0f,
                    maxLife = Random.nextFloat() * 1f + 0.5f,
                    color = neonColors.random(),
                    size = Random.nextFloat() * 6f + 2f
                ))
            }
        }
    }

    private fun spawnTrailParticles() {
        if (particles.size < maxParticles - 10) {
            repeat(2) {
                val angle = -PI.toFloat() / 2f + (Random.nextFloat() - 0.5f) * 0.5f
                val speed = Random.nextFloat() * 200f + 100f
                particles.add(Particle(
                    x = playerX + (Random.nextFloat() - 0.5f) * 20f,
                    y = height * 0.85f,
                    vx = cos(angle) * speed * 0.3f,
                    vy = sin(angle) * speed,
                    life = 0f,
                    maxLife = Random.nextFloat() * 0.5f + 0.2f,
                    color = neonColors.random(),
                    size = Random.nextFloat() * 3f + 1f
                ))
            }
        }

        // 速度线
        if (speedLines.size < maxSpeedLines && Random.nextFloat() < 0.3f) {
            speedLines.add(SpeedLine(
                x = Random.nextFloat() * width,
                y = Random.nextFloat() * height * 0.6f + height * 0.2f,
                length = Random.nextFloat() * 80f + 40f,
                speed = Random.nextFloat() * 500f + 300f,
                alpha = Random.nextInt(100) + 50
            ))
        }
    }

    private fun updateParticles(delta: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * delta
            p.y += p.vy * delta
            p.life += delta
            if (p.life >= p.maxLife) {
                iterator.remove()
            }
        }
    }

    private fun updateSpeedLines(delta: Float) {
        val iterator = speedLines.iterator()
        while (iterator.hasNext()) {
            val sl = iterator.next()
            sl.y += sl.speed * delta
            if (sl.y > height) {
                iterator.remove()
            }
        }
    }

    private fun checkSpeedThreshold() {
        val newIndex = when {
            currentSpeed >= 10f -> 3
            currentSpeed >= 8f -> 2
            currentSpeed >= 6f -> 1
            else -> 0
        }
        if (newIndex != tunnelColorIndex) {
            tunnelColorIndex = newIndex
            updateTunnelColors()
        }
    }

    private fun updateTunnelColors() {
        val colors = tunnelColors[tunnelColorIndex]
        tunnelLinePaint.color = colors.first
        tunnelGlowPaint.color = (colors.first and 0x00FFFFFF) or 0x80000000.toInt()
        tunnelGlowPaint.strokeWidth = 8f
    }

    // ═══ 绘制 ═══

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        canvas.save()
        canvas.translate(shakeX, shakeY)

        // 绘制隧道
        drawTunnel(canvas, w, h)

        // 绘制速度线
        drawSpeedLines(canvas)

        // 绘制障碍物
        drawObstacles(canvas, w, h)

        // 绘制飞船
        drawShip(canvas, w, h)

        // 绘制粒子
        drawParticles(canvas)

        // 绘制闪光
        if (flashAlpha > 0) {
            flashPaint.color = (flashColor and 0x00FFFFFF) or ((flashAlpha * 255).toInt() shl 24)
            canvas.drawRect(0f, 0f, w, h, flashPaint)
        }

        // 绘制高速警告
        if (currentSpeed > maxSpeed * 0.8f) {
            drawWarningOverlay(canvas, w, h)
        }

        canvas.restore()

        // 绘制UI
        drawUI(canvas, w, h)

        // 绘制状态
        when (state) {
            GameState.READY -> drawReadyScreen(canvas, w, h)
            GameState.GAME_OVER -> drawGameOverScreen(canvas, w, h)
            GameState.PLAYING -> {}
        }
    }

    private fun drawTunnel(canvas: Canvas, w: Float, h: Float) {
        val vpX = w / 2f
        val vpY = vanishingY

        // 隧道出口光晕
        val exitGlowSize = 60f + sin(System.currentTimeMillis() * 0.003f) * 20f
        vanishingGlowPaint.shader = RadialGradient(
            vpX, vpY, exitGlowSize,
            tunnelColors[tunnelColorIndex].first,
            0x00000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(vpX, vpY, exitGlowSize, vanishingGlowPaint)

        // 左右隧道边线
        val tunnelLeft = vpX - tunnelWidth / 2f
        val tunnelRight = vpX + tunnelWidth / 2f

        // 绘制多段线形成隧道
        val segments = 20
        for (i in 0 until segments) {
            val z1 = maxZ * i / segments
            val z2 = maxZ * (i + 1) / segments
            val scale1 = focalLength / (focalLength + z1)
            val scale2 = focalLength / (focalLength + z2)

            val lx1 = vpX + (tunnelLeft - vpX) * scale1
            val rx1 = vpX + (tunnelRight - vpX) * scale1
            val lx2 = vpX + (tunnelLeft - vpX) * scale2
            val rx2 = vpX + (tunnelRight - vpX) * scale2

            val y1 = vpY + (h - vpY) * scale1
            val y2 = vpY + (h - vpY) * scale2

            // 发光效果
            tunnelGlowPaint.alpha = (255 * (1f - i.toFloat() / segments) * 0.5f).toInt()
            canvas.drawLine(lx1, y1, lx2, y2, tunnelGlowPaint)
            canvas.drawLine(rx1, y1, rx2, y2, tunnelGlowPaint)

            // 主线
            tunnelLinePaint.alpha = (255 * (1f - i.toFloat() / segments)).toInt()
            canvas.drawLine(lx1, y1, lx2, y2, tunnelLinePaint)
            canvas.drawLine(rx1, y1, rx2, y2, tunnelLinePaint)
        }

        // 地面横向刻度线
        val lineSpacing = 100f
        val offset = tunnelOffset % lineSpacing
        val numLines = (maxZ / lineSpacing).toInt() + 2

        for (i in 0 until numLines) {
            val z = i * lineSpacing - offset
            if (z < 0 || z > maxZ) continue

            val scale = focalLength / (focalLength + z)
            val leftX = vpX + (tunnelLeft - vpX) * scale
            val rightX = vpX + (tunnelRight - vpX) * scale
            val y = vpY + (h - vpY) * scale

            groundLinePaint.alpha = (255 * (1f - z / maxZ) * 0.6f).toInt()
            canvas.drawLine(leftX, y, rightX, y, groundLinePaint)
        }
    }

    private fun drawSpeedLines(canvas: Canvas) {
        for (sl in speedLines) {
            speedLinePaint.color = 0x80FFFFFF.toInt()
            speedLinePaint.alpha = sl.alpha
            speedLinePaint.strokeWidth = 2f
            canvas.drawLine(sl.x, sl.y, sl.x, sl.y + sl.length, speedLinePaint)
        }
    }

    private fun drawObstacles(canvas: Canvas, w: Float, h: Float) {
        val vpX = w / 2f

        // 按 z 排序，远处先画
        val sorted = obstacles.sortedByDescending { it.z }

        for (obs in sorted) {
            if (obs.z < 0 || obs.z > maxZ) continue

            val scale = focalLength / (focalLength + obs.z)
            val centerX = laneX(obs.lane, obs.z, vpX)
            val baseY = vanishingY + (h - vanishingY) * scale
            val size = 40f * scale

            val alpha = (255 * (1f - obs.z / maxZ) * 0.7f + 76f).toInt().coerceIn(0, 255)
            val color = neonColors[obs.lane % neonColors.size]

            obstaclePaint.color = color
            obstaclePaint.alpha = alpha
            obstacleFillPaint.color = (color and 0x00FFFFFF) or (alpha / 4 shl 24)

            when (obs.type) {
                ObstacleType.BLOCK -> {
                    val halfW = size * obs.widthScale
                    val halfH = size * 0.6f
                    val rect = RectF(centerX - halfW, baseY - halfH, centerX + halfW, baseY + halfH)
                    canvas.drawRect(rect, obstacleFillPaint)
                    canvas.drawRect(rect, obstaclePaint)

                    // 对角线
                    canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, obstaclePaint)
                    canvas.drawLine(rect.right, rect.top, rect.left, rect.bottom, obstaclePaint)
                }
                ObstacleType.PILLAR -> {
                    val halfW = size * 0.3f
                    val pillarH = size * 2f
                    val rect = RectF(centerX - halfW, baseY - pillarH, centerX + halfW, baseY)
                    canvas.drawRect(rect, obstacleFillPaint)
                    canvas.drawRect(rect, obstaclePaint)
                }
                ObstacleType.MOVING -> {
                    val halfW = size * 0.8f
                    val halfH = size * 0.5f
                    val rect = RectF(centerX - halfW, baseY - halfH, centerX + halfW, baseY + halfH)
                    canvas.drawRect(rect, obstacleFillPaint)
                    canvas.drawRect(rect, obstaclePaint)

                    // 移动指示箭头
                    val arrowSize = size * 0.3f
                    canvas.drawLine(centerX - arrowSize, baseY, centerX + arrowSize, baseY, obstaclePaint)
                    canvas.drawLine(centerX + arrowSize * 0.7f, baseY - arrowSize * 0.3f,
                        centerX + arrowSize, baseY, obstaclePaint)
                    canvas.drawLine(centerX + arrowSize * 0.7f, baseY + arrowSize * 0.3f,
                        centerX + arrowSize, baseY, obstaclePaint)
                }
            }
        }
    }

    private fun drawShip(canvas: Canvas, w: Float, h: Float) {
        val cx = playerX
        val cy = h * 0.85f
        val shipSize = 30f

        // 计算倾斜
        val tilt = (playerX - w / 2f) / (w / 2f) * 15f

        canvas.save()
        canvas.rotate(tilt, cx, cy)

        // 尾焰光晕
        val trailGlowSize = shipSize * 3f + currentSpeed * 5f
        val trailGlowAlpha = (40 + currentSpeed * 5).toInt().coerceIn(0, 100)
        val trailColor = neonColors[tunnelColorIndex]
        vanishingGlowPaint.shader = RadialGradient(
            cx, cy + shipSize, trailGlowSize,
            trailColor,
            0x00000000,
            Shader.TileMode.CLAMP
        )
        vanishingGlowPaint.alpha = trailGlowAlpha
        canvas.drawCircle(cx, cy + shipSize, trailGlowSize, vanishingGlowPaint)

        // 飞船主体 - 倒三角
        val path = Path()
        path.moveTo(cx, cy - shipSize)  // 顶部尖
        path.lineTo(cx + shipSize * 0.8f, cy + shipSize * 0.3f)  // 右下
        path.lineTo(cx + shipSize * 0.3f, cy + shipSize * 0.5f)  // 右内
        path.lineTo(cx, cy + shipSize * 0.2f)  // 底部内
        path.lineTo(cx - shipSize * 0.3f, cy + shipSize * 0.5f)  // 左内
        path.lineTo(cx - shipSize * 0.8f, cy + shipSize * 0.3f)  // 左下
        path.close()

        // 发光效果
        shipPaint.color = 0xFFFF4081.toInt()
        shipPaint.alpha = 200
        canvas.drawPath(path, shipPaint)

        // 白色核心
        val corePath = Path()
        corePath.moveTo(cx, cy - shipSize * 0.6f)
        corePath.lineTo(cx + shipSize * 0.3f, cy + shipSize * 0.1f)
        corePath.lineTo(cx, cy)
        corePath.lineTo(cx - shipSize * 0.3f, cy + shipSize * 0.1f)
        corePath.close()

        shipCorePaint.alpha = 180
        canvas.drawPath(corePath, shipCorePaint)

        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val alpha = ((1f - p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            particlePaint.color = p.color
            particlePaint.alpha = alpha
            val size = p.size * (1f - p.life / p.maxLife * 0.5f)
            canvas.drawCircle(p.x, p.y, size, particlePaint)
        }
    }

    private fun drawWarningOverlay(canvas: Canvas, w: Float, h: Float) {
        val alpha = ((currentSpeed - maxSpeed * 0.8f) / (maxSpeed * 0.2f) * 30).toInt().coerceIn(0, 30)
        warningPaint.color = (alpha shl 24) or 0x00FF0000
        canvas.drawRect(0f, 0f, w, h, warningPaint)
    }

    private fun drawUI(canvas: Canvas, w: Float, h: Float) {
        // 分数
        scorePaint.textSize = 48f
        canvas.drawText("分数: $score", 30f, 80f, scorePaint)

        // 速度
        scorePaint.textSize = 32f
        canvas.drawText("速度: ${"%.1f".format(currentSpeed)}", 30f, 130f, scorePaint)

        // 最高分
        if (highScore > 0) {
            scorePaint.textSize = 28f
            canvas.drawText("最高: $highScore", 30f, 170f, scorePaint)
        }
    }

    private fun drawReadyScreen(canvas: Canvas, w: Float, h: Float) {
        // 半透明遮罩
        flashPaint.color = 0x80000000.toInt()
        canvas.drawRect(0f, 0f, w, h, flashPaint)

        // 标题
        hintPaint.textSize = 56f
        hintPaint.color = 0xFF00E5FF.toInt()
        canvas.drawText("霓虹隧道冲刺", w / 2f, h * 0.35f, hintPaint)

        // 副标题
        hintPaint.textSize = 32f
        hintPaint.color = 0xCCFFFFFF.toInt()
        canvas.drawText("在星空隧道中极速穿梭", w / 2f, h * 0.42f, hintPaint)

        // 操作提示
        hintPaint.textSize = 28f
        canvas.drawText("按住屏幕左侧 → 向左移动", w / 2f, h * 0.55f, hintPaint)
        canvas.drawText("按住屏幕右侧 → 向右移动", w / 2f, h * 0.60f, hintPaint)

        // 开始提示
        hintPaint.textSize = 36f
        hintPaint.color = 0xFFFFD740.toInt()
        val pulseAlpha = (sin(System.currentTimeMillis() * 0.005f) * 50 + 200).toInt()
        hintPaint.alpha = pulseAlpha
        canvas.drawText("点击屏幕开始", w / 2f, h * 0.72f, hintPaint)
        hintPaint.alpha = 255
    }

    private fun drawGameOverScreen(canvas: Canvas, w: Float, h: Float) {
        // 半透明遮罩
        flashPaint.color = 0xCC000000.toInt()
        canvas.drawRect(0f, 0f, w, h, flashPaint)

        // 游戏结束
        hintPaint.textSize = 56f
        hintPaint.color = 0xFFFF4081.toInt()
        canvas.drawText("游戏结束", w / 2f, h * 0.3f, hintPaint)

        // 分数
        hintPaint.textSize = 48f
        hintPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("分数: $score", w / 2f, h * 0.4f, hintPaint)

        // 最高分
        if (score >= highScore && highScore > 0) {
            hintPaint.textSize = 32f
            hintPaint.color = 0xFFFFD740.toInt()
            canvas.drawText("新纪录!", w / 2f, h * 0.47f, hintPaint)
        }

        hintPaint.textSize = 32f
        hintPaint.color = 0xCCFFFFFF.toInt()
        canvas.drawText("最高分: $highScore", w / 2f, h * 0.54f, hintPaint)

        // 重试提示
        hintPaint.textSize = 36f
        hintPaint.color = 0xFF69F0AE.toInt()
        val pulseAlpha = (sin(System.currentTimeMillis() * 0.005f) * 50 + 200).toInt()
        hintPaint.alpha = pulseAlpha
        canvas.drawText("点击屏幕再飞一次", w / 2f, h * 0.65f, hintPaint)
        hintPaint.alpha = 255

        // 返回提示
        hintPaint.textSize = 24f
        hintPaint.color = 0x80FFFFFF.toInt()
        canvas.drawText("按返回键回到菜单", w / 2f, h * 0.72f, hintPaint)
    }

    // ═══ 触摸控制 ═══

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                isDragging = false

                when (state) {
                    GameState.READY -> {
                        startGame()
                        return true
                    }
                    GameState.GAME_OVER -> {
                        startGame()
                        return true
                    }
                    GameState.PLAYING -> {
                        isTouchingLeft = event.x < width / 2f
                        isTouchingRight = event.x >= width / 2f
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == GameState.PLAYING) {
                    isDragging = true
                    isTouchingLeft = event.x < width / 2f
                    isTouchingRight = event.x >= width / 2f
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouchingLeft = false
                isTouchingRight = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ═══ 辅助函数 ═══

    private fun laneX(lane: Int): Float {
        val vpX = width / 2f
        val tunnelLeft = vpX - tunnelWidth / 2f
        val laneWidth = tunnelWidth / laneCount
        return tunnelLeft + laneWidth * (lane + 0.5f)
    }

    private fun laneX(lane: Int, z: Float, vpX: Float): Float {
        val scale = focalLength / (focalLength + z)
        val laneWidthAtZ = tunnelWidth * scale / laneCount
        return vpX + (lane - laneCount / 2f + 0.5f) * laneWidthAtZ
    }

    private val laneWidth: Float get() = tunnelWidth / laneCount

    companion object {
        private const val PI = Math.PI.toFloat()
    }
}
