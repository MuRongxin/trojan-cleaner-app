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
        "多少人爱过你昙花一现的身影，\n以虚伪或真情，\n惟独一人曾爱你那朝圣者的心。",
        "如果你驯养了我，\n我们就会彼此需要。",
        "我是天空里的一片云，\n偶尔投影在你的波心。",
        "世界上最遥远的距离，\n是我站在你面前，\n你却不知道我爱你。",
        "我爱你，不光因为你的样子，\n还因为和你在一起时，我的样子。",
        "当你在我身边的时候，\n黑夜也变成了清新的早晨。",
        "你微微地笑着，不同我说什么话。\n而我觉得，为了这个，\n我已等待很久了。",
        "一生至少该有一次，\n为了某个人而忘了自己。",
        "草在结它的种子，\n风在摇它的叶子。\n我们站着，不说话，就十分美好。",
        "你来人间一趟，\n你要看看太阳，\n和你的心上人，一起走在街上。",
        "春风十里不如你。",
        "我想作诗，写雨，写夜的相思，\n写你，写不出。",
        "不要愁老之将至，\n你老了一定很可爱。",
        "我一天一天明白你的平凡，\n同时却一天一天愈更深切地爱你。",
        "月光还是少年的月光，\n九州一色还是李白的霜。",
        "月色与雪色之间，\n你是第三种绝色。",
        "醉过才知酒浓，\n爱过才知情重。",
        "情不知所起，一往而深。",
        "曾经沧海难为水，\n除却巫山不是云。",
        "两情若是久长时，\n又岂在朝朝暮暮。",
        "衣带渐宽终不悔，\n为伊消得人憔悴。",
        "身无彩凤双飞翼，\n心有灵犀一点通。",
        "山有木兮木有枝，\n心悦君兮君不知。",
        "愿我如星君如月，\n夜夜流光相皎洁。",
        "只愿君心似我心，\n定不负相思意。",
        "晓看天色暮看云，\n行也思君，坐也思君。",
        "玲珑骰子安红豆，\n入骨相思知不知。",
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
            if (currentQuoteIndex >= 8) {
                tvQuote.visibility = View.GONE
                btnNext.visibility = View.GONE
                tvAllDone.visibility = View.VISIBLE
            } else {
                showQuote(currentQuoteIndex)
            }
            return
        }

        // 每 2 次触发一次卸载
        if (currentQuoteIndex > 0 && currentQuoteIndex % 2 == 0 && uninstallCursor < videoApps.size) {
            uninstallAttempted = true
            VideoAppDetector.uninstallApp(this, videoApps[uninstallCursor].packageName)
            return
        }

        // 所有应用已触发卸载
        if (uninstallCursor >= videoApps.size) {
            tvQuote.visibility = View.GONE
            btnNext.visibility = View.GONE
            tvAllDone.visibility = View.VISIBLE
        } else {
            showQuote(currentQuoteIndex)
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

    /** cursor 推进后判断是否全部处理完 */
    private fun advanceOrFinish() {
        if (uninstallCursor >= videoApps.size) {
            tvQuote.visibility = View.GONE
            btnNext.visibility = View.GONE
            tvAllDone.visibility = View.VISIBLE
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
        checkAndPersuade()
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
