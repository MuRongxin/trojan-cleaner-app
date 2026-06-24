package com.shortvideocleaner.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.View
import android.view.ViewAnimationUtils
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.shortvideocleaner.app.model.AppInfo
import kotlinx.coroutines.*
import kotlin.math.hypot
import kotlin.math.max

class QuoteActivity : AppCompatActivity() {

    private var videoApps: List<AppInfo> = emptyList()
    private var currentQuoteIndex = 0
    private var uninstallCursor = 0
    private var uninstallAttempted = false
    private var isAutoUninstalling = false
    private var dialogShown = false
    private var persuasionDialog: AlertDialog? = null
    private var persuasionApps: List<AppInfo> = emptyList()
    private val persuasionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var countdownSec = 0

    private lateinit var rootView: View
    private lateinit var tvQuote: TextView
    private lateinit var tvAllDone: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: TextView
    private lateinit var progressQuote: ProgressBar

    private val quotes = listOf(
        "我能否将你比作夏日？\n你比它更可爱、更温婉。",
        "使生如夏花之绚烂，\n死如秋叶之静美。",
        "人生至福，\n就是确信有人爱你。",
        "恭喜解锁新成就：\n成年人体验卡一张 🎉",
        "睡了三天三夜才发现，\n原来床可以这么软",
        "通宵打游戏的感觉，\n久违了",
        "染个头发、打个耳洞，\n做回自己",
        "把闹钟关掉，\n睡到自然醒",
        "以后再也没有人\n收你手机了 📱",
        "想去哪座城市，\n现在可以自己做主了",
        "约上最好的朋友，\n做最疯的事",
        "見たい人に会いに行こう\n（去见想见的人吧）",
        "Life is like a box of chocolates.\n你永远不知道下一颗是什么味道。",
        " Carpe diem.\n及时行乐，孩子们。",
        "有些鸟儿是关不住的，\n它们的羽毛太鲜亮了。",
        "愿你在我看不到的地方安然无恙，\n愿你的冬天永远不缺暖阳。",
        "所有大人都曾经是小孩，\n虽然只有少数人记得。",
        "你好吗？\n我很好。",
        "如果再也不能见到你，\n祝你早安，午安，晚安。",
        "我不知道将去何方，\n但我已在路上。",
        "有人住高楼，有人在深沟，\n有人光万丈，有人一身锈。",
        "斯人若彩虹，\n遇上方知有。",
        "念念不忘，\n必有回响。",
        "要么忙着活，\n要么忙着死。",
        "你保护世界，\n我保护你。",
        "世界上有一种鸟是没有脚的，\n它只能一直飞呀飞。",
        "别让别人告诉你，你成不了才。\n如果你有梦想，就要去捍卫它。",
        "前面漆黑一片，什么也看不到。\n也不是，天亮后会很美的。",
        "希望是美好的，\n也许是人间至善，而美好的事物永不消逝。",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)  // 禁止默认转场
        setContentView(R.layout.activity_quote)

        // 全屏 + 沉浸
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        applyFullScreen()

        rootView = findViewById(R.id.root_quote)
        tvQuote = findViewById(R.id.tv_quote_text)
        tvAllDone = findViewById(R.id.tv_all_done)
        btnNext = findViewById(R.id.btn_next_quote)
        btnBack = findViewById(R.id.btn_back)
        progressQuote = findViewById(R.id.progress_quote)

        btnNext.setOnClickListener { showNextQuote() }
        btnBack.setOnClickListener { finishWithAnimation() }

        // 扫描应用 + 开始引言
        scanAndStart()
    }

    // 随机选中的角落（进出用同一个）
    private var animCenterX = 0
    private var animCenterY = 0
    private var cornerPicked = false

    private fun pickRandomCorner(w: Int, h: Int) {
        if (cornerPicked) return
        cornerPicked = true
        val corners = listOf(
            0 to 0,           // 左上
            w to 0,           // 右上
            0 to h,           // 左下
            w to h            // 右下
        )
        val (cx, cy) = corners.random()
        animCenterX = cx
        animCenterY = cy
    }

    // ── 圆形展开动画 ──

    private fun playRevealAnimation(show: Boolean) {
        val w = rootView.width
        val h = rootView.height
        if (w <= 0 || h <= 0) return

        pickRandomCorner(w, h)

        val diagonal = hypot(w.toFloat(), h.toFloat())
        val startRadius = if (show) 0f else diagonal
        val endRadius = if (show) diagonal else 0f

        try {
            val anim = ViewAnimationUtils.createCircularReveal(
                rootView, animCenterX, animCenterY, startRadius, endRadius
            )
            anim.duration = 500

            if (show) {
                rootView.visibility = View.VISIBLE
                anim.start()
            } else {
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
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

    private fun finishWithAnimation() {
        playRevealAnimation(false)
    }

    // ── 扫描 + 开始引言 ──

    private fun scanAndStart() {
        // 从 MainActivity 传来的检测结果
        val passedPackages = intent.getStringArrayListExtra("detected_packages")

        if (passedPackages != null && passedPackages.isNotEmpty()) {
            // 直接使用传来的结果，无需扫描
            val allApps = AppScanner.getInstalledApps(this, includeSystem = true)
            videoApps = allApps.filter { passedPackages.contains(it.packageName) }
            progressQuote.visibility = View.GONE
            startQuotes()
        } else {
            // 没有传结果，自行扫描
            progressQuote.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.IO).launch {
                val apps = AppScanner.getInstalledApps(this@QuoteActivity, includeSystem = true)
                videoApps = VideoAppDetector.detect(apps)

                withContext(Dispatchers.Main) {
                    progressQuote.visibility = View.GONE
                    startQuotes()
                }
            }
        }
    }

    private fun startQuotes() {
        currentQuoteIndex = 0
        uninstallCursor = 0
        uninstallAttempted = false
        isAutoUninstalling = false

        // 始终展示引言，即使没有检测到可卸载的应用
        showQuote(0)
        tvQuote.visibility = View.VISIBLE
        btnNext.visibility = View.VISIBLE
        btnBack.visibility = View.VISIBLE

        // 圆形展开动画（随机角落）+ 启动星空
        rootView.post {
            playRevealAnimation(true)
            findViewById<StarryBackgroundView>(R.id.starry_bg)?.resumeAnimation()
        }
    }

    // ── 引言切换 + 卸载 ──

    private fun showNextQuote() {
        currentQuoteIndex++

        // 如果没有可卸载的应用，纯粹展示引言，展示 8 条后结束
        if (videoApps.isEmpty()) {
            if (currentQuoteIndex >= quotes.size) {
                showAllDone()
            } else {
                showQuote(currentQuoteIndex)
            }
            return
        }

        // 已经在自动卸载流程中，忽略重复点击
        if (isAutoUninstalling) return

        // 还有目标应用未处理：点击后开始连续自动卸载
        if (uninstallCursor < videoApps.size) {
            isAutoUninstalling = true
            showQuote(currentQuoteIndex)
            uninstallAttempted = true
            VideoAppDetector.uninstallApp(this, videoApps[uninstallCursor].packageName)
        } else {
            showAllDone()
        }
    }

    private fun showQuote(index: Int) {
        tvQuote.text = quotes[index % quotes.size]
    }

    // ── 劝导弹窗（5 秒倒计时，不操作则自动进入卸载）──

    private fun checkAndPersuade() {
        if (!uninstallAttempted || dialogShown || videoApps.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val freshApps = AppScanner.getInstalledApps(this@QuoteActivity, includeSystem = true)
            val stillInstalled = VideoAppDetector.detect(freshApps)
            val targetPkg = videoApps.getOrNull(uninstallCursor)?.packageName
            val refused = targetPkg != null && stillInstalled.any { it.packageName == targetPkg }

            withContext(Dispatchers.Main) {
                if (refused) {
                    persuasionApps = stillInstalled
                    dialogShown = true
                    countdownSec = 10
                    val appNames = stillInstalled.joinToString("、") { it.appName }

                    val dialog = AlertDialog.Builder(this@QuoteActivity)
                        .setTitle("💕 给你一个小小的提醒")
                        .setMessage(buildCountdownMsg(appNames, countdownSec))
                        .setPositiveButton("好的，帮我清理") { _, _ ->
                            stopCountdown()
                            triggerBatchUninstall()
                        }
                        .setNegativeButton("不用了") { _, _ ->
                            stopCountdown()
                            dialogShown = false
                            uninstallAttempted = false
                            uninstallCursor++
                            advanceOrFinish()
                        }
                        .setCancelable(false)
                        .show()
                    persuasionDialog = dialog

                    // 每秒更新倒计时，归零自动卸载
                    persuasionHandler.post(object : Runnable {
                        override fun run() {
                            if (!dialogShown) return
                            countdownSec--
                            if (countdownSec <= 0) {
                                // 5 秒未操作 → 重新推进到系统卸载页面
                                retriggerUninstall()
                            } else {
                                persuasionDialog?.setMessage(buildCountdownMsg(appNames, countdownSec))
                                persuasionHandler.postDelayed(this, 1000)
                            }
                        }
                    })
                } else {
                    // 卸载成功，推进到下一个
                    uninstallAttempted = false
                    uninstallCursor++
                    advanceOrFinish()
                }
            }
        }
    }

    private fun buildCountdownMsg(appNames: String, sec: Int): String {
        return "我发现你的手机里装了特别耗时间的应用：\n\n" +
            "${appNames}\n\n" +
            "它们会让你不知不觉刷掉好几个小时…\n" +
            "生命那么短，应该花在更美好的事情上呀 ✨\n\n" +
            "⏳ ${sec} 秒后自动帮你清理…"
    }

    private fun stopCountdown() {
        persuasionHandler.removeCallbacksAndMessages(null)
        persuasionDialog = null
        dialogShown = false
    }

    /** 重新触发当前目标的卸载（倒计时归零时调用） */
    private fun retriggerUninstall() {
        val target = videoApps.getOrNull(uninstallCursor) ?: return
        persuasionDialog?.dismiss()
        persuasionDialog = null
        dialogShown = false
        // 不重置 uninstallAttempted，让它保持以便 onResume 重扫
        VideoAppDetector.uninstallApp(this, target.packageName)
    }

    /** 用户点击「好的，帮我清理」—— 逐个卸载所有检测到的应用 */
    private fun triggerBatchUninstall() {
        dialogShown = false
        uninstallAttempted = false
        persuasionApps.forEach { app ->
            VideoAppDetector.uninstallApp(this, app.packageName)
        }
        videoApps = persuasionApps
        uninstallCursor = 0
        tvQuote.visibility = View.VISIBLE
        btnNext.visibility = View.VISIBLE
        tvAllDone.visibility = View.GONE
        showQuote(0)
    }

    /** 从系统卸载弹窗返回后，自动推进到下一个目标应用 */
    private fun continueBatchUninstall() {
        if (!uninstallAttempted || videoApps.isEmpty()) return

        uninstallAttempted = false
        uninstallCursor++

        if (uninstallCursor < videoApps.size) {
            rootView.postDelayed({
                uninstallAttempted = true
                VideoAppDetector.uninstallApp(this, videoApps[uninstallCursor].packageName)
            }, 300)
        } else {
            isAutoUninstalling = false
            showAllDone()
        }
    }

    private fun showAllDone() {
        tvQuote.visibility = View.GONE
        btnNext.visibility = View.GONE
        tvAllDone.visibility = View.VISIBLE
    }

    /** cursor 推进后判断是否全部处理完 */
    private fun advanceOrFinish() {
        if (uninstallCursor >= videoApps.size) {
            showAllDone()
        } else {
            showQuote(currentQuoteIndex)
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
        findViewById<StarryBackgroundView>(R.id.starry_bg)?.resumeAnimation()
        continueBatchUninstall()
    }

    override fun onPause() {
        super.onPause()
        stopCountdown()
        persuasionDialog?.dismiss()
        persuasionDialog = null
        findViewById<StarryBackgroundView>(R.id.starry_bg)?.pauseAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullScreen()
    }

    override fun onBackPressed() {
        stopCountdown()
        finishWithAnimation()
    }
}
