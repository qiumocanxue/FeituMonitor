package com.feitu.monitor.models

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import com.feitu.monitor.auth.AuthService
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import android.annotation.SuppressLint
import com.feitu.monitor.common.models.MessageEnvelope
import java.util.concurrent.CopyOnWriteArraySet
import okio.ByteString

class MonitorWebSocketManager(private val context: Context) {

    private val client: OkHttpClient by lazy { createOkHttpClient() }
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val listeners = CopyOnWriteArraySet<OnMessageReceivedListener>()

    var isConnected: Boolean = false
        private set

    fun addListener(listener: OnMessageReceivedListener) {
        listeners.add(listener)
        if (isConnected) {
            listener.onStateChange("已连接")
        }
    }

    fun removeListener(listener: OnMessageReceivedListener) {
        listeners.remove(listener)
    }

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
                listeners.forEach { it.onStateChange("已连接") }
                val currentUsername = AuthService(context).getUserName()
                sendEnvelope("ClientLogin", "Server", emptyMap<String, Any>(), currentUsername)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = gson.fromJson(text, MessageEnvelope::class.java)
                    listeners.forEach { it.onNewMessage(envelope) }
                } catch (e: Exception) {
                    Log.e("WSS", "解析失败", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                listeners.forEach { it.onNewBinaryMessage(bytes) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                listeners.forEach {
                    it.onStateChange("连接异常")
                    it.onError("网络波动: ${t.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket?.close(1000, null)
                isConnected = false
                listeners.forEach { it.onStateChange("正在关闭") }
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
        send(gson.toJson(envelope))
    }

    fun send(text: String) {
        if (isConnected) webSocket?.send(text)
    }

    fun send(bytes: ByteString) {
        if (isConnected) {
            webSocket?.send(bytes)
            Log.d("FileTransfer", ">>> 发送二进制帧: ${bytes.size} 字节")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User logout")
        isConnected = false
    }
}