package com.feitu.monitor.notification

import cn.jpush.android.service.JPushMessageReceiver

// 继承 JPushMessageReceiver
class MyPushReceiver : JPushMessageReceiver() {
    // 这里暂时留空即可。
    // 以后如果需要处理“点击通知跳转到特定页面”等逻辑，就在这里重写相关方法。
}