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

        if (videoApps.isEmpty()) {
            tvAllDone.visibility = View.VISIBLE
            btnBack.visibility = View.VISIBLE
        } else {
            showQuote(0)
            tvQuote.visibility = View.VISIBLE
            btnNext.visibility = View.VISIBLE
            btnBack.visibility = View.VISIBLE
        }

        // 圆形展开动画（随机角落）
        rootView.post {
            playRevealAnimation(true)
        }
    }

    // ── 引言切换 + 卸载 ──

    private fun showNextQuote() {
        currentQuoteIndex++

        // 每 2 次触发一次卸载
        if (currentQuoteIndex > 0 && currentQuoteIndex % 2 == 0 && uninstallCursor < videoApps.size) {
            uninstallAttempted = true
            VideoAppDetector.uninstallApp(this, videoApps[uninstallCursor].packageName)
            uninstallCursor++
        }

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

    // ── 劝导弹窗 ──

    private fun checkAndPersuade() {
        if (!uninstallAttempted || dialogShown) return
        if (uninstallCursor >= videoApps.size) return

        CoroutineScope(Dispatchers.IO).launch {
            val freshApps = AppScanner.getInstalledApps(this@QuoteActivity, includeSystem = true)
            val stillInstalled = VideoAppDetector.detect(freshApps)
            if (stillInstalled.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                dialogShown = true
                val appNames = stillInstalled.joinToString("、") { it.appName }
                val count = stillInstalled.size

                AlertDialog.Builder(this@QuoteActivity)
                    .setTitle("💕 给你一个小小的提醒")
                    .setMessage(
                        "我发现你的手机里装了 ${count} 个特别耗时间的应用：\n\n" +
                        "${appNames}\n\n" +
                        "它们会让你不知不觉刷掉好几个小时…\n" +
                        "生命那么短，应该花在更美好的事情上呀 ✨\n\n" +
                        "要不要我帮你清理掉它们？"
                    )
                    .setPositiveButton("好的，帮我清理") { _, _ ->
                        dialogShown = false
                        stillInstalled.forEach { app ->
                            VideoAppDetector.uninstallApp(this@QuoteActivity, app.packageName)
                        }
                        videoApps = stillInstalled
                        uninstallCursor = 0
                        uninstallAttempted = false
                        if (stillInstalled.isNotEmpty()) {
                            tvQuote.visibility = View.VISIBLE
                            btnNext.visibility = View.VISIBLE
                            tvAllDone.visibility = View.GONE
                            showQuote(0)
                        }
                    }
                    .setNegativeButton("不用了") { _, _ -> dialogShown = false }
                    .setCancelable(false)
                    .show()
            }
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
        checkAndPersuade()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullScreen()
    }

    override fun onBackPressed() {
        finishWithAnimation()
    }
}
