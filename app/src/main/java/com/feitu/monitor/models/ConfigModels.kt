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
 * 二维码配置模型 (对应你后端的 /api/config/qr)
 */
data class QrConfig(
    // 如果后端的 JSON 字段名和这里的变量名一模一样，就不需要 @SerializedName
    // 但如果后端返回的是下划线命名(如 hospital_name)，就必须用 @SerializedName 指定映射关系
    @SerializedName("hospital_name")
    val hospitalName: String?,

    @SerializedName("hs_code")
    val hsCode: String,

    @SerializedName("aes_key")
    val aesKey: String?,

    @SerializedName("aes_iv")
    val aesIv: String?
)

/**
 * 公众号配置模型 (对应你后端的 /api/config/oa)
 * 注意：这里我随便写了几个字段，你需要根据你真实的后台 JSON 结构来修改！
 */
data class OaConfig(
    @SerializedName("hospital_name") val hospitalName: String?, // 🌟 映射后台的 hospital_name
    @SerializedName("hs_code") val hsCode: String?,             // 🌟 映射后台的 hs_code
    @SerializedName("access_key") val accessKey: String?,       // 🌟 映射后台的 access_key
    @SerializedName("access_secret") val accessSecret: String?  // 🌟 映射后台的 access_secret
)