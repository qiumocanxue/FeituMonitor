package com.feitu.monitor.models

import com.feitu.monitor.common.models.MessageEnvelope

interface OnMessageReceivedListener {
    fun onStateChange(state: String)
    fun onNewMessage(envelope: MessageEnvelope)
    fun onError(error: String)
}