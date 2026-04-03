package com.feitu.monitor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        setupMenuIcons()

        MonitorFragment.getWssManager(this)?.let {
            it.addListener(this)
            if (it.isConnected) {
                appendLog("监控链路已就绪")
            } else {
                appendLog("正在等待链路建立...")
            }
        }
    }

    private fun initUI() {
        findViewById<TextView>(R.id.tv_toolbar_title).text = agentAlias
        findViewById<View>(R.id.btn_back_custom).setOnClickListener { finish() }

        val toolbar = findViewById<View>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBar.top, 0, 0)
            insets
        }

        progressCpu = findViewById(R.id.progress_cpu)
        progressRam = findViewById(R.id.progress_ram)
        tvCpuLabel = findViewById(R.id.tv_cpu_label)
        tvRamLabel = findViewById(R.id.tv_ram_label)
        tvMiniLog = findViewById(R.id.tv_mini_log)

        val d = resources.displayMetrics.density
        setupGauge(progressCpu, "#FF5252", d)
        setupGauge(progressRam, "#2196F3", d)

        findViewById<View>(R.id.btn_perf_trends).setOnClickListener {
            val request = mapOf(
                "Type" to "GetHistory",
                "From" to "MyAndroid",
                "To" to "Server",
                "Payload" to mapOf("AgentId" to agentId, "Minutes" to 30)
            )
            MonitorFragment.getWssManager(this)?.send(Gson().toJson(request))

            val intent = Intent(this, PerfTrendsActivity::class.java).apply {
                putExtra("AGENT_ID", agentId)
                putExtra("AGENT_ALIAS", agentAlias)
            }
            startActivity(intent)
        }
    }

    /**
     * 🌟 修复警告：使用 KTX 扩展函数 .toColorInt()
     */
    private fun setupGauge(gauge: CircularProgressIndicator, color: String, d: Float) {
        gauge.apply {
            indicatorSize = (64 * d).toInt()
            trackThickness = (6 * d).toInt()
            // 修复：不再使用 Color.parseColor(color)
            setIndicatorColor(color.toColorInt())
            trackColor = "#F0F0F0".toColorInt()
        }
    }

    private fun setupMenuIcons() {
        setMenuItem(R.id.btn_perf_trends, "性能趋势", R.drawable.ic_trending_up)
        setMenuItem(R.id.btn_file_manage, "文件管理", R.drawable.ic_desktop)
        setMenuItem(R.id.btn_remote_desktop, "远程桌面", R.drawable.ic_desktop)
        setMenuItem(R.id.btn_print_service, "自助打印", R.drawable.ic_print)
        setMenuItem(R.id.btn_troubleshoot, "故障排查", R.drawable.ic_build)
        setMenuItem(R.id.btn_cmd, "CMD指令", R.drawable.ic_terminal)
    }

    private fun setMenuItem(layoutId: Int, title: String, iconRes: Int) {
        val container = findViewById<View>(layoutId)
        val iconView = container.findViewById<android.widget.ImageView>(R.id.menu_icon)
        iconView.setImageResource(iconRes)
        val textView = container.findViewById<TextView>(R.id.menu_text)
        textView.text = title
    }

    override fun onNewMessage(envelope: MessageEnvelope) {
        if (envelope.Type == "Heartbeat" &&
            envelope.From.trim().equals(agentId.trim(), ignoreCase = true)) {
            runOnUiThread { updateRealtimeStats(envelope.Payload) }
        }
    }

    /**
     * 🌟 修复说明：
     * 1. 使用 getString 配合占位符处理 UI 文本（已在之前的 turn 中部分修复）
     * 2. 避免在 setText 中直接使用 String.format 或拼接
     */
    private fun updateRealtimeStats(payload: Any?) {
        try {
            val json = Gson().toJson(payload)
            val data = Gson().fromJson(json, HeartbeatPayload::class.java) ?: return

            progressCpu.setProgress(data.CpuUsage, true)
            progressRam.setProgress(data.RamUsage.toInt(), true)

            // 确保 strings.xml 中定义了：
            // <string name="cpu_format">CPU %1$d%%</string>
            // <string name="ram_format">RAM %1$.1f%%</string>
            tvCpuLabel.text = getString(R.string.cpu_format, data.CpuUsage)
            tvRamLabel.text = getString(R.string.ram_format, data.RamUsage)

        } catch (e: Exception) {
            Log.e("ControlCenter", "心跳解析异常: ${e.message}")
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        // 日志类拼接通常被允许，但如果 Lint 报错，可考虑使用占位符
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
        MonitorFragment.getWssManager(this)?.removeListener(this)
    }
}