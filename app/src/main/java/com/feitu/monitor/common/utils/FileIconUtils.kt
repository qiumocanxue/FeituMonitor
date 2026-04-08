package com.feitu.monitor.common.utils

import com.feitu.monitor.R
import com.feitu.monitor.common.models.IFileItem

object FileIconUtils {

    /**
     * 根据文件后缀获取对应的图标资源 ID
     */
    fun getFileIconRes(item: IFileItem): Int {
        if (item.isDirectory) return R.drawable.ic_folder

        val name = item.fileName.lowercase()
        return when {
            // Word 文档
            name.endsWith(".doc") || name.endsWith(".docx") -> R.drawable.ic_file_word
            // Excel 表格
            name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv") -> R.drawable.ic_file_excel
            // PPT 演示
            name.endsWith(".ppt") || name.endsWith(".pptx") -> R.drawable.ic_file_ppt
            // PDF 文档
            name.endsWith(".pdf") -> R.drawable.ic_file_pdf
            // 文本/日志/配置/脚本
            name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".ini") ||
                    name.endsWith(".json") || name.endsWith(".xml") -> R.drawable.ic_file_txt
            // 压缩包
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
                    name.endsWith(".tar") || name.endsWith(".gz") -> R.drawable.ic_file_zip
            // 图片
            name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> R.drawable.ic_file_image
            // 视频
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") ||
                    name.endsWith(".avi") || name.endsWith(".rmvb") -> R.drawable.ic_file_video
            // 音频
            name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") ||
                    name.endsWith(".m4a") -> R.drawable.ic_file_audio
            // 链接
            name.endsWith(".url") || name.endsWith(".link") -> R.drawable.ic_file_url
            // 未知类型
            else -> R.drawable.ic_file_unknown
        }
    }
}