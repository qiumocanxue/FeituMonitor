package com.feitu.monitor.common.models

import androidx.annotation.Keep

@Keep
data class MessageEnvelope(
    val Type: String,
    val From: String,
    val To: String,
    val RequestId: String,
    val Payload: Any?
)

@Keep
data class AgentInfo(
    val UniqueId: String,
    val Alias: String,
    val MachineName: String,
    val Status: String,
    var CpuUsage: Int = 0,
    var RamUsage: Double = 0.0,
    var NetUp: Double = 0.0,
    var NetDown: Double = 0.0
)

@Keep
data class HeartbeatPayload(
    val CpuUsage: Int,
    val RamUsage: Double,
    val NetUp: Double,
    val NetDown: Double,
    val DiskFreeGb: Double,
    val Drives: List<String>
)

@Keep
data class HistoryPoint(
    val cpu: Float,
    val ram: Float,
    val time: String
)

@Keep
data class HistoryResponsePayload(
    val Points: List<HistoryPoint>
)