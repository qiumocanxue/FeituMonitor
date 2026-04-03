package com.feitu.monitor.models

import com.google.gson.annotations.SerializedName

/**
 * 登录结果模型
 */
data class LoginResult(
    val success: Boolean,
    val message: String = ""
)

/**
 * 加密参数
 */

data class EncryptionParams(
    val id: Int = 0,
    val hospitalName: String,
    val hscod: String,
    val aesKey: String,
    val aesIv: String,
    val accessKey: String,
    val accessSecret: String
)