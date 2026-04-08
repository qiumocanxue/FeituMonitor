package com.feitu.monitor.auth

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.feitu.monitor.R
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var authService: AuthService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authService = AuthService(this)

        val etUsername = findViewById<TextInputEditText>(R.id.et_username)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login_submit)
        val progressLogin = findViewById<ProgressBar>(R.id.progress_login)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. 切换为加载状态
            btnLogin.text = "" // 清空文字
            btnLogin.isEnabled = false // 禁用按钮防止重复点击
            progressLogin.visibility = View.VISIBLE // 显示转圈圈

            // 2. 启动协程发起网络请求
            lifecycleScope.launch {
                val result = authService.login(username, password)

                // 3. 请求结束，恢复 UI 状态
                btnLogin.text = "立即登录"
                btnLogin.isEnabled = true
                progressLogin.visibility = View.GONE

                if (result.success) {
                    Toast.makeText(this@LoginActivity, "登录成功！", Toast.LENGTH_SHORT).show()
                    // 🌟 重点：告诉 MainActivity 我们登录成功了，并关闭当前页面
                    setResult(RESULT_OK)
                    finish()
                } else {
                    // 显示后端返回的错误信息（如密码错误、被限流等）
                    Snackbar.make(findViewById(android.R.id.content), result.message, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(getColor(R.color.error_red))
                        .show()
                }
            }
        }
    }
}