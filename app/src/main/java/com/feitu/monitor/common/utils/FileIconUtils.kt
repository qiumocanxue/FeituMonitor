package com.feitu.monitor.common.utils

import com.feitu.monitor.R
import com.feitu.monitor.common.models.IFileItem

object FileIconUtils {

    /**
     * 根据文件后缀获取对应的图标资源 ID
     * 匹配逻辑基于你筛选出的 16 个核心矢量图标
     */
    fun getFileIconRes(item: IFileItem): Int {
        // 1. 优先判断是否为文件夹
        if (item.isDirectory) return R.drawable.ic_file_folder

        val name = item.fileName.lowercase()

        // 2. 根据后缀名进入对应的图标分类
        return when {
            // Word 文档
            name.endsWith(".doc") || name.endsWith(".docx") -> R.drawable.ic_file_word

            // Excel 表格
            name.endsWith(".xls") || name.endsWith(".xlsx") -> R.drawable.ic_file_excel

            // CSV 数据
            name.endsWith(".csv") -> R.drawable.ic_file_csv

            // PPT 幻灯片
            name.endsWith(".ppt") || name.endsWith(".pptx") -> R.drawable.ic_file_ppt

            // PDF 文档
            name.endsWith(".pdf") -> R.drawable.ic_file_pdf

            // 纯文本、日志、配置文件
            name.endsWith(".txt") || name.endsWith(".log") ||
                    name.endsWith(".ini") || name.endsWith(".conf") -> R.drawable.ic_file_txt

            // 压缩包
            name.endsWith(".zip") || name.endsWith(".rar") ||
                    name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") -> R.drawable.ic_file_zip

            // 图片格式
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                    name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> R.drawable.ic_file_image

            // 视频格式
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") ||
                    name.endsWith(".avi") || name.endsWith(".rmvb") -> R.drawable.ic_file_video

            // 音频格式
            name.endsWith(".mp3") || name.endsWith(".wav") ||
                    name.endsWith(".flac") || name.endsWith(".m4a") -> R.drawable.ic_file_audio

            // 可执行程序 (远程管理常用)
            name.endsWith(".exe") || name.endsWith(".msi") ||
                    name.endsWith(".bat") || name.endsWith(".cmd") -> R.drawable.ic_file_exe

            // 网页格式
            name.endsWith(".html") || name.endsWith(".htm") -> R.drawable.ic_file_html

            // XML 与 JSON 配置
            name.endsWith(".xml") || name.endsWith(".json") -> R.drawable.ic_file_xml

            // 快捷方式与链接
            name.endsWith(".url") || name.endsWith(".link") || name.endsWith(".lnk") -> R.drawable.ic_file_link

            // 默认未知文件类型
            else -> R.drawable.ic_file_unknown
        }
    }
}