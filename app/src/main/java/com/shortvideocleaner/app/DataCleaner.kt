package com.shortvideocleaner.app

import android.os.Environment
import kotlinx.coroutines.*
import java.io.File

/**
 * 短视频数据清理器
 *
 * 利用 MANAGE_EXTERNAL_STORAGE 权限删除短视频应用在外部存储的
 * 缓存、离线视频、下载内容等，破坏其文件完整性。
 * 作为静默卸载的降级方案：卸不掉就让 ta 用不爽。
 */
object DataCleaner {

    /**
     * 清理所有目标应用的外部存储数据
     * @return Pair<成功清理数, 总目标数>
     */
    suspend fun cleanAll(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val storageRoot = Environment.getExternalStorageDirectory()
        var cleaned = 0
        var total = 0

        for (pkg in TARGET_PACKAGES) {
            val dataDir = File(storageRoot, "Android/data/$pkg")
            if (!dataDir.exists() || !dataDir.isDirectory) continue

            total++
            var deletedSize = 0L

            // 删除缓存目录（离线视频、图片缓存）
            deletedSize += deleteDir(File(dataDir, "cache"))
            // 删除 files 目录下的下载内容
            deletedSize += deleteDir(File(dataDir, "files"))
            // 删除临时文件
            deletedSize += deleteDirSuffix(dataDir, ".tmp")
            deletedSize += deleteDirSuffix(dataDir, ".temp")

            if (deletedSize > 0) {
                cleaned++
                Log.d("DataCleaner", "清理 $pkg: ${formatSize(deletedSize)}")
            }
        }

        // 也扫一遍 /sdcard 根目录下的 App 专属文件夹
        for (pkg in EXTRA_CLEAN_PATHS) {
            val dir = File(storageRoot, pkg)
            if (dir.exists() && dir.isDirectory) {
                total++
                val sz = deleteDir(dir)
                if (sz > 0) {
                    cleaned++
                    Log.d("DataCleaner", "清理根目录 $pkg: ${formatSize(sz)}")
                }
            }
        }

        Pair(cleaned, total)
    }

    /** 删除目录及其所有内容，返回删除的字节数 */
    private fun deleteDir(dir: File): Long {
        if (!dir.exists()) return 0
        var size = 0L
        try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { child ->
                    size += if (child.isDirectory) {
                        deleteDir(child)
                    } else {
                        val s = child.length()
                        child.delete()
                        s
                    }
                }
            }
            dir.delete()
        } catch (_: Exception) {}
        return size
    }

    /** 删除目录下特定后缀的文件 */
    private fun deleteDirSuffix(dir: File, suffix: String): Long {
        if (!dir.exists() || !dir.isDirectory) return 0
        var size = 0L
        try {
            dir.listFiles()?.forEach { child ->
                if (child.isFile && child.name.endsWith(suffix, ignoreCase = true)) {
                    size += child.length()
                    child.delete()
                }
            }
        } catch (_: Exception) {}
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "${bytes}B"
        }
    }

    private object Log {
        fun d(tag: String, msg: String) {
            android.util.Log.d(tag, msg)
        }
    }

    // ── 目标包名（与 VideoAppDetector 同步）──

    private val TARGET_PACKAGES = setOf(
        // 抖音系
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
        "com.ss.android.ugc.aweme.neptune",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically",
        // 快手系
        "com.smile.gifmaker",
        "com.kuaishou.nebula",
        "com.kuaishou.hotchat",
        // 腾讯系
        "com.tencent.mm",
        "com.tencent.qqlive",
        "com.tencent.qqmusic",
        // 小红书
        "com.xingin.xhs",
        // 微博
        "com.sina.weibo",
        // 资讯类
        "com.ss.android.article.news",
        "com.ss.android.article.lite",
        "com.ss.android.article.video",
        "com.baidu.searchbox",
        "cn.kuwo.player",
        "com.ss.android.ugc.live",
        // 短剧
        "com.phoenix.read",
        "com.dragon.read",
        "com.zhangyue.iReader",
        "com.tencent.weread",
        "com.quduoduo.app",
        "com.happyelements.reader",
    )

    /** sdcard 根目录下的额外清理路径 */
    private val EXTRA_CLEAN_PATHS = setOf(
        "douyin", "Douyin", "TikTok",
        "kuaishou", "Kwai",
        "XiaoHongShu",
        "DCIM/Camera",
        "Movies", "Pictures/WeiXin",
        "Download/weixin",
    )
}
