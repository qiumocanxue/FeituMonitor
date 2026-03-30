package com.feitu.monitor

import android.app.Application
import cn.jpush.android.api.JPushInterface

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 初始化极光推送
        JPushInterface.setDebugMode(true)
        JPushInterface.init(this)
    }
}