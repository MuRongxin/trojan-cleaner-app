package com.shortvideocleaner.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.*
import kotlin.random.Random

class CardFlipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var backListener: (() -> Unit)? = null

    // 情话卡片数据
    private val loveMessages = listOf(
        LoveCard("💌", "我喜欢你\n认真且怂\n从一而终"),
        LoveCard("💕", "你是我\n最想留住的幸运"),
        LoveCard("🌸", "遇见你\n是所有故事的开始"),
        LoveCard("✨", "你笑起来\n真好看"),
        LoveCard("🌙", "今晚月色真美\n风也温柔"),
        LoveCard("💝", "想和你\n一起慢慢变老"),
        LoveCard("🌹", "你是我\n藏在心底的欢喜"),
        LoveCard("💫", "余生很长\n我只想和你走"),
        LoveCard("🎀", "你的名字\n是我最短的情书"),
        LoveCard("🦋", "喜欢你\n像风走了八千里"),
        LoveCard("🌈", "你是我\n最美的意外"),
        LoveCard("🍀", "遇见你\n花光了所有运气"),
        LoveCard("🎵", "想做你的\n小众喜好"),
        LoveCard("🌊", "你是我\n藏不住的欢喜"),
        LoveCard("⭐", "你是我\n写不完的温柔"),
        LoveCard("🎨", "你的眼里\n有星辰大海"),
        LoveCard("🔮", "想做你的\n枕边书"),
        LoveCard("🎪", "你是我\n最想留住的幸运"),
        LoveCard("🌺", "你是我\n今生最美的风景"),
        LoveCard("🎠", "想和你\n看遍世间繁华"),
    )

    // 卡片视图
    private val cards = mutableListOf<CardView>()
    private var currentCardIndex = 0
    private var flippedCount = 0

    // 背景装饰
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0A0A1A")
    }

    private val starPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
    }

    init {
        setWillNotDraw(false)

        // 返回按钮
        val backBtn = TextView(context).apply {
            text = "← 返回"
            setTextColor(Color.parseColor("#99FFFFCC"))
            textSize = 14f
            setPadding(32, 32, 32, 32)
        }
        backBtn.setOnClickListener { backListener?.invoke() }
        addView(backBtn)

        // 标题
        val title = TextView(context).apply {
            text = "🃏 情话翻翻看"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 20f
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        addView(title)

        // 创建卡片网格
        post { createCardGrid() }
    }

    fun setOnBackListener(listener: () -> Unit) {
        backListener = listener
    }

    private fun createCardGrid() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val padding = 40
        val cardWidth = (w - padding * 2) / 4
        val cardHeight = (h - 300) / 5
        val startY = 200

        // 打乱情话顺序
        val shuffled = loveMessages.shuffled().take(20)

        for (row in 0 until 5) {
            for (col in 0 until 4) {
                val index = row * 4 + col
                if (index >= shuffled.size) break

                val card = CardView(context)
                val x = padding + col * cardWidth
                val y = startY + row * cardHeight

                card.layoutParams = LayoutParams(cardWidth - 10, cardHeight - 10)
                card.x = x.toFloat()
                card.y = y.toFloat()
                card.setCardData(shuffled[index])
                card.setOnClickListener { onCardClicked(card) }

                addView(card)
                cards.add(card)
            }
        }

        // 添加翻牌统计
        val stats = TextView(context).apply {
            text = "已翻开 0/${shuffled.size}"
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 30
            }
        }
        addView(stats)
    }

    private fun onCardClicked(card: CardView) {
        if (card.isFlipped || card.isAnimating) return

        card.flip()
        flippedCount++

        // 更新统计
        updateStats()

        // 检查是否全部翻开
        if (flippedCount >= cards.size) {
            postDelayed({ showAllDoneMessage() }, 500)
        }
    }

    private fun updateStats() {
        val stats = getChildAt(childCount - 1) as? TextView ?: return
        stats.text = "已翻开 $flippedCount/${cards.size}"
    }

    private fun showAllDoneMessage() {
        val doneText = TextView(context).apply {
            text = "💕 所有的悄悄话都看完了\n你就是我心中最美的诗"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC0A0A1A"))
            setPadding(40, 40, 40, 40)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        addView(doneText)

        // 点击移除
        doneText.setOnClickListener {
            removeView(doneText)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 绘制背景
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 绘制闪烁星星
        val time = System.currentTimeMillis() * 0.001f
        for (i in 0 until 30) {
            val x = (i * 137.5f) % w
            val y = (i * 97.3f) % (h * 0.15f) + 60
            val twinkle = sin(time * 2 + i * 0.5f)
            val alpha = (twinkle * 50 + 150).toInt().coerceIn(100, 200)
            starPaint.alpha = alpha
            canvas.drawCircle(x, y, 1.5f, starPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cards.clear()
    }

    // 情话卡片数据
    data class LoveCard(val emoji: String, val message: String)

    // 卡片视图
    inner class CardView(context: Context) : View(context) {

        var isFlipped = false
        var isAnimating = false

        private var cardData: LoveCard? = null
        private var flipRotation = 0f

        private val cardPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#44FFB3C1")
        }

        private val emojiPaint = Paint().apply {
            isAntiAlias = true
            textSize = 60f
            textAlign = Paint.Align.CENTER
        }

        private val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = 28f
            color = Color.parseColor("#FFFFFF")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private val questionPaint = Paint().apply {
            isAntiAlias = true
            textSize = 48f
            color = Color.parseColor("#FF69B4")
            textAlign = Paint.Align.CENTER
        }

        private val heartPath = Path()

        fun setCardData(data: LoveCard) {
            cardData = data
            invalidate()
        }

        fun flip() {
            if (isAnimating) return
            isAnimating = true

            val flipIn = ObjectAnimator.ofFloat(this, "flipRotation", 0f, 90f)
            val flipOut = ObjectAnimator.ofFloat(this, "flipRotation", 90f, 0f)

            flipIn.duration = 200
            flipOut.duration = 200

            flipIn.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isFlipped = true
                    flipOut.start()
                }
            })

            flipOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })

            flipIn.start()
        }

        fun setFlipRotation(rotation: Float) {
            flipRotation = rotation
            scaleX = cos(Math.toRadians(rotation.toDouble())).toFloat()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2
            val cy = h / 2

            if (isFlipped) {
                // 翻开状态 - 显示情话
                drawFlippedCard(canvas, w, h, cx, cy)
            } else {
                // 未翻开状态 - 显示问号
                drawUnflippedCard(canvas, w, h, cx, cy)
            }
        }

        private fun drawUnflippedCard(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
            // 卡片背景
            cardPaint.color = Color.parseColor("#2A1A3E")
            canvas.drawRoundRect(4f, 4f, w - 4, h - 4, 12f, 12f, cardPaint)

            // 边框
            canvas.drawRoundRect(4f, 4f, w - 4, h - 4, 12f, 12f, borderPaint)

            // 装饰爱心
            cardPaint.color = Color.parseColor("#33FFB3C1")
            drawHeart(canvas, cx, cy - 20, 30f, cardPaint)

            // 问号
            questionPaint.textSize = min(w, h) * 0.4f
            canvas.drawText("?", cx, cy + 20, questionPaint)
        }

        private fun drawFlippedCard(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
            // 卡片背景
            cardPaint.color = Color.parseColor("#1A2A3E")
            canvas.drawRoundRect(4f, 4f, w - 4, h - 4, 12f, 12f, cardPaint)

            // 边框
            borderPaint.color = Color.parseColor("#4487CEEB")
            canvas.drawRoundRect(4f, 4f, w - 4, h - 4, 12f, 12f, borderPaint)

            cardData?.let { data ->
                // emoji
                emojiPaint.textSize = min(w, h) * 0.3f
                canvas.drawText(data.emoji, cx, cy - 10, emojiPaint)

                // 情话文字
                textPaint.textSize = min(w, h) * 0.12f
                val lines = data.message.split("\n")
                val lineHeight = textPaint.textSize * 1.4f
                var textY = cy + 30

                for (line in lines) {
                    canvas.drawText(line, cx, textY, textPaint)
                    textY += lineHeight
                }
            }
        }

        private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
            val s = size / 2
            heartPath.reset()
            heartPath.moveTo(cx, cy + s * 0.4f)
            heartPath.cubicTo(cx - s, cy - s * 0.2f, cx - s * 0.8f, cy - s, cx, cy - s * 0.4f)
            heartPath.cubicTo(cx + s * 0.8f, cy - s, cx + s, cy - s * 0.2f, cx, cy + s * 0.4f)
            heartPath.close()
            canvas.drawPath(heartPath, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN && !isFlipped && !isAnimating) {
                performClick()
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            return super.performClick()
        }
    }
}
