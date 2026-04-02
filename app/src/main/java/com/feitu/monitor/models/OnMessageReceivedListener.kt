package com.feitu.monitor.models

interface OnMessageReceivedListener {
    fun onStateChange(state: String)
    fun onNewMessage(envelope: MessageEnvelope)
    fun onError(error: String)
}