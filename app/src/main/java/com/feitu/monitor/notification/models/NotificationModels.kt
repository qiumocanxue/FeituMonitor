package com.feitu.monitor.notification.models

// 后端返回的原始消息模型
data class RawNotification(
    val type: String?,    // WS 专用
    val title: String?,   // HTTP 专用
    val message: String?, // 通用
    val level: String?,   // 通用
    val timestamp: Any?
)