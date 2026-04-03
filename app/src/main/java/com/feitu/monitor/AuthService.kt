package com.feitu.monitor

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.feitu.monitor.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import android.annotation.SuppressLint

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.Protocol

class AuthService(context: Context) {

    companion object {
        const val BASE_URL = "https://nyy.qiu.ink:7122"
    }

    // 全局复用一个 OkHttpClient 实例以提升性能
    private val client = createUnsafeOkHttpClient()
    private val gson = Gson()

    // 快捷获取 SharedPreferences
    private val prefs = context.getSharedPreferences("feitu_prefs", Context.MODE_PRIVATE)

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        try {
            // 创建一个不检查证书的信任管理器
            val trustAllCerts = arrayOf<TrustManager>(
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true } // 🌟 忽略域名匹配检查
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /**
     * 登录方法
     * suspend 关键字等价于 Dart 的 Future (需要在协程中调用)
     * withContext(Dispatchers.IO) 确保网络请求在后台线程执行，不卡死 UI
     */
    suspend fun login(username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/api/login"

            try {
                val jsonParam = JSONObject().apply {
                    put("Username", username)
                    put("Password", password)
                }.toString()

                // 🌟 修复编译错误：使用字符串的扩展函数 toMediaTypeOrNull()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

                // 🌟 修复建议：RequestBody.create 也建议改为扩展函数写法
                val requestBody = jsonParam.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    // ... 后续逻辑保持不变
                    val responseString = response.body?.string() ?: "{}"
                    val jsonObject = JSONObject(responseString)

                    if (response.isSuccessful && jsonObject.optBoolean("success")) {
                        val token = jsonObject.getString("token")
                        prefs.edit {
                            putString("jwt_token", token)
                            putString("username", username)
                        }
                        return@withContext LoginResult(true, jsonObject.optString("message", "登录成功"))
                    } else {
                        val errorDetail = jsonObject.optString("message", "登录失败")
                        return@withContext LoginResult(false, errorDetail)
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthService", "登录异常", e)
                return@withContext LoginResult(false, "⚠️ 网络连接错误")
            }
        }

    // 检查是否已登录
    fun isLoggedIn(): Boolean = prefs.contains("jwt_token")

    // 登出
    fun logout() {
        prefs.edit {
            remove("jwt_token")
            remove("username")
        }
    }

    // 获取 Token
    fun getToken(): String? = prefs.getString("jwt_token", null)

    fun getUserName(): String = prefs.getString("username", "管理员") ?: "管理员"

    // 检查响应头，如果有新 Token 就自动更新
    private fun checkAndSaveNewToken(response: Response) {
        val newToken = response.header("x-new-token")
        if (!newToken.isNullOrEmpty()) {
            Log.d("AuthService", "🔄 [AutoRefresh] 收到新 Token，正在更新本地存储...")
            // 🌟 修复 4：更新 Token
            prefs.edit { putString("jwt_token", newToken) }
        }
    }

    // 主动保活心跳请求
    suspend fun keepAlive() = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext
        val url = "$BASE_URL/api/agents"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                checkAndSaveNewToken(response)

                if (response.isSuccessful) {
                    Log.d("AuthService", "💓 [AutoRefresh] 保活请求成功")
                }
            }
        } catch (e: Exception) {
            Log.e("AuthService", "⚠️ [AutoRefresh] 保活请求失败", e)
        }
    }

    /**
     * 🌟 新增：用于解析后端返回的 {"success": true, "message": "..."}
     */
    data class ApiResponse(val success: Boolean, val message: String?)

    /**
     * 1. 获取全部配置列表 (GET) - 路径更新为 /list
     */
    suspend fun getEncryptionParams(): List<EncryptionParams> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val url = "$BASE_URL/api/encryption-params/list?token=$token" // 🌟 路径已更新

        Log.d("FeituAPI", "🚀 [加载列表] URL: $url")

        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: "[]"
                    val type = object : TypeToken<List<EncryptionParams>>() {}.type
                    return@withContext gson.fromJson<List<EncryptionParams>>(json, type)
                }
            }
        } catch (e: Exception) {
            Log.e("FeituAPI", "💥 获取列表失败", e)
        }
        return@withContext emptyList()
    }

    /**
     * 2. 保存或修改配置 (POST)
     */
    suspend fun saveEncryptionParams(params: EncryptionParams): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        val url = "$BASE_URL/api/encryption-params?token=$token"

        try {
            val json = gson.toJson(params)
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""
                Log.d("FeituAPI", "📥 [保存返回]: $responseString")
                val apiRes = gson.fromJson(responseString, ApiResponse::class.java)
                return@withContext apiRes?.success ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 3. 删除某条配置 (DELETE)
     */
    suspend fun deleteEncryptionParam(id: Int): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        val url = "$BASE_URL/api/encryption-params/$id?token=$token"

        try {
            val request = Request.Builder().url(url).delete().build()
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""
                Log.d("FeituAPI", "📥 [删除返回]: $responseString")
                val apiRes = gson.fromJson(responseString, ApiResponse::class.java)
                return@withContext apiRes?.success ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}