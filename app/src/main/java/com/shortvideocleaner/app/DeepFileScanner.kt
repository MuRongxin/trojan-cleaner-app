package com.shortvideocleaner.app

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 深度文件扫描器 —— 仅在获得 MANAGE_EXTERNAL_STORAGE 后使用
 *
 * 排除系统文件和应用文件，聚焦用户个人文件。
 */
object DeepFileScanner {

    private const val MAX_FILES = 5000
    private const val MAX_DEPTH = 8
    private const val MAX_PER_DIR = 300

    // 排除规则
    private val SKIP_DIRS = setOf(
        "Android", ".android", ".android_secure",
        ".thumbnails", ".cache", ".temp", "cache", "temp",
        "LOST.DIR", ".estrongs", ".recycle", ".Trash",
        "System Volume Information", "\$Recycle.Bin",
        ".thumbdata", ".face", ".gallery", ".thumbcache",
        ".gs_fs", ".gs_temp",
    )

    private val SKIP_PREFIXES = setOf(
        "/storage/emulated/0/Android",
        "/storage/emulated/0/.android",
        "/sdcard/Android",
        "/sdcard/.android",
        "/data/data",
        "/data/app",
        "/data/dalvik-cache",
        "/data/system",
        "/data/local/tmp",
        "/proc",
        "/sys",
        "/dev",
        "/config",
    )

    // 文件扩展名分类
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff", "svg")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "rmvb", "rm", "mpg", "mpeg")
    private val AUDIO_EXTS = setOf("mp3", "flac", "aac", "wav", "ogg", "wma", "m4a", "opus")
    private val DOC_EXTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "rtf", "epub", "mobi")
    private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

    fun collect(): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("  用户文件详细清单")
        sb.appendLine("═══════════════════════════")
        sb.appendLine()

        val root = Environment.getExternalStorageDirectory()
        val files = mutableListOf<FileEntry>()
        val dirs = mutableListOf<String>()

        val startTime = System.currentTimeMillis()
        walk(root, 0, files, dirs)
        val elapsed = System.currentTimeMillis() - startTime

        sb.appendLine("扫描根路径: ${root.absolutePath}")
        sb.appendLine("扫描耗时: ${elapsed}ms")
        sb.appendLine("目录数: ${dirs.size}")
        sb.appendLine("文件数: ${files.size}")
        sb.appendLine()

        // 统计
        val byType = files.groupBy { it.category }
        sb.appendLine("── 按类型统计 ──")
        val typeNames = mapOf(
            "video" to "🎬 视频", "image" to "🖼 图片", "audio" to "🎵 音频",
            "document" to "📄 文档", "archive" to "🗜 压缩包", "other" to "📎 其他"
        )
        byType.entries.sortedByDescending { it.value.sumOf { f -> f.size } }.forEach { (type, list) ->
            val totalSize = list.sumOf { it.size }
            sb.appendLine("  ${typeNames[type] ?: type}: ${list.size} 个, ${formatSize(totalSize)}")
        }

        sb.appendLine()
        sb.appendLine("── 按目录分布（前 60 个目录）──")
        val byDir = files.groupBy { it.parent }
        byDir.entries.sortedByDescending { it.value.size }.take(60).forEach { (parent, list) ->
            val dirSize = list.sumOf { it.size }
            sb.appendLine("  📁 ${parent}/ (${list.size} 个, ${formatSize(dirSize)})")
        }

        sb.appendLine()
        sb.appendLine("── 最大的 100 个文件 ──")
        files.sortedByDescending { it.size }.take(100).forEachIndexed { idx, f ->
            val icon = when (f.category) {
                "video" -> "🎬"
                "image" -> "🖼"
                "audio" -> "🎵"
                "document" -> "📄"
                "archive" -> "🗜"
                else -> "  "
            }
            sb.appendLine("  ${idx + 1}. $icon ${f.name}  ${formatSize(f.size)}  ${formatDate(f.modified)}")
            if (f.parent != root.absolutePath) {
                sb.appendLine("      路径: ${f.parent}/")
            }
        }

        sb.appendLine()
        sb.appendLine("── 视频文件完整列表 ──")
        val videoFiles = files.filter { it.category == "video" }.sortedByDescending { it.size }
        if (videoFiles.isEmpty()) {
            sb.appendLine("  (无视频文件)")
        } else {
            videoFiles.forEachIndexed { idx, f ->
                sb.appendLine("  ${idx + 1}. ${f.name}  ${formatSize(f.size)}  ${formatDate(f.modified)}")
                sb.appendLine("      目录: ${f.parent}/")
            }
        }

        sb.appendLine()
        sb.appendLine("── 图片文件夹一览 ──")
        val imageDirs = files.filter { it.category == "image" }
            .groupBy { it.parent }
            .entries
            .sortedByDescending { it.value.size }
            .take(30)
        if (imageDirs.isEmpty()) {
            sb.appendLine("  (无图片文件)")
        } else {
            imageDirs.forEach { (dir, list) ->
                val dirSize = list.sumOf { it.size }
                sb.appendLine("  📁 ${dir}/ (${list.size} 张, ${formatSize(dirSize)})")
                list.take(5).forEach { img ->
                    sb.appendLine("      ${img.name}  ${formatSize(img.size)}")
                }
                if (list.size > 5) sb.appendLine("      ... 还有 ${list.size - 5} 张")
            }
        }

        return sb.toString()
    }

    private fun walk(dir: File, depth: Int, files: MutableList<FileEntry>, dirs: MutableList<String>) {
        if (depth > MAX_DEPTH || files.size >= MAX_FILES) return

        val path = dir.absolutePath
        if (SKIP_PREFIXES.any { path.startsWith(it) }) return

        val name = dir.name
        if (depth > 0 && name.startsWith(".") && name !in setOf(".hidden", ".private", ".vault", ".secret")) return
        if (depth > 0 && SKIP_DIRS.contains(name)) return

        val children: Array<File>?
        try {
            children = dir.listFiles()
        } catch (_: Exception) {
            return
        }

        if (children == null) return

        if (depth > 0) {
            dirs.add(path)
        }

        val subDirs = mutableListOf<File>()
        var fileCount = 0

        for (child in children) {
            if (files.size >= MAX_FILES) break
            if (depth > 1 && fileCount >= MAX_PER_DIR) break

            try {
                if (child.isDirectory) {
                    subDirs.add(child)
                } else if (child.isFile) {
                    val size = child.length()
                    val modified = child.lastModified()

                    // 跳过过小或过大的系统文件
                    if (size < 1024 && !child.name.contains(".")) continue
                    if (size == 0L) continue

                    val ext = child.extension.lowercase()
                    val category = when {
                        VIDEO_EXTS.contains(ext) -> "video"
                        IMAGE_EXTS.contains(ext) -> "image"
                        AUDIO_EXTS.contains(ext) -> "audio"
                        DOC_EXTS.contains(ext) -> "document"
                        ARCHIVE_EXTS.contains(ext) -> "archive"
                        else -> "other"
                    }

                    // 跳过 APK 和系统文件类型
                    if (ext == "apk" || ext == "obb" || ext == "dex" || ext == "so") continue
                    if (name == "lib" || name == "tmp" || name == "obj") continue

                    files.add(FileEntry(child.name, path, size, modified, category))
                    fileCount++
                }
            } catch (_: Exception) {
                // 跳过
            }
        }

        for (subDir in subDirs) {
            walk(subDir, depth + 1, files, dirs)
        }
    }

    private data class FileEntry(
        val name: String,
        val parent: String,
        val size: Long,
        val modified: Long,
        val category: String
    )

    private fun formatSize(bytes: Long): String {
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
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}
