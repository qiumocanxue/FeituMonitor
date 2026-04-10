package com.feitu.monitor.remote.models

import java.util.UUID


data class FileCommand(
    val Type: String = "FileCommand",
    val From: String,
    val To: String,
    val RequestId: String = UUID.randomUUID().toString().replace("-", ""),
    val Payload: FilePayload
)

/**
 * 1. 消息类型常量
 */
object MessageType {
    const val FILE_COMMAND = "FileCommand"
    const val FILE_RESPONSE = "FileResponse"
    const val FILE_DATA = "FileData"
    const val ERROR = "Error"
}

/**
 * 指令具体载荷
 */
data class FilePayload(
    val Action: String,
    val Path: String,
    val Base64Data: String? = null,
    val FileSize: Long? = null,
    val TotalChunks: Int? = null
)

/**
 * 远程文件响应解析模型 (通用包装)
 */
data class FileResponsePayload(
    val Status: String? = null,
    val Message: String? = null,
    val Data: RemoteDataResponse? = null,
    val Content: String? = null,
    val Path: String? = null,
    val FileName: String? = null
)
/**
 * 目录清单 Data 结构
 */
data class RemoteDataResponse(
    val CurrentPath: String?,
    val SubFolders: List<String>?,
    val Files: List<RemoteFileItem>?,
    val Error: String?
)

data class RemoteFileItem(
    val Name: String,
    val Size: Long,
    val LastMod: String
)

object FileAction {
    const val LIST = "ListFiles"
    const val OPEN = "OpenFile"
    const val DOWNLOAD = "Download"
    const val DELETE = "Delete"
    const val UPLOAD_START = "UploadStart"
    const val UPLOAD_END = "UploadEnd"
    const val DRIVES = "GetDrives"
}