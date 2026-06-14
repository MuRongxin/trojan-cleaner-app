package com.shortvideocleaner.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class GameActivity : AppCompatActivity() {

    private lateinit var rootView: View
    private lateinit var gameContainer: ViewGroup

    // 游戏卡片
    private lateinit var cardHeart: MaterialCardView
    private lateinit var cardFirework: MaterialCardView
    private lateinit var cardShake: MaterialCardView
    private lateinit var cardFlip: MaterialCardView

    // 当前显示的游戏视图
    private var currentGameView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setContentView(R.layout.activity_game)

        applyFullScreen()

        rootView = findViewById(R.id.root_game)
        gameContainer = findViewById(R.id.game_container)

        // 游戏卡片
        cardHeart = findViewById(R.id.card_heart_game)
        cardFirework = findViewById(R.id.card_firework_game)
        cardShake = findViewById(R.id.card_shake_game)
        cardFlip = findViewById(R.id.card_flip_game)

        setupGameCards()

        // 圆形展开动画 + 启动星空
        rootView.post {
            playRevealAnimation(true)
            findViewById<GameStarryView>(R.id.starry_game_bg)?.resumeAnimation()
        }
    }

    private fun setupGameCards() {
        cardHeart.setOnClickListener { startHeartGame() }
        cardFirework.setOnClickListener { startFireworkGame() }
        cardShake.setOnClickListener { startShakeGame() }
        cardFlip.setOnClickListener { startFlipGame() }
    }

    private fun startHeartGame() {
        hideMenu()
        val heartView = HeartGameView(this)
        showGame(heartView)
        heartView.startGame()
    }

    private fun startFireworkGame() {
        hideMenu()
        val fireworkView = FireworkView(this)
        showGame(fireworkView)
        fireworkView.startAnimation()
    }

    private fun startShakeGame() {
        hideMenu()
        val shakeView = ShakeGameView(this)
        showGame(shakeView)
    }

    private fun startFlipGame() {
        hideMenu()
        val flipView = CardFlipView(this)
        showGame(flipView)
    }

    private fun showGame(gameView: View) {
        gameContainer.removeAllViews()
        gameContainer.addView(gameView)
        gameContainer.visibility = View.VISIBLE
        currentGameView = gameView
    }

    private fun hideMenu() {
        findViewById<View>(R.id.menu_scroll).visibility = View.GONE
    }

    private fun showMenu() {
        gameContainer.removeAllViews()
        gameContainer.visibility = View.GONE
        findViewById<View>(R.id.menu_scroll).visibility = View.VISIBLE
        currentGameView = null
    }

    private fun playRevealAnimation(show: Boolean) {
        val w = rootView.width
        val h = rootView.height
        if (w <= 0 || h <= 0) return

        val diagonal = Math.hypot(w.toDouble(), h.toDouble()).toFloat()
        val startRadius = if (show) 0f else diagonal
        val endRadius = if (show) diagonal else 0f
        val centerX = w / 2
        val centerY = h / 2

        try {
            val anim = android.view.ViewAnimationUtils.createCircularReveal(
                rootView, centerX, centerY, startRadius, endRadius
            )
            anim.duration = 500

            if (show) {
                rootView.visibility = View.VISIBLE
                anim.start()
            } else {
                anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        rootView.visibility = View.INVISIBLE
                        finish()
                        overridePendingTransition(0, 0)
                    }
                })
                anim.start()
            }
        } catch (_: Exception) {
            if (!show) finish()
        }
    }

    private fun applyFullScreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onResume() {
        super.onResume()
        applyFullScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullScreen()
    }

    override fun onBackPressed() {
        if (currentGameView != null) {
            showMenu()
        } else {
            playRevealAnimation(false)
        }
    }
}
