package com.feitu.monitor.remote.models

import com.feitu.monitor.common.models.IFileItem

data class RemoteFile(
    val name: String,
    override val isDirectory: Boolean,
    val size: Long,
    val lastModified: String,
    val absolutePath: String
) : IFileItem {
    override val fileName: String get() = name
    override val sizeText: String get() = if (isDirectory) "文件夹" else formatSize(size)
    override val dateText: String get() = lastModified
    override val fullPath: String get() = absolutePath

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}