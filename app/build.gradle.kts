plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.feitu.monitor"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.feitu.monitor"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 极光配置 (注意 Kotlin DSL 的写法)
        manifestPlaceholders["JPUSH_PKGNAME"] = applicationId!!
        manifestPlaceholders["JPUSH_APPKEY"] = "f8dadd125e47950b93043b1d" // 填入了你的真实 AppKey
        manifestPlaceholders["JPUSH_CHANNEL"] = "developer-default"

        // 小米通道 (JPush 5.5.3 开始不需要 MI- 前缀)
        manifestPlaceholders["XIAOMI_APPID"] = "你的小米APPID"
        manifestPlaceholders["XIAOMI_APPKEY"] = "你的小米APPKEY"

        // 荣耀通道 (极光文档摘要里没写，但官方插件支持)
        manifestPlaceholders["HONOR_APPID"] = "你的荣耀APPID"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // 引入极光基础 SDK (使用 5.5.3 版本)
    implementation("cn.jiguang.sdk:jpush:5.5.3")
    implementation(files("libs/HiPushSDK-8.0.12.307.aar"))
    // 二维码生成和扫描库 (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // 协程 (用于异步处理)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // OkHttp网络请求库
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Gson JSON 解析库
    implementation("com.google.code.gson:gson:2.10.1")
    // SwipeRefreshLayout 下拉刷新库
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

}