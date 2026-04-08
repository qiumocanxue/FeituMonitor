package com.feitu.monitor.remote.models

data class RemoteFile(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: String,
    val absolutePath: String
)