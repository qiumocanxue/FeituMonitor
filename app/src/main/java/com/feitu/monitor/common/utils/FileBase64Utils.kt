package com.feitu.monitor.common.utils

import android.util.Base64
import java.io.File

object FileBase64Utils {

    /**
     * 将文件转为 Base64 字符串（用于 Upload）
     */
    fun fileToBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * 将 Base64 字符串转为文件（用于 Download 后的保存）
     */
    fun base64ToFile(base64Str: String, targetFile: File) {
        val bytes = Base64.decode(base64Str, Base64.DEFAULT)
        targetFile.writeBytes(bytes)
    }
}