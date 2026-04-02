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
        manifestPlaceholders["JPUSH_APPKEY"] = "f8dadd125e47950b93043b1d"
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
    // 基础库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.camera2.pipe)

    // 单元测试
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    implementation(libs.jpush)
    implementation(libs.zxing.android.embedded)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.mpandroidchart)

    // 本地 AAR 保持不变
    implementation(files("libs/HiPushSDK-8.0.12.307.aar"))
}