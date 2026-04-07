package com.feitu.monitor

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.feitu.monitor.models.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class AgentFileActivity : AppCompatActivity(), OnMessageReceivedListener {

    private var currentPath = "C:\\"
    private var agentId = ""
    private var agentAlias = ""
    private val fileList = mutableListOf<RemoteFile>()
    private lateinit var adapter: FileAdapter
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_file)

        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        agentAlias = intent.getStringExtra("AGENT_ALIAS") ?: "远程设备"

        initUI()

        // 注册监听
        MonitorFragment.getWssManager(this)?.addListener(this)

        loadFiles(currentPath)
    }

    private fun initUI() {
        findViewById<TextView>(R.id.tv_current_path).text = currentPath

        val rvFiles = findViewById<RecyclerView>(R.id.rv_files)
        rvFiles.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(fileList) { file ->
            if (file.isDirectory) {
                enterFolder(file.absolutePath)
            } else {
                showFileOptions(file)
            }
        }
        rvFiles.adapter = adapter

        findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).setOnRefreshListener {
            loadFiles(currentPath)
        }

        findViewById<View>(R.id.btn_path_back).setOnClickListener {
            goBackParent()
        }
    }

    /**
     * 🌟 修复后的发送逻辑
     */
    private fun loadFiles(path: String) {
        currentPath = path
        findViewById<TextView>(R.id.tv_current_path).text = path

        val swipe = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipe.isRefreshing = true

        val command = FileCommand(
            From = "Android_Client",
            To = agentId,
            Payload = FilePayload(
                Action = FileAction.LIST,
                Path = path
            )
        )

        val json = gson.toJson(command)
        Log.d("FileWS", "📤 发送请求: $json")

        // 🌟 修复点：先获取管理器，再判断是否为空，不捕获 send() 的返回值
        val wss = MonitorFragment.getWssManager(this)
        if (wss != null && wss.isConnected) {
            wss.send(json)
        } else {
            swipe.isRefreshing = false
            Toast.makeText(this, "发送失败：连接已断开", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewMessage(envelope: MessageEnvelope) {
        if (envelope.Type == "FileCommand") {
            Log.d("FileWS", "📥 收到响应: ${envelope.Payload}")

            runOnUiThread {
                findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false
                try {
                    val payloadStr = gson.toJson(envelope.Payload)
                    val payloadJson = JSONObject(payloadStr)

                    // 🌟 注意：请根据后端返回的实际字段名修改这里的 "Data"
                    // 如果后端返回的是 {"success":true, "payload": "..."}，这里应改为 "payload"
                    val dataArrayStr = payloadJson.optString("Data")

                    if (!dataArrayStr.isNullOrEmpty() && dataArrayStr != "null") {
                        val type = object : TypeToken<List<RemoteFile>>() {}.type
                        val newList = gson.fromJson<List<RemoteFile>>(dataArrayStr, type)

                        fileList.clear()
                        fileList.addAll(newList ?: emptyList())
                        adapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    Log.e("FileWS", "解析错误: ${e.message}")
                }
            }
        }
    }

    private fun enterFolder(path: String) {
        loadFiles(path)
    }

    private fun showFileOptions(file: RemoteFile) {
        val options = arrayOf("查看内容", "删除文件")
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestOpenFile(file)
                    1 -> requestDeleteFile(file)
                }
            }.show()
    }

    private fun requestOpenFile(file: RemoteFile) {
        val command = FileCommand(
            From = "Android", To = agentId,
            Payload = FilePayload(Action = FileAction.OPEN, Path = file.absolutePath)
        )
        MonitorFragment.getWssManager(this)?.send(gson.toJson(command))
    }

    private fun requestDeleteFile(file: RemoteFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle("确认删除")
            .setMessage("确定删除 ${file.name}？")
            .setPositiveButton("确定") { _, _ ->
                val command = FileCommand(
                    From = "Android", To = agentId,
                    Payload = FilePayload(Action = FileAction.DELETE, Path = file.absolutePath)
                )
                MonitorFragment.getWssManager(this)?.send(gson.toJson(command))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun goBackParent() {
        if (currentPath == "C:\\" || currentPath == "/") return
        val separator = if (currentPath.contains("\\")) "\\" else "/"
        val parent = currentPath.substringBeforeLast(separator)
        loadFiles(if (parent.isEmpty() || !parent.contains(separator))
            (if (separator == "\\") "C:\\" else "/") else parent)
    }

    override fun onStateChange(state: String) {}
    override fun onError(error: String) {}

    override fun onDestroy() {
        super.onDestroy()
        MonitorFragment.getWssManager(this)?.removeListener(this)
    }
}