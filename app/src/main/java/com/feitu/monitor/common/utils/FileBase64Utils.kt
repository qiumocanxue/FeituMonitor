package com.feitu.monitor.common.utils

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object FileBase64Utils {
    /**
     * 保存到系统公共 Download 目录
     */
    fun saveBase64ToFile(context: Context, base64Str: String, fileName: String): File? {
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)

            // 🌟 获取系统公共下载目录：/storage/emulated/0/Download
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // 如果目录不存在则创建（虽然系统下载目录通常都存在）
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val file = File(downloadDir, fileName)

            // 如果文件名已存在，自动重命名避免覆盖（可选优化）
            var finalFile = file
            if (finalFile.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast(".")
                val ext = fileName.substringAfterLast(".", "")
                finalFile = File(downloadDir, "${nameWithoutExt}_${System.currentTimeMillis()}.$ext")
            }

            FileOutputStream(finalFile).use { it.write(bytes) }
            Log.d("FileBase64Utils", "文件已保存到: ${finalFile.absolutePath}")
            finalFile
        } catch (e: Exception) {
            Log.e("FileBase64Utils", "保存失败: ${e.message}")
            null
        }
    }
}