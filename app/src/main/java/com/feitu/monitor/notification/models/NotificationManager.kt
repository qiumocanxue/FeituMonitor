package com.feitu.monitor.notification.models


// 1. 消息数据模型
data class SystemMessage(
    val id: String,
    val msg: String,
    val level: String = "info", // 'info', 'error', 'warning', 'success'
    val time: Long = System.currentTimeMillis()
)

// 2. 全局消息管理器
object NotificationManager {
    val messages = mutableListOf<SystemMessage>()
    var unreadCount = 0
    var onDataChanged: (() -> Unit)? = null
    var onUnreadChanged: ((Int) -> Unit)? = null

    // 当收到新消息时（无论来自 HTTP 还是 WS）
    fun addMessage(msg: SystemMessage) {
        messages.add(0, msg) // 塞到列表最前面
        unreadCount++        // 未读数递增

        // 🌟 核心：触发回调通知 MainActivity 亮起红点，通知 Fragment 刷新列表
        onDataChanged?.invoke()
        onUnreadChanged?.invoke(unreadCount)
    }

    fun markAllAsRead() {
        unreadCount = 0
        onUnreadChanged?.invoke(unreadCount)
    }

    fun clearMessages() {
        messages.clear()
        unreadCount = 0
        onDataChanged?.invoke()
        onUnreadChanged?.invoke(unreadCount)
    }
}