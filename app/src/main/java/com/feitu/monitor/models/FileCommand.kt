package com.feitu.monitor.models

import java.util.UUID

/**
 * 1. 远程文件发送指令协议 (手机 -> Server -> Agent)
 */
data class FileCommand(
    val Type: String = "FileCommand",
    val From: String,
    val To: String,
    val RequestId: String = UUID.randomUUID().toString().replace("-", ""),
    val Payload: FilePayload
)

/**
 * 指令具体载荷
 */
data class FilePayload(
    val Action: String,      // 对应 FileAction 中的常量
    val Path: String,        // 远程机器的绝对路径
    val Base64Data: String? = null // 上传或写入时使用的内容
)

/**
 * 2. 远程文件响应解析模型 (Agent -> Server -> 手机)
 * 匹配你日志中 Data 字段的结构
 */
data class RemoteDataResponse(
    val CurrentPath: String?,
    val SubFolders: List<String>?,
    val Files: List<RemoteFileItem>?,
    val Status: String?,
    val Error: String?
)

/**
 * 响应中 Files 列表的具体单项
 */
data class RemoteFileItem(
    val Name: String,
    val Size: Long,      // 对应日志中的 Size
    val LastMod: String  // 对应日志中的 LastMod
)

/**
 * 🌟 指令常量定义
 */
object FileAction {
    const val OPEN = "OpenFile"      // 打开/读取文件内容
    const val DOWNLOAD = "Download"  // 下载文件
    const val UPLOAD = "Upload"      // 上传文件
    const val DELETE = "Delete"      // 删除文件
    const val LIST = "ListFiles"     // 获取目录列表
}