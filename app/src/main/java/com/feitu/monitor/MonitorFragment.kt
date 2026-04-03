package com.feitu.monitor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.feitu.monitor.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MonitorFragment : Fragment(R.layout.fragment_monitor), OnMessageReceivedListener {

    private lateinit var tvStatus: TextView
    private lateinit var viewStatusDot: View
    private lateinit var adapter: AgentAdapter
    private val gson = Gson()

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var wssInstance: MonitorWebSocketManager? = null

        fun getWssManager(context: Context): MonitorWebSocketManager? {
            if (wssInstance == null) {
                wssInstance = MonitorWebSocketManager(context.applicationContext)
            }
            return wssInstance
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tv_wss_status)
        viewStatusDot = view.findViewById(R.id.view_status_dot)
        val rvAgents = view.findViewById<RecyclerView>(R.id.rv_agents)

        adapter = AgentAdapter()
        adapter.onItemClick = { agent: AgentInfo ->
            val intent = Intent(requireContext(), AgentControlActivity::class.java).apply {
                putExtra("AGENT_ID", agent.UniqueId)
                putExtra("AGENT_ALIAS", agent.Alias)
            }
            startActivity(intent)
        }

        rvAgents.layoutManager = LinearLayoutManager(requireContext())
        rvAgents.adapter = adapter
        (rvAgents.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        val manager = getWssManager(requireContext())
        manager?.let {
            it.addListener(this)
            if (!it.isConnected) {
                startAutoConnection(it)
            }
        }
    }

    private fun startAutoConnection(manager: MonitorWebSocketManager) {
        onStateChange("正在连接...")
        manager.connect("wss://yy.qiu.ink:7122/ws-monitor")
    }

    override fun onStateChange(state: String) {
        activity?.runOnUiThread {
            if (!isAdded || isDetached) return@runOnUiThread

            tvStatus.text = state
            when (state) {
                "已连接" -> {
                    viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.GREEN)
                    // 🌟 修复警告 2: 使用 String.toColorInt() 扩展函数
                    tvStatus.setTextColor("#4CAF50".toColorInt())
                }
                "连接断开", "连接失败" -> {
                    viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.RED)
                    // 🌟 修复警告 3: 使用 String.toColorInt() 扩展函数
                    tvStatus.setTextColor("#FF5252".toColorInt())
                }
                else -> {
                    viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
                    tvStatus.setTextColor(Color.GRAY)
                }
            }
        }
    }

    override fun onNewMessage(envelope: MessageEnvelope) {
        activity?.runOnUiThread {
            if (!isAdded || isDetached) return@runOnUiThread
            try {
                when (envelope.Type) {
                    "Heartbeat" -> {
                        val json = gson.toJson(envelope.Payload)
                        val heartbeat = gson.fromJson(json, HeartbeatPayload::class.java)

                        adapter.updateAgentStats(
                            envelope.From,
                            heartbeat.CpuUsage,
                            heartbeat.RamUsage,
                            heartbeat.NetUp,
                            heartbeat.NetDown
                        )
                    }
                    "AgentList" -> {
                        val json = gson.toJson(envelope.Payload)
                        val type = object : TypeToken<List<AgentInfo>>() {}.type
                        val list: List<AgentInfo> = gson.fromJson(json, type)
                        adapter.setAgents(list)
                    }
                }
            } catch (e: Exception) {
                Log.e("MonitorUI", "数据处理异常: ${e.message}")
            }
        }
    }

    override fun onError(error: String) {
        onStateChange("连接失败")
        Log.e("MonitorUI", "WSS错误反馈: $error")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 仅移除自己的监听，不破坏整个链路
        getWssManager(requireContext())?.removeListener(this)
    }
}