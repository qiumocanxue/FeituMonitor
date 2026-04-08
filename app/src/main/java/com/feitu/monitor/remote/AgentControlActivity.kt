package com.feitu.monitor.remote

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.feitu.monitor.MonitorFragment
import com.feitu.monitor.R
import com.feitu.monitor.common.models.HeartbeatPayload
import com.feitu.monitor.common.models.MessageEnvelope
import com.feitu.monitor.models.*
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

class AgentControlActivity : AppCompatActivity(), OnMessageReceivedListener {

    private lateinit var agentId: String
    private lateinit var agentAlias: String

    private lateinit var progressCpu: CircularProgressIndicator
    private lateinit var progressRam: CircularProgressIndicator
    private lateinit var tvCpuLabel: TextView
    private lateinit var tvRamLabel: TextView
    private lateinit var tvMiniLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_control)

        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        agentAlias = intent.getStringExtra("AGENT_ALIAS") ?: "未知设备"

        initUI()
        // 🌟 统一配置所有菜单项
        setupMenuIcons()

        MonitorFragment.Companion.getWssManager(this)?.let {
            it.addListener(this)
            if (it.isConnected) {
                appendLog("监控链路已就绪")
            } else {
                appendLog("正在等待链路建立...")
            }
        }
    }

    private fun initUI() {
        // 设置标题和返回键
        findViewById<TextView>(R.id.tv_toolbar_title).text = agentAlias
        findViewById<View>(R.id.btn_back_custom).setOnClickListener { finish() }

        // 沉浸式状态栏适配
        val toolbar = findViewById<View>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBar.top, 0, 0)
            insets
        }

        // 初始化进度条和文本
        progressCpu = findViewById(R.id.progress_cpu)
        progressRam = findViewById(R.id.progress_ram)
        tvCpuLabel = findViewById(R.id.tv_cpu_label)
        tvRamLabel = findViewById(R.id.tv_ram_label)
        tvMiniLog = findViewById(R.id.tv_mini_log)

        val d = resources.displayMetrics.density
        setupGauge(progressCpu, "#FF5252", d)
        setupGauge(progressRam, "#2196F3", d)
    }

    /**
     * 🌟 核心重构：在这里统一配置所有的按钮逻辑
     */
    private fun setupMenuIcons() {
        // 1. 性能趋势
        setMenuItem(R.id.btn_perf_trends, "性能趋势", R.drawable.ic_trending_up) {
            // 发送历史数据请求
            val request = mapOf(
                "Type" to "GetHistory",
                "From" to "MyAndroid",
                "To" to "Server",
                "Payload" to mapOf("AgentId" to agentId, "Minutes" to 30)
            )
            MonitorFragment.Companion.getWssManager(this)?.send(Gson().toJson(request))

            // 跳转页面
            startActivity(Intent(this, PerfTrendsActivity::class.java).apply {
                putExtra("AGENT_ID", agentId)
                putExtra("AGENT_ALIAS", agentAlias)
            })
        }

        // 2. 文件管理 (已修复点击响应)
        setMenuItem(R.id.btn_file_manage, "文件管理", R.drawable.ic_desktop) {
            startActivity(Intent(this, AgentFileActivity::class.java).apply {
                putExtra("AGENT_ID", agentId)
                putExtra("AGENT_ALIAS", agentAlias)
            })
        }

        // 3. 远程桌面 (占位)
        setMenuItem(R.id.btn_remote_desktop, "远程桌面", R.drawable.ic_desktop) {
            showPendingToast("远程桌面")
        }

        // 4. 自助打印 (占位)
        setMenuItem(R.id.btn_print_service, "自助打印", R.drawable.ic_print) {
            showPendingToast("自助打印")
        }

        // 5. 故障排查 (占位)
        setMenuItem(R.id.btn_troubleshoot, "故障排查", R.drawable.ic_build) {
            showPendingToast("故障排查")
        }

        // 6. CMD指令 (占位)
        setMenuItem(R.id.btn_cmd, "CMD指令", R.drawable.ic_terminal) {
            showPendingToast("CMD指令")
        }
    }

    /**
     * 🌟 封装后的菜单设置方法，支持传入点击回调
     */
    private fun setMenuItem(layoutId: Int, title: String, iconRes: Int, onClick: () -> Unit) {
        val container = findViewById<View>(layoutId)
        // 设置图标和文字
        container.findViewById<ImageView>(R.id.menu_icon).setImageResource(iconRes)
        container.findViewById<TextView>(R.id.menu_text).text = title

        // 绑定点击事件
        container.setOnClickListener { onClick() }
    }

    private fun showPendingToast(featureName: String) {
        Toast.makeText(this, "$featureName 功能正在适配中...", Toast.LENGTH_SHORT).show()
    }

    /**
     * 设置圆环进度条样式
     */
    private fun setupGauge(gauge: CircularProgressIndicator, color: String, d: Float) {
        gauge.apply {
            indicatorSize = (64 * d).toInt()
            trackThickness = (6 * d).toInt()
            setIndicatorColor(color.toColorInt())
            trackColor = "#F0F0F0".toColorInt()
        }
    }

    // --- 消息处理与状态同步逻辑保持不变 ---

    override fun onNewMessage(envelope: MessageEnvelope) {
        if (envelope.Type == "Heartbeat" &&
            envelope.From.trim().equals(agentId.trim(), ignoreCase = true)) {
            runOnUiThread { updateRealtimeStats(envelope.Payload) }
        }
    }

    private fun updateRealtimeStats(payload: Any?) {
        try {
            val json = Gson().toJson(payload)
            val data = Gson().fromJson(json, HeartbeatPayload::class.java) ?: return

            progressCpu.setProgress(data.CpuUsage, true)
            progressRam.setProgress(data.RamUsage.toInt(), true)

            tvCpuLabel.text = getString(R.string.cpu_format, data.CpuUsage)
            tvRamLabel.text = getString(R.string.ram_format, data.RamUsage)

        } catch (e: Exception) {
            Log.e("ControlCenter", "心跳解析异常: ${e.message}")
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val lines = "[$time] $msg\n${tvMiniLog.text}".lines().take(6)
        tvMiniLog.text = lines.joinToString("\n")
    }

    override fun onStateChange(state: String) {
        runOnUiThread { appendLog("连接状态: $state") }
    }

    override fun onError(error: String) {
        runOnUiThread { appendLog("错误反馈: $error") }
    }

    override fun onDestroy() {
        super.onDestroy()
        MonitorFragment.Companion.getWssManager(this)?.removeListener(this)
    }
}