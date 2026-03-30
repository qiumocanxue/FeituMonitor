package com.feitu.monitor

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.feitu.monitor.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class AuthService(context: Context) {

    companion object {
        // ⚠️ 局域网调试请使用具体 IP，模拟器使用 10.0.2.2
        const val BASE_URL = "https://yy.qiu.ink"
    }

    // 全局复用一个 OkHttpClient 实例以提升性能
    private val client = OkHttpClient()
    private val gson = Gson()

    // 快捷获取 SharedPreferences
    private val prefs = context.getSharedPreferences("feitu_prefs", Context.MODE_PRIVATE)

    /**
     * 登录方法
     * suspend 关键字等价于 Dart 的 Future (需要在协程中调用)
     * withContext(Dispatchers.IO) 确保网络请求在后台线程执行，不卡死 UI
     */
    suspend fun login(username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/token"

            try {
                val formBody = FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseString = response.body?.string() ?: "{}"
                    val jsonObject = JSONObject(responseString)

                    if (response.isSuccessful) {
                        val token = jsonObject.getString("access_token")

                        // 🌟 修复 2：使用 KTX 扩展写法，无需手动写 apply()
                        prefs.edit {
                            putString("jwt_token", token)
                            putString("username", username)
                        }

                        return@withContext LoginResult(true, "登录成功")
                    } else {
                        val errorDetail = jsonObject.optString("detail", "登录失败")
                        return@withContext LoginResult(false, if (response.code == 429) "⛔ $errorDetail" else "❌ $errorDetail")
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthService", "登录异常", e)
                return@withContext LoginResult(false, "⚠️ 网络连接错误，请检查服务器")
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

    // --- 获取二维码配置列表 ---
    suspend fun getQrConfigs(): List<QrConfig> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val url = "$BASE_URL/api/config/qr"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseString = response.body?.string() ?: "[]"
                    Log.e("FeituJSON", "后台返回的配置数据是: $responseString")

                    // 🌟 使用 Gson 解析 Json Array 为 List<QrConfig>
                    val listType = object : TypeToken<List<QrConfig>>() {}.type
                    return@withContext gson.fromJson(responseString, listType)
                } else {
                    Log.e("AuthService", "获取QR配置失败: ${response.code}")
                    return@withContext emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("AuthService", "网络异常", e)
            return@withContext emptyList()
        }
    }

    // --- 获取公众号配置列表 ---
    suspend fun getOaConfigs(): List<OaConfig> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val url = "$BASE_URL/api/config/oa"

        // 🌟 打印：请求地址和 Token
        Log.d("FeituAPI", "🚀 [OA配置请求] 开始请求: $url")
        Log.d("FeituAPI", "🔑 [Auth Header]: Bearer $token")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                // 🌟 打印：响应状态码
                Log.d("FeituAPI", "📡 [OA响应状态]: ${response.code}")

                if (response.isSuccessful) {
                    val json = response.body?.string() ?: "[]"

                    // 🌟 打印：最关键的原始 JSON
                    Log.d("FeituAPI", "📥 [OA原始数据]: $json")

                    val type = object : TypeToken<List<OaConfig>>() {}.type
                    return@withContext Gson().fromJson(json, type)
                } else {
                    Log.e("FeituAPI", "❌ OA请求失败: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("FeituAPI", "💥 OA请求异常: ${e.message}")
        }
        return@withContext emptyList()
    }
}