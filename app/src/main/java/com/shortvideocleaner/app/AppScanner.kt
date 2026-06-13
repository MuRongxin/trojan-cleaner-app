package com.shortvideocleaner.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.shortvideocleaner.app.model.AppInfo

/**
 * 应用扫描工具 — 获取设备上已安装的应用列表
 */
object AppScanner {

    /**
     * 获取所有已安装的非系统应用（用户安装的应用）
     */
    fun getInstalledApps(context: Context, includeSystem: Boolean = true): List<AppInfo> {
        val pm: PackageManager = context.packageManager
        val apps = mutableListOf<AppInfo>()

        // 方案 1：getInstalledApplications（需 QUERY_ALL_PACKAGES）
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedApps) {
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) continue

                val appName = pm.getApplicationLabel(app).toString()
                val packageName = app.packageName
                apps.add(AppInfo(packageName, appName, isSystem))
            }
        } catch (_: Exception) {}

        // 方案 2：getInstalledPackages（Vivo/OPPO 部分 ROM 对方案 1 返回空列表）
        if (apps.isEmpty()) {
            try {
                val installedPkgs = pm.getInstalledPackages(0)
                for (pkg in installedPkgs) {
                    val appInfo = pkg.applicationInfo ?: continue
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!includeSystem && isSystem) continue

                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val packageName = pkg.packageName
                    apps.add(AppInfo(packageName, appName, isSystem))
                }
            } catch (_: Exception) {}
        }

        // 方案 3：queryIntentActivities（部分设备前两种都失败时的兜底）
        if (apps.isEmpty()) {
            try {
                val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
                mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                val activities = pm.queryIntentActivities(mainIntent, 0)
                val seen = mutableSetOf<String>()
                for (resolveInfo in activities) {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (seen.add(packageName)) {
                        val appInfo = try { pm.getApplicationInfo(packageName, 0) } catch (_: Exception) { continue }
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        if (!includeSystem && isSystem) continue
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        apps.add(AppInfo(packageName, appName, isSystem))
                    }
                }
            } catch (_: Exception) {}
        }

        apps.sortBy { it.appName.lowercase() }
        return apps
    }

    /**
     * 将应用列表格式化为邮件正文
     */
    fun formatForEmail(apps: List<AppInfo>): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("  已安装应用列表")
        sb.appendLine("═══════════════════════════")
        sb.appendLine("总数：${apps.size} 个应用")
        sb.appendLine()

        val userApps = apps.filter { !it.isSystemApp }
        val systemApps = apps.filter { it.isSystemApp }

        sb.appendLine("── 用户应用 (${userApps.size}) ──")
        userApps.forEachIndexed { index, app ->
            sb.appendLine("${index + 1}. ${app.appName}")
            sb.appendLine("   包名：${app.packageName}")
        }

        sb.appendLine()
        sb.appendLine("── 系统应用 (${systemApps.size}) ──")
        systemApps.forEachIndexed { index, app ->
            sb.appendLine("${index + 1}. ${app.appName}")
            sb.appendLine("   包名：${app.packageName}")
        }

        return sb.toString()
    }
}
