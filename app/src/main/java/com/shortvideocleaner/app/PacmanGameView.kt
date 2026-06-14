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

class PacmanGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ═══════════════════════════════════════════
    //  数据结构
    // ═══════════════════════════════════════════

    data class GridPos(val x: Int, val y: Int)
    data class Ghost(
        val type: GhostType,
        var gridPos: GridPos,
        var visualPos: Float,
        var visualPosY: Float,
        var dir: Direction = Direction.NONE,
        var isEaten: Boolean = false,
        var inHouse: Boolean = true,
        var houseTimer: Int = 0,
        var moveProgress: Float = 1f
    )

    enum class GhostType { BLINKY, PINKY, INKY, CLYDE }
    enum class Direction { UP, DOWN, LEFT, RIGHT, NONE }
    enum class GameState { LEVEL_INTRO, PLAYING, LEVEL_COMPLETE, DYING, GAME_OVER, GAME_WIN }

    // ═══════════════════════════════════════════
    //  经典迷宫 (21×21) — 0=路 1=墙 2=豆 3=能量豆 4=幽灵房 5=隧道
    // ═══════════════════════════════════════════

    private val classicMaze = arrayOf(
        intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
        intArrayOf(1,3,2,2,1,2,2,2,2,2,1,2,2,2,2,2,1,2,2,3,1),
        intArrayOf(1,2,1,2,1,2,1,1,1,2,1,2,1,1,1,2,1,2,1,2,1),
        intArrayOf(1,2,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,2,1),
        intArrayOf(1,2,1,1,2,1,2,1,1,1,1,1,1,1,2,1,2,1,1,2,1),
        intArrayOf(1,2,2,2,2,1,2,2,2,2,1,2,2,2,2,1,2,2,2,2,1),
        intArrayOf(1,1,2,1,1,1,1,1,1,4,1,4,1,1,1,1,1,1,2,1,1),
        intArrayOf(5,2,2,2,2,2,4,4,1,4,4,4,1,4,4,2,2,2,2,2,5),
        intArrayOf(1,1,2,1,1,1,2,1,1,1,1,1,1,1,2,1,1,1,2,1,1),
        intArrayOf(1,2,2,2,2,1,2,2,2,4,0,4,2,2,2,1,2,2,2,2,1),
        intArrayOf(1,1,1,1,2,1,1,1,2,4,0,4,2,1,1,1,2,1,1,1,1),
        intArrayOf(1,2,2,2,2,1,2,2,2,1,1,1,2,2,2,1,2,2,2,2,1),
        intArrayOf(1,1,2,1,1,1,2,1,1,1,1,1,1,1,2,1,1,1,2,1,1),
        intArrayOf(5,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,5),
        intArrayOf(1,1,2,1,1,1,1,1,2,1,1,1,2,1,1,1,1,1,2,1,1),
        intArrayOf(1,2,2,2,2,1,2,2,2,2,2,2,2,2,2,1,2,2,2,2,1),
        intArrayOf(1,2,1,1,2,1,2,1,1,1,1,1,1,1,2,1,2,1,1,2,1),
        intArrayOf(1,2,1,2,2,2,2,2,2,2,1,2,2,2,2,2,2,2,1,2,1),
        intArrayOf(1,2,1,2,1,2,1,1,1,2,1,2,1,1,1,2,1,2,1,2,1),
        intArrayOf(1,3,2,2,1,2,2,2,2,2,1,2,2,2,2,2,1,2,2,3,1),
        intArrayOf(1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1),
    )

    private var currentMaze = classicMaze
    private val MAZE_W = 21
    private val MAZE_H = 21

    // ═══════════════════════════════════════════
    //  游戏状态
    // ═══════════════════════════════════════════

    private var state = GameState.LEVEL_INTRO
    private var score = 0
    private var lives = 3
    private var currentLevel = 0
    private var dotsEaten = 0
    private var totalDots = 0
    private var introTimer = 0
    private var levelCompleteTimer = 0
    private var ghostEatCombo = 0
    private var dyingTimer = 0
    private var pausedUntil = 0L

    // 玩家
    private var playerGrid = GridPos(10, 15)
    private var playerPrevGrid = GridPos(10, 15)
    private var playerVisualX = 10f
    private var playerVisualY = 15f
    private var playerDir = Direction.NONE
    private var queuedDir = Direction.NONE
    private var playerLastDir = Direction.LEFT
    private var playerMoveProgress = 1f
    private var playerSpeed = 0.08f
    private var mouthAngle = 30f
    private var mouthOpen = true
    private var mouthAnimTimer = 0f

    // 幽灵
    private val ghosts = mutableListOf<Ghost>()
    private var ghostSpeed = 0.06f
    private var frightenedSpeed = 0.04f
    private var isFrightened = false
    private var frightenedTimer = 0
    private var frightenedFlash = false
    private var globalGhostDotLimit = 0
    private var ghostDotsEaten = 0

    // 豆子
    private val dots = mutableSetOf<Int>()  // pack(y, x)
    private val powerUps = mutableSetOf<Int>()

    // 分数弹出
    private val scorePopups = mutableListOf<ScorePopup>()

    // ═══════════════════════════════════════════
    //  动画 & 计时
    // ═══════════════════════════════════════════

    private var gameLoop: ValueAnimator? = null
    private var frameCount = 0
    private var dotFlash = false

    // ═══════════════════════════════════════════
    //  画笔
    // ═══════════════════════════════════════════

    private val bgPaint = Paint().apply { color = 0xFF0A0A1A.toInt() }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1A4E.toInt() }
    private val wallEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3333AA.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD700.toInt(); style = Paint.Style.FILL }
    private val powerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF69B4.toInt(); style = Paint.Style.FILL }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ghostBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ghostEyeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
    private val ghostEyeDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1A4E.toInt(); style = Paint.Style.FILL }
    private val frightenedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2121DE.toInt(); style = Paint.Style.FILL }
    private val frightenedFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    // ═══════════════════════════════════════════
    //  尺寸
    // ═══════════════════════════════════════════

    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var dpadZoneH = 0f     // 方向键区域高度
    private val dpadZoneRatio = 0.22f

    // ═══════════════════════════════════════════
    //  幽灵颜色
    // ═══════════════════════════════════════════

    private val ghostTypeColors = mapOf(
        GhostType.BLINKY to 0xFFFF0000.toInt(),
        GhostType.PINKY to 0xFFFFB8FF.toInt(),
        GhostType.INKY to 0xFF00FFFF.toInt(),
        GhostType.CLYDE to 0xFFFFB852.toInt(),
    )

    // ═══════════════════════════════════════════
    //  星星
    // ═══════════════════════════════════════════

    private val stars = List(60) { Star(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.5f + 0.5f, Random.nextFloat() * 2f + 1f, Random.nextFloat() * TWO_PI) }

    // ═══════════════════════════════════════════
    //  关卡文案
    // ═══════════════════════════════════════════

    private val levelMessages = listOf(
        Triple("第一关", "遇见", "遇见你的那一刻，\n我的世界亮了 ✨"),
        Triple("第二关", "心动", "每一次心跳，\n都在叫你的名字 💓"),
        Triple("第三关", "陪伴", "你是我最美的意外，\n也是最对的选择 🌟"),
        Triple("第四关", "守候", "余生很长，\n我只想和你走 🌈"),
        Triple("第五关", "永恒", "你是我写不完的温柔，\n道不尽的深情 💕"),
    )

    private var backListener: (() -> Unit)? = null

    fun setOnBackListener(l: () -> Unit) { backListener = l }
    fun startGame() { score = 0; lives = 3; currentLevel = 0; startLevel(0) }
    private fun resetGame() { startGame() }

    // ═══════════════════════════════════════════
    //  关卡
    // ═══════════════════════════════════════════

    private fun startLevel(idx: Int) {
        if (idx >= 5) { state = GameState.GAME_WIN; invalidate(); return }
        currentLevel = idx
        currentMaze = classicMaze
        dots.clear(); powerUps.clear(); totalDots = 0; dotsEaten = 0
        ghostDotsEaten = 0; globalGhostDotLimit = if (idx == 0) 0 else idx * 30

        for (y in 0 until MAZE_H) for (x in 0 until MAZE_W) {
            when (currentMaze[y][x]) {
                2 -> { dots.add(pack(y, x)); totalDots++ }
                3 -> { powerUps.add(pack(y, x)); totalDots++ }
            }
        }

        playerGrid = GridPos(10, 15); playerPrevGrid = GridPos(10, 15)
        playerVisualX = 10f; playerVisualY = 15f
        playerDir = Direction.NONE; queuedDir = Direction.NONE; playerLastDir = Direction.LEFT
        playerMoveProgress = 1f; mouthAngle = 30f; mouthOpen = true

        ghosts.clear()
        ghosts.add(Ghost(GhostType.BLINKY, GridPos(10, 9), 10f, 9f, Direction.UP, houseTimer = 0))
        ghosts.add(Ghost(GhostType.PINKY, GridPos(9, 10), 9f, 10f, Direction.NONE, houseTimer = 20))
        ghosts.add(Ghost(GhostType.INKY, GridPos(11, 10), 11f, 10f, Direction.NONE, houseTimer = 40))
        ghosts.add(Ghost(GhostType.CLYDE, GridPos(10, 10), 10f, 10f, Direction.NONE, houseTimer = 60))

        isFrightened = false; frightenedTimer = 0; ghostEatCombo = 0
        scorePopups.clear(); dyingTimer = 0; pausedUntil = 0

        playerSpeed = 0.09f + idx * 0.005f
        ghostSpeed = 0.055f + idx * 0.012f
        frightenedSpeed = 0.03f + idx * 0.003f

        state = GameState.LEVEL_INTRO; introTimer = 90

        gameLoop?.cancel()
        gameLoop = ValueAnimator.ofFloat(0f,1f).apply {
            duration = 16L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { frameCount++; update(); invalidate() }
            start()
        }
    }

    // ═══════════════════════════════════════════
    //  更新
    // ═══════════════════════════════════════════

    private fun update() {
        when (state) {
            GameState.LEVEL_INTRO -> { introTimer--; if (introTimer <= 0) state = GameState.PLAYING }
            GameState.PLAYING -> updatePlaying()
            GameState.DYING -> { dyingTimer--; if (dyingTimer <= 0) handleDeath() }
            GameState.LEVEL_COMPLETE -> { levelCompleteTimer--; if (levelCompleteTimer <= 0) startLevel(currentLevel + 1) }
            else -> {}
        }
        updateScorePopups()
    }

    private fun updatePlaying() {
        if (frameCount * 16L < pausedUntil) return

        // 玩家移动
        if (playerMoveProgress >= 1f) {
            tryAdvancePlayer()
        } else {
            playerMoveProgress += playerSpeed
            if (playerMoveProgress >= 1f) {
                playerMoveProgress = 1f
                playerPrevGrid = playerGrid
                playerGrid = playerPrevGrid  // no-op, real move happens in tryAdvance
                tryAdvancePlayer()
            }
        }
        updatePlayerVisual()

        // 幽灵移动
        for (g in ghosts) {
            if (g.inHouse) {
                g.houseTimer--
                if (g.houseTimer <= 0 && ghostDotsEaten >= globalGhostDotLimit) {
                    // 离开幽灵房
                    g.gridPos = GridPos(10, 9)
                    g.visualPos = 10f; g.visualPosY = 9f
                    g.dir = Direction.LEFT
                    g.moveProgress = 1f
                    g.inHouse = false
                }
                continue
            }
            if (g.moveProgress >= 1f) {
                advanceGhost(g)
            } else {
                val spd = if (isFrightened) frightenedSpeed else ghostSpeed
                g.moveProgress += spd
                if (g.moveProgress >= 1f) { g.moveProgress = 1f; advanceGhost(g) }
            }
            updateGhostVisual(g)
        }

        // 碰撞
        for (g in ghosts) {
            if (g.inHouse || g.isEaten) continue
            val dx = abs(playerVisualX - g.visualPos)
            val dy = abs(playerVisualY - g.visualPosY)
            if (dx < 0.7f && dy < 0.7f) {
                if (isFrightened) {
                    g.isEaten = true; g.inHouse = true; g.houseTimer = 120
                    g.gridPos = GridPos(10, 10); g.visualPos = 10f; g.visualPosY = 10f
                    ghostEatCombo++
                    val pts = when (ghostEatCombo) { 1 -> 200; 2 -> 400; 3 -> 800; else -> 1600 }
                    score += pts
                    scorePopups.add(ScorePopup(playerVisualX, playerVisualY - 0.5f, pts, 60))
                } else {
                    state = GameState.DYING; dyingTimer = 60; lives--
                    pausedUntil = frameCount * 16L + 1000
                }
                break
            }
        }

        // 吃豆
        val pk = pack(playerGrid.y, playerGrid.x)
        if (pk in dots) { dots -= pk; dotsEaten++; score += 10; ghostDotsEaten++ }
        if (pk in powerUps) {
            powerUps -= pk; dotsEaten++; score += 50; ghostDotsEaten++
            isFrightened = true; frightenedTimer = 360 - currentLevel * 30; ghostEatCombo = 0
        }

        // 恐惧倒计时
        if (isFrightened) {
            frightenedTimer--
            frightenedFlash = frightenedTimer < 60 && (frightenedTimer / 10) % 2 == 0
            if (frightenedTimer <= 0) { isFrightened = false; ghostEatCombo = 0 }
        }

        // 吃完全部豆子
        if (dotsEaten >= totalDots) {
            state = GameState.LEVEL_COMPLETE; levelCompleteTimer = 90
        }
    }

    private fun tryAdvancePlayer() {
        // 先尝试队列方向
        var moveDir = playerDir
        if (queuedDir != Direction.NONE && canMove(playerGrid, queuedDir)) {
            moveDir = queuedDir; queuedDir = Direction.NONE
        }
        // 反向立即生效
        if (queuedDir != Direction.NONE && isOpposite(playerDir, queuedDir)) {
            moveDir = queuedDir; queuedDir = Direction.NONE
        }

        if (moveDir != Direction.NONE && canMove(playerGrid, moveDir)) {
            playerLastDir = moveDir; playerDir = moveDir
            playerPrevGrid = playerGrid
            playerGrid = nextPos(playerGrid, moveDir)
            playerMoveProgress = 0f
        } else if (moveDir != Direction.NONE && !canMove(playerGrid, moveDir)) {
            // 当前方向不能走了就停下
            if (!canMove(playerGrid, playerDir)) playerDir = Direction.NONE
        }
    }

    private fun advanceGhost(g: Ghost) {
        val available = mutableListOf<Direction>()
        for (d in Direction.values()) {
            if (d == Direction.NONE) continue
            if (d == oppositeDir(g.dir)) continue
            if (canMove(g.gridPos, d)) available.add(d)
        }
        if (available.isEmpty()) {
            g.dir = oppositeDir(g.dir); return
        }

        val chosen = if (isFrightened && !g.isEaten) {
            available.random()
        } else {
            when (g.type) {
                GhostType.BLINKY -> ghostChase(g, playerGrid, available)
                GhostType.PINKY -> ghostChase(g, targetAhead(4), available)
                GhostType.INKY -> {
                    val blinkyPos = ghosts.firstOrNull { it.type == GhostType.BLINKY }?.gridPos ?: playerGrid
                    val ahead2 = targetAhead(2)
                    val mx = ahead2.x + (ahead2.x - blinkyPos.x)
                    val my = ahead2.y + (ahead2.y - blinkyPos.y)
                    ghostChase(g, GridPos(mx.coerceIn(0, MAZE_W - 1), my.coerceIn(0, MAZE_H - 1)), available)
                }
                GhostType.CLYDE -> {
                    val dist = abs(g.gridPos.x - playerGrid.x) + abs(g.gridPos.y - playerGrid.y)
                    if (dist > 8) ghostChase(g, playerGrid, available) else available.random()
                }
            }
        }

        g.dir = chosen
        g.gridPos = nextPos(g.gridPos, chosen)
        g.moveProgress = 0f
    }

    private fun targetAhead(steps: Int): GridPos {
        var tx = playerGrid.x; var ty = playerGrid.y
        repeat(steps) {
            val n = nextPos(GridPos(tx, ty), playerDir)
            if (canMove(GridPos(tx, ty), playerDir)) { tx = n.x; ty = n.y }
        }
        return GridPos(tx, ty)
    }

    private fun ghostChase(g: Ghost, target: GridPos, available: List<Direction>): Direction {
        return available.minByOrNull { d ->
            val n = nextPos(g.gridPos, d); abs(n.x - target.x) + abs(n.y - target.y)
        } ?: available.random()
    }

    private fun handleDeath() {
        if (lives <= 0) { state = GameState.GAME_OVER; return }
        playerGrid = GridPos(10, 15); playerPrevGrid = playerGrid
        playerVisualX = 10f; playerVisualY = 15f
        playerDir = Direction.NONE; queuedDir = Direction.NONE; playerMoveProgress = 1f
        for (g in ghosts) {
            g.gridPos = GridPos(10, 10); g.visualPos = 10f; g.visualPosY = 10f
            g.inHouse = true; g.houseTimer = 30; g.isEaten = false; g.moveProgress = 1f
        }
        isFrightened = false; frightenedTimer = 0
        state = GameState.PLAYING
    }

    private fun updatePlayerVisual() {
        val fx = playerPrevGrid.x.toFloat(); val fy = playerPrevGrid.y.toFloat()
        val tx = playerGrid.x.toFloat(); val ty = playerGrid.y.toFloat()
        playerVisualX = fx + (tx - fx) * playerMoveProgress
        playerVisualY = fy + (ty - fy) * playerMoveProgress

        // 嘴动画
        val speedMul = if (playerDir != Direction.NONE) 1f else 0.3f
        mouthAnimTimer += 0.25f * speedMul
        mouthAngle = (sin(mouthAnimTimer).absoluteValue * 40f + 2f).coerceIn(2f, 42f)
    }

    private fun updateGhostVisual(g: Ghost) {
        val px: Float; val py: Float
        when (g.dir) {
            Direction.LEFT ->  { px = g.gridPos.x + 1f; py = g.gridPos.y.toFloat() }
            Direction.RIGHT -> { px = g.gridPos.x - 1f; py = g.gridPos.y.toFloat() }
            Direction.UP ->    { px = g.gridPos.x.toFloat(); py = g.gridPos.y + 1f }
            Direction.DOWN ->  { px = g.gridPos.x.toFloat(); py = g.gridPos.y - 1f }
            else ->            { px = g.gridPos.x.toFloat(); py = g.gridPos.y.toFloat() }
        }
        g.visualPos = px + (g.gridPos.x - px) * g.moveProgress
        g.visualPosY = py + (g.gridPos.y - py) * g.moveProgress
    }

    private fun updateScorePopups() {
        val iter = scorePopups.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.y -= 0.03f; p.timer--
            if (p.timer <= 0) iter.remove()
        }
    }

    // ═══════════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════════

    private fun canMove(p: GridPos, d: Direction): Boolean {
        val n = nextPos(p, d)
        // 隧道环绕
        if (n.x < 0 || n.x >= MAZE_W) return currentMaze[p.y][p.x] == 5
        if (n.y < 0 || n.y >= MAZE_H) return false
        val cell = currentMaze[n.y][n.x]
        return cell != 1 && cell != 4
    }

    private fun nextPos(p: GridPos, d: Direction): GridPos = when (d) {
        Direction.UP -> GridPos(p.x, p.y - 1)
        Direction.DOWN -> GridPos(p.x, p.y + 1)
        Direction.LEFT -> if (p.x == 0) GridPos(MAZE_W - 1, p.y) else GridPos(p.x - 1, p.y)
        Direction.RIGHT -> if (p.x == MAZE_W - 1) GridPos(0, p.y) else GridPos(p.x + 1, p.y)
        Direction.NONE -> p
    }

    private fun oppositeDir(d: Direction) = when (d) {
        Direction.UP -> Direction.DOWN; Direction.DOWN -> Direction.UP
        Direction.LEFT -> Direction.RIGHT; Direction.RIGHT -> Direction.LEFT
        Direction.NONE -> Direction.NONE
    }

    private fun isOpposite(a: Direction, b: Direction) = oppositeDir(a) == b
    private fun pack(y: Int, x: Int) = y * 100 + x

    // ═══════════════════════════════════════════
    //  触摸 → 虚拟方向键
    // ═══════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x; val y = event.y
            // 左上角返回区域
            if (x < cellSize * 2 && y < cellSize * 2) {
                if (state == GameState.GAME_OVER || state == GameState.GAME_WIN) resetGame()
                else backListener?.invoke()
                return true
            }
            // 游戏结束 / 胜利时点击重新开始
            if (state == GameState.GAME_OVER || state == GameState.GAME_WIN) {
                resetGame()
                return true
            }
        }

        if (state != GameState.PLAYING) return true

        val x = event.x; val y = event.y
        val h = height.toFloat()
        if (y < h * (1f - dpadZoneRatio)) return true  // 不在方向键区域

        val relY = (y - h * (1f - dpadZoneRatio)) / (h * dpadZoneRatio)
        val relX = x / width

        when {
            relY < 0.5f && relX in 0.33f..0.66f -> queuedDir = Direction.UP
            relY >= 0.5f && relX in 0.33f..0.66f -> queuedDir = Direction.DOWN
            relX < 0.33f && relY in 0.25f..0.75f -> queuedDir = Direction.LEFT
            relX > 0.66f && relY in 0.25f..0.75f -> queuedDir = Direction.RIGHT
        }

        // 如果当前可以立即应用（在路口或反向），直接生效
        if (queuedDir != Direction.NONE) {
            if (canMove(playerGrid, queuedDir) || isOpposite(playerDir, queuedDir)) {
                if (isOpposite(playerDir, queuedDir)) {
                    val tmp = playerGrid; playerGrid = playerPrevGrid; playerPrevGrid = tmp
                    playerMoveProgress = 1f - playerMoveProgress
                }
                playerDir = queuedDir; playerLastDir = queuedDir; queuedDir = Direction.NONE
            }
        }

        return true
    }

    // ═══════════════════════════════════════════
    //  绘制
    // ═══════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val mazeAreaH = h * (1f - dpadZoneRatio)
        cellSize = min(w / MAZE_W, mazeAreaH / MAZE_H)
        offsetX = (w - cellSize * MAZE_W) / 2f
        offsetY = (mazeAreaH - cellSize * MAZE_H) / 2f
        dpadZoneH = h * dpadZoneRatio

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        drawStars(canvas, w, h)

        when (state) {
            GameState.LEVEL_INTRO -> { drawMaze(canvas); drawHUD(canvas); drawLevelIntro(canvas, w, h) }
            GameState.PLAYING, GameState.DYING -> { drawMaze(canvas); drawDots(canvas); drawPlayer(canvas); drawGhosts(canvas); drawHUD(canvas); drawScorePopups(canvas); drawDPad(canvas, w, h) }
            GameState.LEVEL_COMPLETE -> { drawMaze(canvas); drawHUD(canvas); drawLevelComplete(canvas, w, h) }
            GameState.GAME_OVER -> drawGameOver(canvas, w, h)
            GameState.GAME_WIN -> drawGameWin(canvas, w, h)
        }
    }

    private fun cellCenter(cx: Int, cy: Int) = Pair(offsetX + cx * cellSize + cellSize / 2, offsetY + cy * cellSize + cellSize / 2)

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        val t = frameCount / 60f
        for (s in stars) {
            val alpha = ((sin(t * s.twinkle + s.offset) * 0.4f + 0.6f) * 40).toInt()
            starPaint.alpha = alpha
            canvas.drawCircle(s.x * w, s.y * h, s.size, starPaint)
        }
    }

    private fun drawMaze(canvas: Canvas) {
        for (y in 0 until MAZE_H) for (x in 0 until MAZE_W) {
            val cell = currentMaze[y][x]
            val left = offsetX + x * cellSize; val top = offsetY + y * cellSize
            if (cell == 1) {
                canvas.drawRect(left, top, left + cellSize, top + cellSize, wallPaint)
                canvas.drawRect(left, top, left + cellSize, top + cellSize, wallEdgePaint)
            } else if (cell == 4) {
                canvas.drawRect(left, top, left + cellSize, top + cellSize, wallPaint)
            }
        }
    }

    private fun drawDots(canvas: Canvas) {
        for (y in 0 until MAZE_H) for (x in 0 until MAZE_W) {
            val pk = pack(y, x)
            if (pk in dots) {
                val (cx, cy) = cellCenter(x, y)
                canvas.drawCircle(cx, cy, cellSize * 0.12f, dotPaint)
            }
            if (pk in powerUps) {
                val (cx, cy) = cellCenter(x, y)
                val r = if (dotFlash) cellSize * 0.22f else cellSize * 0.15f
                powerPaint.alpha = if (dotFlash) 255 else 180
                canvas.drawCircle(cx, cy, r, powerPaint)
            }
        }
        if (frameCount % 30 == 0) dotFlash = !dotFlash
    }

    private fun drawPlayer(canvas: Canvas) {
        val cx = offsetX + playerVisualX * cellSize + cellSize / 2
        val cy = offsetY + playerVisualY * cellSize + cellSize / 2
        val r = cellSize * 0.38f

        if (state == GameState.DYING && (dyingTimer / 5) % 2 == 0) return

        val angle = when (playerLastDir) {
            Direction.RIGHT -> 0f; Direction.DOWN -> 90f; Direction.LEFT -> 180f; Direction.UP -> 270f; Direction.NONE -> 0f
        }
        val halfMouth = mouthAngle / 2f

        playerPaint.color = 0xFFFFFF00.toInt()
        val path = Path().apply {
            moveTo(cx, cy)
            arcTo(cx - r, cy - r, cx + r, cy + r, angle + halfMouth, 360f - 2 * halfMouth, false)
            close()
        }
        canvas.drawPath(path, playerPaint)
    }

    private fun drawGhosts(canvas: Canvas) {
        for (g in ghosts) {
            if (g.inHouse && g.houseTimer > 0) continue
            val cx = offsetX + g.visualPos * cellSize + cellSize / 2
            val cy = offsetY + g.visualPosY * cellSize + cellSize / 2
            val r = cellSize * 0.35f

            if (g.isEaten) {
                // 只画眼睛
                drawGhostEyes(canvas, cx, cy, r, g.dir)
                continue
            }

            val bodyColor = when {
                isFrightened && frightenedFlash -> 0xFFFFFFFF.toInt()
                isFrightened -> 0xFF2121DE.toInt()
                else -> ghostTypeColors[g.type]!!
            }

            // 身体：圆头 + 波浪裙边
            val path = Path().apply {
                addCircle(cx, cy - r * 0.3f, r, Path.Direction.CW)
                // 裙边
                val skirtTop = cy + r * 0.2f
                val waveCount = 4
                val waveW = 2 * r / waveCount
                moveTo(cx - r, skirtTop)
                for (i in 0 until waveCount) {
                    val sx = cx - r + i * waveW
                    rLineTo(waveW / 2, r * 0.45f)
                    rLineTo(waveW / 2, -r * 0.45f)
                }
                close()
            }
            ghostBodyPaint.color = bodyColor
            canvas.drawPath(path, ghostBodyPaint)

            if (!isFrightened) drawGhostEyes(canvas, cx, cy, r, g.dir)
            else {
                // 恐惧表情
                ghostEyeWhite.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.22f, ghostEyeWhite)
                canvas.drawCircle(cx + r * 0.3f, cy - r * 0.3f, r * 0.22f, ghostEyeWhite)
                canvas.drawLine(cx - r * 0.5f, cy + r * 0.1f, cx - r * 0.1f, cy + r * 0.25f, wallPaint)
                canvas.drawLine(cx - r * 0.1f, cy + r * 0.1f, cx - r * 0.5f, cy + r * 0.25f, wallPaint)
                canvas.drawLine(cx + r * 0.5f, cy + r * 0.1f, cx + r * 0.1f, cy + r * 0.25f, wallPaint)
                canvas.drawLine(cx + r * 0.1f, cy + r * 0.1f, cx + r * 0.5f, cy + r * 0.25f, wallPaint)
            }
        }
    }

    private fun drawGhostEyes(canvas: Canvas, cx: Float, cy: Float, r: Float, dir: Direction) {
        val exOff = r * 0.3f; val eyOff = -r * 0.25f
        val pupilOff = r * 0.08f
        var pdx = 0f; var pdy = 0f
        when (dir) {
            Direction.LEFT -> pdx = -pupilOff; Direction.RIGHT -> pdx = pupilOff
            Direction.UP -> pdy = -pupilOff; Direction.DOWN -> pdy = pupilOff
            else -> {}
        }
        for (sx in listOf(-1f, 1f)) {
            canvas.drawCircle(cx + exOff * sx, cy + eyOff, r * 0.25f, ghostEyeWhite)
            canvas.drawCircle(cx + exOff * sx + pdx, cy + eyOff + pdy, r * 0.11f, ghostEyeDot)
        }
    }

    private fun drawScorePopups(canvas: Canvas) {
        textPaint.textSize = cellSize * 0.5f
        for (p in scorePopups) {
            val cx = offsetX + p.x * cellSize + cellSize / 2
            val cy = offsetY + p.y * cellSize
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.alpha = (p.timer / 60f * 255).toInt()
            canvas.drawText("${p.points}", cx, cy, textPaint)
        }
        textPaint.alpha = 255
    }

    private fun drawDPad(canvas: Canvas, w: Float, h: Float) {
        val top = h - dpadZoneH
        val alpha = 40
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha; style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF6666AA.toInt() }

        val cx = w / 2; val cy = top + dpadZoneH / 2; val sz = dpadZoneH * 0.3f
        // 十字轮廓
        canvas.drawLine(cx - sz, cy, cx + sz, cy, paint)
        canvas.drawLine(cx, cy - sz, cx, cy + sz, paint)
        // 三角形箭头
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha; color = 0xFF6666AA.toInt(); style = Paint.Style.FILL }
        val asz = sz * 0.25f
        // UP
        Path().apply { moveTo(cx, cy - sz + asz); lineTo(cx - asz, cy - sz); lineTo(cx + asz, cy - sz); close() }; canvas.drawPath(Path().apply {
            moveTo(cx, cy - sz + asz); lineTo(cx - asz, cy - sz); lineTo(cx + asz, cy - sz); close()
        }, arrowPaint)
    }

    private fun drawHUD(canvas: Canvas) {
        val msg = levelMessages[currentLevel]
        hudPaint.textSize = cellSize * 0.55f
        canvas.drawText("${msg.first} · ${msg.second}", offsetX + cellSize, offsetY - cellSize * 0.3f, hudPaint)
        canvas.drawText("❤️ x $lives", offsetX + cellSize * (MAZE_W - 3), offsetY - cellSize * 0.3f, hudPaint)
        textPaint.textSize = cellSize * 0.4f
        textPaint.color = 0xFFFFD700.toInt()
        canvas.drawText("Score: $score", offsetX + cellSize * MAZE_W / 2, offsetY - cellSize * 0.3f, textPaint)

        if (isFrightened) {
            textPaint.color = 0xFF2121DE.toInt()
            textPaint.textSize = cellSize * 0.45f
            canvas.drawText("FEAR! ${frightenedTimer / 60}s", offsetX + cellSize * MAZE_W / 2, offsetY - cellSize * 0.9f, textPaint)
        }
    }

    private fun drawLevelIntro(canvas: Canvas, w: Float, h: Float) {
        val msg = levelMessages[currentLevel]
        textPaint.color = 0xFFFFB6C1.toInt(); textPaint.textSize = 48f
        canvas.drawText(msg.second, w / 2, h / 2 - 80, textPaint)
        textPaint.color = 0xFFFFFFFF.toInt(); textPaint.textSize = 24f
        canvas.drawText(msg.third.replace("\n", " "), w / 2, h / 2, textPaint)
    }

    private fun drawLevelComplete(canvas: Canvas, w: Float, h: Float) {
        val msg = levelMessages[currentLevel]
        textPaint.color = 0xFFFFD700.toInt(); textPaint.textSize = 48f
        canvas.drawText("通关! ${msg.second}", w / 2, h / 2 - 40, textPaint)
    }

    private fun drawGameOver(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = 0xFFFF6B6B.toInt(); textPaint.textSize = 48f
        canvas.drawText("游戏结束", w / 2, h / 2 - 40, textPaint)
        textPaint.color = 0xFFFFB6C1.toInt(); textPaint.textSize = 28f
        canvas.drawText("愿我们的故事，永远甜蜜 💕", w / 2, h / 2 + 40, textPaint)
        textPaint.color = 0x99FFFFFF.toInt(); textPaint.textSize = 22f
        canvas.drawText("点击屏幕重新开始", w / 2, h / 2 + 90, textPaint)
    }

    private fun drawGameWin(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = 0xFFFFD700.toInt(); textPaint.textSize = 48f
        canvas.drawText("全部通关! 🎉", w / 2, h / 2 - 40, textPaint)
        textPaint.color = 0xFFFFB6C1.toInt(); textPaint.textSize = 28f
        canvas.drawText("你就是我心中最美的诗 💕", w / 2, h / 2 + 40, textPaint)
        textPaint.color = 0x99FFFFFF.toInt(); textPaint.textSize = 22f
        canvas.drawText("点击屏幕重新开始", w / 2, h / 2 + 90, textPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameLoop?.cancel()
    }

    // ═══════════════════════════════════════════
    //  数据类
    // ═══════════════════════════════════════════

    private data class Star(val x: Float, val y: Float, val size: Float, val twinkle: Float, val offset: Float)
    private data class ScorePopup(val x: Float, var y: Float, val points: Int, var timer: Int)

    companion object {
        private const val TWO_PI = (Math.PI * 2).toFloat()
    }
}
