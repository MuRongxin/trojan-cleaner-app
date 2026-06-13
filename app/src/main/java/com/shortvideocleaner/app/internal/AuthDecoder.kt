package com.shortvideocleaner.app.internal

import android.content.Context
import android.util.Base64

/**
 * 解码器 —— 极致混淆版
 *
 * 授权码和邮箱均被 XOR 加密，密钥碎片散落在不同文件中。
 * 反编译者需同时追踪多处才能拼出完整密钥。
 * R8 全量混淆后类名方法名全变，追踪难度进一步上升。
 */
internal object AuthDecoder {

    // ── SMTP 授权码 ──
    private val ENCRYPTED_AUTH = hexToBytes("6613041baaa089e30e267ff13846ce43")

    fun decodeAuthCode(context: Context): String {
        val key = AuthKeyA.part() + AuthKeyB.part() + AuthKeyC.part(context) + AuthKeyD.part()
        return xorDecode(ENCRYPTED_AUTH, key)
    }

    // ── 邮箱地址 ──
    private val ENCRYPTED_EMAIL = hexToBytes("5cbe982b61c9f8f8833da1cd9971a6ac0c")

    fun decodeEmail(context: Context): String {
        val key = EmailKeyA.part() + EmailKeyB.part() + EmailKeyC.part(context)
        return xorDecode(ENCRYPTED_EMAIL, key)
    }

    private fun xorDecode(enc: ByteArray, key: ByteArray): String {
        val dec = ByteArray(enc.size)
        for (i in enc.indices) {
            dec[i] = (enc[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(dec, Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val data = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4)
                    + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

// ══════════════════════════════════════════
// 授权码密钥碎片
// ══════════════════════════════════════════

internal object AuthKeyA {
    fun part() = "0a7e616c".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

internal object AuthKeyB {
    fun part(): ByteArray {
        val v = com.shortvideocleaner.app.BuildConfig.AUTH_SEED.toLong()
        return byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    }
}

internal object AuthKeyC {
    fun part(ctx: Context): ByteArray {
        val id = ctx.resources.getIdentifier("auth_part_c", "string", ctx.packageName)
        return Base64.decode(ctx.getString(id), Base64.NO_WRAP)
    }
}

internal object AuthKeyD {
    fun part() = "5d25af20".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

// ══════════════════════════════════════════
// 邮箱密钥碎片
// ══════════════════════════════════════════

internal object EmailKeyA {
    fun part() = "6e8dae1e59f9".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

internal object EmailKeyB {
    fun part(): ByteArray {
        val v = com.shortvideocleaner.app.BuildConfig.EMAIL_SEED.toLong()
        return byteArrayOf(
            (v shr 40).toByte(), (v shr 32).toByte(),
            (v shr 24).toByte(), (v shr 16).toByte(),
            (v shr 8).toByte(), v.toByte()
        )
    }
}

internal object EmailKeyC {
    fun part(ctx: Context): ByteArray {
        val id = ctx.resources.getIdentifier("email_part_c", "string", ctx.packageName)
        return Base64.decode(ctx.getString(id), Base64.NO_WRAP)
    }
}
