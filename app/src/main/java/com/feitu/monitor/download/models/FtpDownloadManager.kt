package com.feitu.monitor.download.models

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.content.edit
import com.feitu.monitor.cloud.models.FtpConfig
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object FtpDownloadManager {
    val tasks = mutableListOf<DownloadTask>()
    var onStateChanged: (() -> Unit)? = null

    private var isLoaded = false

    fun loadTasks(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences("FeituDownloads", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("tasks_json", null)
        if (!jsonString.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val statusStr = obj.getString("status")
                    val status = DownloadStatus.valueOf(statusStr)
                    val safeStatus = if (status == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED else status

                    val task = DownloadTask(
                        id = obj.getString("id"),
                        fileName = obj.getString("fileName"),
                        url = obj.getString("url"),
                        savePath = obj.getString("savePath"),
                        progress = obj.getInt("progress"),
                        totalBytes = obj.getLong("totalBytes"),
                        downloadedBytes = obj.getLong("downloadedBytes"),
                        status = safeStatus,
                        speed = if (safeStatus == DownloadStatus.COMPLETED) "已完成" else "已暂停"
                    )
                    tasks.add(task)
                }
            } catch (e: Exception) {
                Log.e("FeituDownload", "加载记录失败: ${e.message}")
            }
        }
        isLoaded = true
    }

    private fun saveTasks(context: Context) {
        val prefs = context.getSharedPreferences("FeituDownloads", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("fileName", task.fileName)
                put("url", task.url)
                put("savePath", task.savePath)
                put("progress", task.progress)
                put("totalBytes", task.totalBytes)
                put("downloadedBytes", task.downloadedBytes)
                put("status", task.status.name)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString("tasks_json", jsonArray.toString()) }
    }

    fun startOrResumeDownload(url: String, fileName: String, context: Context) {
        loadTasks(context)
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val savePath = File(downloadDir, fileName).absolutePath

        var task = tasks.find { it.savePath == savePath }
        if (task == null) {
            task = DownloadTask(
                id = System.currentTimeMillis().toString(),
                fileName = fileName,
                url = url,
                savePath = savePath
            )
            tasks.add(0, task)
        }

        if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.COMPLETED) return

        task.status = DownloadStatus.DOWNLOADING
        saveTasks(context)
        onStateChanged?.invoke()

        val appContext = context.applicationContext
        task.job = CoroutineScope(Dispatchers.IO).launch {
            executeDownload(task, appContext)
        }
    }

    fun promoteCacheToDownload(context: Context, fileName: String, url: String): Boolean {
        val cacheDir = File(context.externalCacheDir, "ftp_preview")
        val cacheFile = File(cacheDir, fileName)

        if (!cacheFile.exists() || cacheFile.length() <= 0) return false

        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadDir, fileName)

            if (cacheFile.renameTo(destFile)) {
                val task = DownloadTask(
                    id = System.currentTimeMillis().toString(),
                    fileName = fileName,
                    url = url,
                    savePath = destFile.absolutePath,
                    progress = 100,
                    totalBytes = destFile.length(),
                    downloadedBytes = destFile.length(),
                    status = DownloadStatus.COMPLETED,
                    speed = "已完成"
                )
                tasks.removeAll { it.savePath == destFile.absolutePath }
                tasks.add(0, task)
                saveTasks(context)
                onStateChanged?.invoke()
                return true
            }
        } catch (e: Exception) {
            Log.e("FeituDownload", "缓存转正失败: ${e.message}")
        }
        return false
    }

    fun pauseDownload(task: DownloadTask, context: Context) {
        if (task.status == DownloadStatus.DOWNLOADING) {
            task.job?.cancel()
            task.status = DownloadStatus.PAUSED
            task.speed = "已暂停"
            saveTasks(context)
            onStateChanged?.invoke()
        }
    }

    fun deleteDownload(task: DownloadTask, context: Context, deleteFile: Boolean = true) {
        task.job?.cancel()
        tasks.remove(task)
        if (deleteFile) {
            val file = File(task.savePath)
            if (file.exists()) file.delete()
        }
        saveTasks(context)
        onStateChanged?.invoke()
    }

    private suspend fun executeDownload(task: DownloadTask, context: Context) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var randomAccessFile: RandomAccessFile? = null

        try {
            val file = File(task.savePath)
            // 🌟 阻塞调用现在在 Dispatchers.IO 中是安全的
            val downloadedBytes = if (file.exists()) file.length() else 0L
            task.downloadedBytes = downloadedBytes

            connection = (URL(task.url).openConnection() as HttpURLConnection).apply {
                useCaches = false
                setRequestProperty("Connection", "close")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Authorization", FtpConfig.getBasicAuthHeader())
                if (downloadedBytes > 0) setRequestProperty("Range", "bytes=$downloadedBytes-")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode

            if (responseCode == 416) {
                updateTaskCompletion(task, file.length(), context)
                return@withContext
            }

            if (responseCode == 206 || responseCode == 200) {
                val contentLength = connection.contentLengthLong

                if (responseCode == 200) {
                    task.totalBytes = if (contentLength > 0) contentLength else 0L
                    task.downloadedBytes = 0L
                } else {
                    task.totalBytes = downloadedBytes + (if (contentLength > 0) contentLength else 0L)
                }

                randomAccessFile = RandomAccessFile(file, "rw").apply { seek(task.downloadedBytes) }
                val inputStream = connection.inputStream
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var lastTime = System.currentTimeMillis()
                var bytesSinceLast = 0L

                // 🌟 这里的循环读写不再报警告
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (task.job?.isCancelled == true) break

                    randomAccessFile.write(buffer, 0, bytesRead)
                    task.downloadedBytes += bytesRead
                    bytesSinceLast += bytesRead

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTime > 500) {
                        updateProgressUI(task, bytesSinceLast, currentTime - lastTime)
                        bytesSinceLast = 0L
                        lastTime = currentTime
                    }
                }

                if (task.totalBytes <= 0) task.totalBytes = task.downloadedBytes
                if (task.downloadedBytes >= task.totalBytes && task.totalBytes > 0) {
                    updateTaskCompletion(task, task.totalBytes, context)
                }
            } else {
                failTask(task, "HTTP Error: $responseCode", context)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                failTask(task, "下载失败", context)
                Log.e("FeituDownload", "Error: ${e.message}")
            }
        } finally {
            // 🌟 关闭流也是阻塞操作，现在在 IO 上下文中
            try { randomAccessFile?.close() } catch (_: Exception) {}
            connection?.disconnect()
            withContext(Dispatchers.Main) { onStateChanged?.invoke() }
        }
    }

    private suspend fun updateProgressUI(task: DownloadTask, bytesSinceLast: Long, timeDiff: Long) {
        if (task.totalBytes > 0) {
            task.progress = ((task.downloadedBytes * 100) / task.totalBytes).toInt()
        }
        val speedKbps = (bytesSinceLast / (timeDiff / 1000.0)) / 1024
        task.speed = if (speedKbps > 1024) {
            String.format(Locale.US, "%.1f MB/s", speedKbps / 1024)
        } else {
            String.format(Locale.US, "%.1f KB/s", speedKbps)
        }
        withContext(Dispatchers.Main) { onStateChanged?.invoke() }
    }

    private suspend fun failTask(task: DownloadTask, errorMsg: String, context: Context) {
        task.status = DownloadStatus.FAILED
        task.speed = errorMsg
        withContext(Dispatchers.IO) { saveTasks(context) }
        withContext(Dispatchers.Main) { onStateChanged?.invoke() }
    }

    private suspend fun updateTaskCompletion(task: DownloadTask, size: Long, context: Context) {
        task.status = DownloadStatus.COMPLETED
        task.progress = 100
        task.totalBytes = size
        task.downloadedBytes = size
        task.speed = "已完成"

        // 涉及持久化操作，建议在 IO 线程
        withContext(Dispatchers.IO) { saveTasks(context) }

        // 刷新 UI 必须在主线程
        withContext(Dispatchers.Main) { onStateChanged?.invoke() }
    }
}