package com.feitu.monitor.cloud

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.feitu.monitor.R
import com.feitu.monitor.cloud.models.FtpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class FilePreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启边到边（Edge-to-Edge）显示，这是 2026 年 Android 开发的标准做法
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_file_preview)

        // 现代做法通常在主题(Theme)中设置，若必须在代码设置：
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false // 状态栏文字设为白色

        // 用 Resource 获取默认字符
        val fileUrl = intent.getStringExtra("FILE_URL") ?: ""
        val fileName = intent.getStringExtra("FILE_NAME") ?: getString(R.string.preview_default)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // 避让刘海
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }

        setSupportActionBar(toolbar)
        supportActionBar?.title = fileName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tvContent = findViewById<TextView>(R.id.tv_content)
        // 使用 Resource String
        tvContent.text = getString(R.string.loading)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", FtpConfig.getBasicAuthHeader())
                connection.connectTimeout = 10000

                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    withContext(Dispatchers.Main) {
                        tvContent.text = text
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        // 使用带占位符的字符串资源
                        tvContent.text = getString(R.string.load_failed, connection.responseCode)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // 使用带占位符的字符串资源
                    tvContent.text = getString(R.string.connection_error, e.message ?: "Unknown")
                }
            }
        }
    }
}