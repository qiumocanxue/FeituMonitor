package com.feitu.monitor.models

import java.util.UUID

/**
 * 远程文件指令协议模型
 */
data class FileCommand(
    val Type: String = "FileCommand",
    val From: String,
    val To: String,
    val RequestId: String = UUID.randomUUID().toString().replace("-", ""),
    val Payload: FilePayload
)

/**
 * 指令具体内容
 */
data class FilePayload(
    val Action: String,      // 对应 FileAction 中的常量
    val Path: String,        // 远程机器的绝对路径
    val Base64Data: String? = null // 上传时需要的 Base64 内容
)

/**
 * 🌟 指令常量定义
 * 建议使用此对象中的常量，避免手动敲字符串导致拼写错误
 */
object FileAction {
    const val OPEN = "OpenFile"      // 打开/读取文件内容
    const val DOWNLOAD = "Download"  // 下载文件
    const val UPLOAD = "Upload"      // 上传文件
    const val DELETE = "Delete"      // 删除文件

    // 如果后端支持获取目录列表，通常约定为 ListFiles
    const val LIST = "ListFiles"
}