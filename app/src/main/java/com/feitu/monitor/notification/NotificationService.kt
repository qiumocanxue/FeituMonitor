package com.feitu.monitor.notification

import android.content.Context
import android.util.Log
import com.feitu.monitor.auth.AuthService
import com.feitu.monitor.notification.models.NotificationManager
import com.feitu.monitor.notification.models.RawNotification
import com.feitu.monitor.notification.models.SystemMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationService(private val context: Context) {
    private val authService = AuthService(context)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isConnected = false
    private var isConnecting = false
    private var heartbeatJob: Job? = null

    companion object {
        const val TAG = "FeituNotify"
    }

    // ================= 1. HTTP 获取历史消息 =================
    suspend fun fetchHistory() = withContext(Dispatchers.IO) {
        val token = authService.getToken() ?: return@withContext
        val url = "${AuthService.Companion.BASE_URL}/api/notifications/history?limit=50"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBodyString = response.body?.string() ?: "[]"
                if (response.isSuccessful) {
                    val type = object : TypeToken<List<RawNotification>>() {}.type
                    val rawList: List<RawNotification> = gson.fromJson(responseBodyString, type)

                    val mappedList = rawList.map {
                        // 🌟 修复：兼容处理 title 和 timestamp
                        val displayMsg = if (!it.title.isNullOrEmpty()) {
                            "${it.title}\n${it.message ?: ""}"
                        } else {
                            it.message ?: ""
                        }

                        // 将 HTTP 的 Double 时间戳转为 Long 毫秒
                        val ts = when (val t = it.timestamp) {
                            is Double -> (t * 1000).toLong()
                            is String -> t.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: System.currentTimeMillis()
                            else -> System.currentTimeMillis()
                        }

                        SystemMessage(
                            id = UUID.randomUUID().toString(),
                            msg = displayMsg,
                            level = it.level ?: "info",
                            time = ts
                        )
                    }

                    withContext(Dispatchers.Main) {
                        NotificationManager.messages.clear()
                        NotificationManager.messages.addAll(mappedList)
                        NotificationManager.onDataChanged?.invoke()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取历史消息失败: ${e.message}")
        }
    }

    // ================= 2. WebSocket 实时监听 =================
    fun startService() {
        if (isConnected || isConnecting) return
        val token = authService.getToken() ?: return
        isConnecting = true

        val wsUrl = AuthService.Companion.BASE_URL.replace("http", "ws") + "/ws/web?token=$token"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                isConnecting = false
                startHttpHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWsMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                isConnecting = false
                serviceScope.launch {
                    delay(5000)
                    startService() // 重连
                }
            }
        })
    }

    private fun handleWsMessage(text: String) {
        try {
            val raw = gson.fromJson(text, RawNotification::class.java)

            // 🌟 匹配你 Python 后端的 payload 结构
            if (raw.type == "notification") {
                val newMsg = SystemMessage(
                    id = UUID.randomUUID().toString(),
                    msg = raw.message ?: "",
                    level = raw.level ?: "info",
                    time = System.currentTimeMillis() // 实时消息直接用当前手机时间
                )

                serviceScope.launch(Dispatchers.Main) {
                    NotificationManager.addMessage(newMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 WS 消息失败")
        }
    }

    private fun startHttpHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(20 * 60 * 1000)
                authService.keepAlive()
            }
        }
    }

    fun stopService() {
        webSocket?.close(1000, "Logout")
        heartbeatJob?.cancel()
        isConnected = false
    }
}