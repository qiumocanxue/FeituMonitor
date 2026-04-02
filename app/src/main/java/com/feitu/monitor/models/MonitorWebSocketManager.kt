package com.feitu.monitor.models

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import com.feitu.monitor.AuthService
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import android.annotation.SuppressLint

class MonitorWebSocketManager(private val context: Context) {

    private val client: OkHttpClient by lazy { createOkHttpClient() }
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    var listener: OnMessageReceivedListener? = null

    var isConnected: Boolean = false
        private set

    // 测试方法，上线要改
    @SuppressLint("CustomX509TrustManager", "BadHostnameVerifier")
    private fun createOkHttpClient(): OkHttpClient {
        try {
            val pfxStream: InputStream = context.assets.open("test_cert.pfx")
            val password = "123456"
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(pfxStream, password.toCharArray())

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password.toCharArray())

            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, trustAllCerts, java.security.SecureRandom())

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            throw RuntimeException("证书加载失败: ${e.message}")
        }
    }

    fun connect(wssUrl: String) {
        val request = Request.Builder().url(wssUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                listener?.onStateChange("已连接")

                val currentUsername = AuthService(context).getUserName()
                sendEnvelope(
                    type = "ClientLogin",
                    to = "Server",
                    fromOverride = currentUsername,
                    payload = emptyMap<String, Any>()
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    Log.d("WSS_RECEIVE", "原始数据: $text")
                    val envelope = gson.fromJson(text, MessageEnvelope::class.java)
                    listener?.onNewMessage(envelope)
                } catch (e: Exception) {
                    // 🌟 修复警告 1：在 Log 中使用 e，解析失败时能看到具体原因
                    Log.e("WSS", "解析失败: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                listener?.onStateChange("连接断开")
                listener?.onError(t.message ?: "未知错误")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected = false
                listener?.onStateChange("正在关闭")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
        })
    }

    fun sendEnvelope(type: String, to: String, payload: Any?, fromOverride: String? = null) {
        val envelope = MessageEnvelope(
            Type = type,
            From = fromOverride ?: "Android_User_001",
            To = to,
            RequestId = UUID.randomUUID().toString(),
            Payload = payload
        )

        val json = gson.toJson(envelope)
        webSocket?.send(json)
        Log.d("WSS_SEND", "发送数据包: $json")
    }

    fun send(text: String) {
        if (isConnected) {
            webSocket?.send(text)
            Log.d("WSS", "已发送: $text")
        } else {
            Log.e("WSS", "发送失败：连接未就绪")
        }
    }

    @Suppress("unused")
    fun disconnect() {
        webSocket?.close(1000, "User logout")
        isConnected = false
    }
}