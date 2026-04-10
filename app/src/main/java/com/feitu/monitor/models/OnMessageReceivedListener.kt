package com.feitu.monitor.models

import com.feitu.monitor.common.models.MessageEnvelope
import okio.ByteString

interface OnMessageReceivedListener {
    fun onStateChange(state: String)
    fun onNewMessage(envelope: MessageEnvelope)
    fun onNewBinaryMessage(bytes: ByteString) {}
    fun onError(error: String)
}