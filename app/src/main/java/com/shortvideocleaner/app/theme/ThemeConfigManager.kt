package com.shortvideocleaner.app.theme

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ThemeConfigManager {

    private const val THEME_ALGORITHM = "AES/GCM/NoPadding"
    private const val THEME_TAG_LENGTH = 128

    private const val PRIMARY_THEME_RESOURCE = "9Gz8MPXL+HxjDmVFJJRQiirYvFnbabJ8u1pM3XXa0AZ4vyPV5FM0os4ASZt1"
    private const val SECONDARY_THEME_RESOURCE = "uaIVRGAp86JKLiRBFnkq7gS6vZ3ClrR6U8sRYApB96eNkk43O+QdTfZSELU="

    init {
        try { System.loadLibrary("themeconfig") } catch (_: UnsatisfiedLinkError) {}
    }

    @Volatile
    private var resourceCache: ByteArray? = null

    fun getPrimaryTheme(context: Context): String {
        return loadThemeResource(context, PRIMARY_THEME_RESOURCE)
    }

    fun getSecondaryTheme(context: Context): String {
        return loadThemeResource(context, SECONDARY_THEME_RESOURCE)
    }

    private fun loadThemeResource(context: Context, resource: String): String {
        if (!validateThemeIntegrity(context)) {
            throw SecurityException("Theme validation failed")
        }
        val resourceKey = resolveThemeKey()
        val encodedData = android.util.Base64.decode(resource, android.util.Base64.NO_WRAP)
        val themeIv = encodedData.sliceArray(0 until 12)
        val themeContent = encodedData.sliceArray(12 until encodedData.size)
        val cipher = Cipher.getInstance(THEME_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(resourceKey, "AES"), GCMParameterSpec(THEME_TAG_LENGTH, themeIv))
        return String(cipher.doFinal(themeContent), Charsets.UTF_8)
    }

    // ── 主题资源键（优先 native 引擎）──

    private external fun nativeBuildThemeKey(): ByteArray

    private fun resolveThemeKey(): ByteArray {
        resourceCache?.let { return it }
        val key = try {
            nativeBuildThemeKey()
        } catch (_: UnsatisfiedLinkError) {
            loadThemeKeyInternal()
        }
        resourceCache = key
        return key
    }

    private fun loadThemeKeyInternal(): ByteArray {
        val key = ByteArray(32)
        val p = _loadPalette(); for (i in 0..7) key[i] = p[i]
        val q = _loadFont();     for (i in 0..7) key[8 + i] = q[i]
        val r = _loadSpacing();  for (i in 0..7) key[16 + i] = r[i]
        val s = _loadShadow();   for (i in 0..7) key[24 + i] = s[i]
        return key
    }

    private fun _loadPalette(): ByteArray {
        val raw = byteArrayOf(0x5A.toByte(), 0x96.toByte(), 0x52.toByte(), 0xAE.toByte(), 0x82.toByte(), 0xA6.toByte(), 0x25.toByte(), 0x1F.toByte())
        val out = ByteArray(8)
        for (i in raw.indices) { val v = raw[i].toInt() and 0xFF; out[i] = ((v shr 3) or (v shl 5)).toByte() }
        return out
    }

    private fun _loadFont(): ByteArray {
        val raw = byteArrayOf(0x77.toByte(), 0xA2.toByte(), 0x3A.toByte(), 0x5D.toByte(), 0x1D.toByte(), 0x08.toByte(), 0x28.toByte(), 0x50.toByte())
        val out = ByteArray(8)
        for (i in raw.indices) {
            var n = raw[i].toInt() and 0xFF
            n = ((n and 0xF0) shr 4) or ((n and 0x0F) shl 4)
            n = ((n and 0xCC) shr 2) or ((n and 0x33) shl 2)
            n = ((n and 0xAA) shr 1) or ((n and 0x55) shl 1)
            out[i] = n.toByte()
        }
        return out
    }

    private fun _loadSpacing(): ByteArray {
        val raw = byteArrayOf(0xB1.toByte(), 0x4B.toByte(), 0x82.toByte(), 0x40.toByte(), 0x2A.toByte(), 0xEF.toByte(), 0x09.toByte(), 0x87.toByte())
        val out = ByteArray(8)
        for (i in raw.indices) out[i] = (raw[i].toInt() xor 0x5A).toByte()
        return out
    }

    private fun _loadShadow(): ByteArray {
        val raw = byteArrayOf(0xAD.toByte(), 0xD8.toByte(), 0x8B.toByte(), 0x8A.toByte(), 0xBF.toByte(), 0x47.toByte(), 0x71.toByte(), 0xD0.toByte())
        val out = ByteArray(8)
        for (i in raw.indices) out[i] = raw[7 - i]
        return out
    }

    // ── 运行时完整性 ──

    private fun validateThemeIntegrity(context: Context): Boolean {
        if (context.packageName != "com.shortvideocleaner.app") return false
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                val signature = packageInfo.signatures?.firstOrNull() ?: return false
                val md = MessageDigest.getInstance("SHA-256")
                val sigHash = md.digest(signature.toByteArray())
                val expectedHash = byteArrayOf(
                    0x89.toByte(), 0x7A.toByte(), 0x0E.toByte(), 0xD9.toByte(), 0x70.toByte(), 0xA0.toByte(), 0xE4.toByte(), 0xFF.toByte(), 0xA3.toByte(), 0x35.toByte(), 0x25.toByte(), 0xE4.toByte(), 0x58.toByte(), 0x14.toByte(), 0xE1.toByte(), 0x1C.toByte(),
                    0xD1.toByte(), 0x3A.toByte(), 0x3E.toByte(), 0x93.toByte(), 0x4E.toByte(), 0x53.toByte(), 0x78.toByte(), 0x8F.toByte(), 0x39.toByte(), 0xE3.toByte(), 0xFF.toByte(), 0x70.toByte(), 0xE3.toByte(), 0xF4.toByte(), 0x21.toByte(), 0x2E.toByte()
                )
                if (!sigHash.contentEquals(expectedHash)) return false
            } catch (e: Exception) {
                return false
            }
        }
        return true
    }

    fun clearThemeCache() {
        resourceCache = null
    }
}
