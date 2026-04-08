package com.feitu.monitor.common.models

/**
 * 通用文件项接口，用于统一远程 Agent 文件和云端 FTP 文件的 UI 展示
 */
interface IFileItem {
    val fileName: String      // 显示名称
    val isDirectory: Boolean  // 是否为文件夹
    val sizeText: String      // 格式化后的容量文本 (如 "1.2 MB" 或 "-")
    val dateText: String      // 格式化后的日期文本
    val fullPath: String      // 唯一路径或 URL，用于点击跳转
}