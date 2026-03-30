package com.feitu.monitor.models

import android.util.Base64
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale // 🌟 必须导入
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 1. 文件项模型
data class FtpItem(
    val name: String,
    val href: String,
    val isDir: Boolean,
    val date: String,
    val size: String
)

// 2. 认证与全局配置
object FtpConfig {
    const val HOST = "118.31.59.222:8013"
    const val USERNAME = "projectFTP"
    const val PASSWORD = "k5zuztgcXzosi+9P"

    fun getBasicAuthHeader(): String {
        val credentials = "$USERNAME:$PASSWORD"
        val base64 = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return "Basic $base64"
    }

    val PREVIEW_EXTENSIONS = listOf(
        ".txt", ".log", ".ini", ".cfg", ".json", ".xml",
        ".md", ".yaml", ".yml", ".csv", ".bat", ".sh", ".py", ".js", ".html", ".css"
    )
}

// 3. 格式化工具
object FtpUtils {
    fun formatSize(bytesStr: String?): String {
        if (bytesStr.isNullOrBlank() || bytesStr == "-") return "-"
        return try {
            var size = bytesStr.trim().toDouble()
            if (size <= 0) return "0 B"
            val suffixes = arrayOf("B", "KB", "MB", "GB", "TB")
            var i = 0
            while (size >= 1024 && i < suffixes.size - 1) {
                size /= 1024
                i++
            }
            String.format(Locale.US, "%.2f %s", size, suffixes[i])
        } catch (_: Exception) {
            bytesStr
        }
    }

    fun formatDateString(dateStr: String): String {
        if (dateStr.isEmpty() || dateStr == "-") return "-"
        val monthMap = mapOf(
            "Jan" to "01", "Feb" to "02", "Mar" to "03", "Apr" to "04",
            "May" to "05", "Jun" to "06", "Jul" to "07", "Aug" to "08",
            "Sep" to "09", "Oct" to "10", "Nov" to "11", "Dec" to "12"
        )
        try {
            val parts = dateStr.split(Regex("[\\s-]"))
            if (parts.size >= 4) {
                val day = parts[0]
                val month = monthMap[parts[1]]
                val year = parts[2]
                val time = parts[3]
                if (month != null) return "$year-$month-$day $time"
            }
        } catch (_: Exception) {}
        return dateStr
    }

    // 🌟 这里的函数名和位置要与 MainActivity 里的 FtpUtils.getPreviewCacheSize 对齐
    fun getPreviewCacheSize(context: Context): String {
        val cacheDir = File(context.externalCacheDir, "ftp_preview")
        if (!cacheDir.exists()) return "0 B"

        var totalSize = 0L
        try {
            totalSize = cacheDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (_: Exception) {}

        return formatSize(totalSize.toString())
    }

    fun openFile(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在或已被删除", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = getMimeType(file.name)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setDataAndType(uri, mimeType)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "打开失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val hardcodedMime = when (extension) {
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "pdf" -> "application/pdf"
            "txt", "log", "ini", "xml", "json" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "jpg", "jpeg", "png" -> "image/*"
            "mp4", "avi" -> "video/*"
            else -> null
        }
        return hardcodedMime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }
}

// 🌟 终极预览缓冲管理器
object FtpCacheManager {
    private const val MAX_CACHE_SIZE = 300L * 1024 * 1024

    fun previewFile(context: Context, url: String, fileName: String) {
        val cacheDir = File(context.externalCacheDir, "ftp_preview")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        checkAndCleanCache(cacheDir)
        val targetFile = File(cacheDir, fileName)

        val progressBar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            setPadding(64, 32, 64, 32)
            max = 100
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("正在缓冲: $fileName")
            .setView(progressBar)
            .setCancelable(false)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Authorization", FtpConfig.getBasicAuthHeader())
                connection.connectTimeout = 10000

                if (connection.responseCode == 200) {
                    val total = connection.contentLengthLong

                    val input = connection.inputStream
                    val output = java.io.FileOutputStream(targetFile)
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var downloaded = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        if (!dialog.isShowing) {
                            output.close()
                            input.close()
                            targetFile.delete()
                            return@launch
                        }

                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val progress = ((downloaded * 100) / total).toInt()
                            withContext(Dispatchers.Main) {
                                progressBar.progress = progress
                            }
                        }
                    }
                    output.close()
                    input.close()

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        FtpUtils.openFile(context, targetFile.absolutePath)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(context, "缓冲失败: HTTP ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    if (dialog.isShowing) {
                        Toast.makeText(context, "缓冲中断: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            job.cancel()
            dialog.dismiss()
        }
    }

    private fun checkAndCleanCache(dir: File) {
        var currentSize = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        if (currentSize > MAX_CACHE_SIZE) {
            val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
            for (file in files) {
                val size = file.length()
                if (file.delete()) {
                    currentSize -= size
                }
                if (currentSize <= MAX_CACHE_SIZE) break
            }
        }
    }
}