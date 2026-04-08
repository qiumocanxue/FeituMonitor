package com.feitu.monitor.download.models

// 下载状态枚举
enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

// 下载任务数据模型
data class DownloadTask(
    val id: String,
    val fileName: String,
    val url: String,
    val savePath: String,
    var progress: Int = 0,
    var totalBytes: Long = 0,
    var downloadedBytes: Long = 0,
    var speed: String = "0 KB/s",
    var status: DownloadStatus = DownloadStatus.PENDING,
    @Transient var job: kotlinx.coroutines.Job? = null
)