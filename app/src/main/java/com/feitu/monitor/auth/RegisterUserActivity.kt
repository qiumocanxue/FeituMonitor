package com.feitu.monitor.auth

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.feitu.monitor.R
import kotlinx.coroutines.launch

class RegisterUserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_user)

        val authService = AuthService(this)
        val etUser = findViewById<EditText>(R.id.et_reg_username)
        val etPass = findViewById<EditText>(R.id.et_reg_password)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_do_register).setOnClickListener {
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString().trim()

            if (u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val result = authService.registerUser(u, p)
                Toast.makeText(this@RegisterUserActivity, result.message, Toast.LENGTH_LONG).show()
                if (result.success) finish()
            }
        }
    }
}