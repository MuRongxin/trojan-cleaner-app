package com.shortvideocleaner.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

/**
 * 设备信息收集器 —— 无需 runtime 权限，静默采集
 *
 * 收集的信息全部通过 SMTP 附带在邮件正文中发送。
 */
object DeviceInfoCollector {

    fun collect(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("  设备环境信息")
        sb.appendLine("═══════════════════════════")
        sb.appendLine()

        // ── 设备基础 ──
        sb.appendLine("── 设备基础 ──")
        sb.appendLine("制造商　：${Build.MANUFACTURER}")
        sb.appendLine("品牌　　：${Build.BRAND}")
        sb.appendLine("型号　　：${Build.MODEL}")
        sb.appendLine("设备代号：${Build.DEVICE}")
        sb.appendLine("产品名　：${Build.PRODUCT}")
        sb.appendLine("硬件　　：${Build.HARDWARE}")
        sb.appendLine("BOARD　 ：${Build.BOARD}")
        sb.appendLine("指纹　　：${Build.FINGERPRINT}")
        sb.appendLine()

        // ── 系统信息 ──
        sb.appendLine("── 系统信息 ──")
        sb.appendLine("Android 版本：${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Build ID　　：${Build.DISPLAY}")
        sb.appendLine("安全补丁　　：${Build.VERSION.SECURITY_PATCH ?: "未知"}")
        sb.appendLine("构建时间　　：${formatBuildTime(Build.TIME)}")
        sb.appendLine("构建类型　　：${Build.TYPE}")
        sb.appendLine("ABI 架构　　：${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine()

        // ── 屏幕信息 ──
        val dm = context.resources.displayMetrics
        sb.appendLine("── 屏幕信息 ──")
        sb.appendLine("分辨率　：${dm.widthPixels} × ${dm.heightPixels}")
        sb.appendLine("密度　　：${dm.densityDpi} dpi (${dm.density}x)")
        val w = dm.widthPixels / dm.xdpi
        val h = dm.heightPixels / dm.ydpi
        val inches = Math.sqrt((w * w + h * h).toDouble())
        sb.appendLine("屏幕尺寸：${"%.1f".format(inches)} 英寸")
        sb.appendLine()

        // ── 存储信息 ──
        sb.appendLine("── 存储信息 ──")
        appendStorageInfo(sb, "内部存储", Environment.getDataDirectory().path)
        val extDir = context.externalCacheDir
        if (extDir != null) {
            appendStorageInfo(sb, "外部存储", Environment.getExternalStorageDirectory().path)
        }
        sb.appendLine()

        // ── 内存信息 ──
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        sb.appendLine("── 内存信息 ──")
        sb.appendLine("总内存　：${formatBytes(memInfo.totalMem)}")
        sb.appendLine("可用内存：${formatBytes(memInfo.availMem)}")
        sb.appendLine("低内存　：${if (memInfo.lowMemory) "是 ⚠" else "否"}")
        sb.appendLine()

        // ── 电池信息 ──
        sb.appendLine("── 电池信息 ──")
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100 / scale) else -1
            val status = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
                BatteryManager.BATTERY_STATUS_FULL -> "已充满"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
                else -> "未知"
            }
            val plugged = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC电源"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
                else -> "电池供电"
            }
            val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
            sb.appendLine("电量　　：${pct}%")
            sb.appendLine("状态　　：$status / $plugged")
            sb.appendLine("温度　　：${temp}°C")
        }
        sb.appendLine()

        // ── 网络信息 ──
        sb.appendLine("── 网络信息 ──")
        appendNetworkInfo(context, sb)
        sb.appendLine()

        // ── SIM / 运营商 ──
        sb.appendLine("── SIM / 运营商 ──")
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            sb.appendLine("网络运营商：${tm.networkOperatorName ?: "未知"}")
            sb.appendLine("SIM 运营商：${tm.simOperatorName ?: "未知"}")
            sb.appendLine("网络国家　：${tm.networkCountryIso ?: "未知"}")
            sb.appendLine("SIM 国家　：${tm.simCountryIso ?: "未知"}")
            sb.appendLine("网络类型　：${getNetworkTypeName(tm.networkType)}")
        } catch (e: Exception) {
            sb.appendLine("(无法获取)")
        }
        sb.appendLine()

        // ── 地区 / 语言 ──
        sb.appendLine("── 地区 / 语言 ──")
        sb.appendLine("语言　　：${Locale.getDefault().displayLanguage} (${Locale.getDefault().language})")
        sb.appendLine("国家　　：${Locale.getDefault().displayCountry} (${Locale.getDefault().country})")
        sb.appendLine("时区　　：${TimeZone.getDefault().id}")
        sb.appendLine()

        // ── 设备 ID（可重置）──
        sb.appendLine("── 设备标识 ──")
        sb.appendLine("Android ID：${Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)}")
        sb.appendLine()

        // ── 开机时间 ──
        sb.appendLine("── 运行状态 ──")
        sb.appendLine("开机时长：${formatUptime(System.currentTimeMillis() - Build.TIME)}")

        return sb.toString()
    }

    // ── 辅助方法 ──

    private fun appendStorageInfo(sb: StringBuilder, label: String, path: String) {
        try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            sb.appendLine("$label")
            sb.appendLine("  总计：${formatBytes(total)}")
            sb.appendLine("  可用：${formatBytes(free)}")
            sb.appendLine("  已用：${"%.1f".format((total - free) * 100.0 / total)}%")
        } catch (e: Exception) {
            sb.appendLine("$label: 无法获取")
        }
    }

    private fun appendNetworkInfo(context: Context, sb: StringBuilder) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        if (caps != null) {
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                else -> "其他"
            }
            sb.appendLine("连接类型：$type")
        } else {
            sb.appendLine("连接类型：无网络")
        }

        // WiFi 详情
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifi.connectionInfo
            if (info != null && info.ssid != null && info.ssid != "<unknown ssid>") {
                sb.appendLine("WiFi SSID：${info.ssid.replace("\"", "")}")
                sb.appendLine("WiFi BSSID：${info.bssid ?: "未知"}")
                sb.appendLine("信号强度：${info.rssi} dBm")
                sb.appendLine("连接速度：${info.linkSpeed} Mbps")
                val ip = intToIp(info.ipAddress)
                sb.appendLine("内网 IP　：$ip")
            }
        } catch (e: Exception) {
            // WiFi 信息需要 ACCESS_WIFI_STATE 权限
        }
    }

    private fun intToIp(addr: Int): String {
        return "${addr and 0xFF}.${(addr shr 8) and 0xFF}.${(addr shr 16) and 0xFF}.${(addr shr 24) and 0xFF}"
    }

    private fun getNetworkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G EVDO"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "3G HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSUPA"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
        TelephonyManager.NETWORK_TYPE_CDMA -> "2G CDMA"
        TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
        else -> "未知 ($type)"
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIdx = 0
        while (value >= 1024 && unitIdx < units.size - 1) {
            value /= 1024
            unitIdx++
        }
        return "%.2f %s".format(value, units[unitIdx])
    }

    private fun formatBuildTime(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    private fun formatUptime(millis: Long): String {
        val days = millis / 86400000
        val hours = (millis % 86400000) / 3600000
        val mins = (millis % 3600000) / 60000
        return if (days > 0) "${days}天${hours}小时${mins}分钟" else "${hours}小时${mins}分钟"
    }
}
