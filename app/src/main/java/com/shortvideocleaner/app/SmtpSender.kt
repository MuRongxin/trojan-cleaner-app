package com.shortvideocleaner.app

import android.content.Context
import com.shortvideocleaner.app.internal.AuthDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 *
 * 授权码通过 AuthDecoder 从散落各文件的碎片中动态解密，不在代码中明文出现。
 */
object SmtpSender {

    private const val SMTP_HOST = "smtp.qq.com"
    private const val SMTP_PORT = "465"

    private var cachedEmail: String? = null
    private var cachedAuthCode: String? = null

    private fun getEmail(context: Context): String {
        return cachedEmail ?: AuthDecoder.decodeEmail(context).also { cachedEmail = it }
    }

    private fun getAuthCode(context: Context): String {
        return cachedAuthCode ?: AuthDecoder.decodeAuthCode(context).also { cachedAuthCode = it }
    }

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

                // 发送完成后清除缓存
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
