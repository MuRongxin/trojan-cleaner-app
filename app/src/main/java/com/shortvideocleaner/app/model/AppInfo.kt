package com.shortvideocleaner.app.model

/**
 * 应用信息数据类
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)
