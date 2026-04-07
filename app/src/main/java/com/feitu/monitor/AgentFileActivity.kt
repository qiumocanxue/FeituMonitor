package com.feitu.monitor

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.feitu.monitor.models.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
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

        // 1. 开启全屏模式适配刘海
        setupEdgeToEdge()

        setContentView(R.layout.activity_agent_file)

        // 🌟 2. 核心逻辑：拦截系统/侧滑返回键
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRootDirectory()) {
                    // 如果在根目录，直接退出页面
                    finish()
                } else {
                    // 如果在子目录，返回上一级文件夹
                    goBackParent()
                }
            }
        })

        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        agentAlias = intent.getStringExtra("AGENT_ALIAS") ?: "远程设备"

        initUI()

        // 注册监听
        MonitorFragment.getWssManager(this)?.addListener(this)

        // 初始加载
        loadFiles(currentPath)
    }

    /**
     * 判断当前是否处于磁盘根目录
     */
    private fun isRootDirectory(): Boolean {
        // Windows: "C:\" (len 3) | Linux: "/" (len 1)
        return (currentPath.length <= 3 && currentPath.contains(":")) || currentPath == "/"
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun initUI() {
        // 刘海屏适配：动态调整占位 View 高度
        val statusBarPlaceholder = findViewById<View>(R.id.status_bar_placeholder)
        ViewCompat.setOnApplyWindowInsetsListener(statusBarPlaceholder) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val params = view.layoutParams
            params.height = statusBar.top
            view.layoutParams = params
            insets
        }

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

        // 🌟 3. 顶部返回按钮：点击直接返回上一页 (不进行层级回退)
        findViewById<View>(R.id.btn_path_back).setOnClickListener {
            finish()
        }

        // 磁盘切换
        findViewById<View>(R.id.btn_switch_drive)?.setOnClickListener {
            showDriveSelector()
        }
    }

    private fun showDriveSelector() {
        val drives = arrayOf("C:\\", "D:\\", "E:\\", "F:\\", "G:\\")
        MaterialAlertDialogBuilder(this)
            .setTitle("切换磁盘")
            .setItems(drives) { _, which ->
                loadFiles(drives[which])
            }
            .show()
    }

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
        val wss = MonitorFragment.getWssManager(this)
        if (wss != null && wss.isConnected) {
            wss.send(json)
        } else {
            swipe.isRefreshing = false
            Toast.makeText(this, "发送失败：连接已断开", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewMessage(envelope: MessageEnvelope) {
        if (envelope.Type == "FileResponse" || envelope.Type == "FileCommand") {
            runOnUiThread {
                findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false
                try {
                    val payloadStr = gson.toJson(envelope.Payload)
                    val payloadJson = JSONObject(payloadStr)
                    val dataStr = payloadJson.optString("Data")

                    if (dataStr.isNullOrEmpty() || dataStr == "null") return@runOnUiThread

                    val responseData = gson.fromJson(dataStr, RemoteDataResponse::class.java)
                    val pathFromRemote = responseData.CurrentPath ?: currentPath
                    currentPath = pathFromRemote
                    findViewById<TextView>(R.id.tv_current_path).text = currentPath

                    val newList = mutableListOf<RemoteFile>()

                    // 文件夹
                    responseData.SubFolders?.forEach { folderName ->
                        newList.add(RemoteFile(
                            name = folderName,
                            isDirectory = true,
                            size = 0,
                            lastModified = "-",
                            absolutePath = combinePath(pathFromRemote, folderName)
                        ))
                    }

                    // 文件
                    responseData.Files?.forEach { fileItem ->
                        newList.add(RemoteFile(
                            name = fileItem.Name,
                            isDirectory = false,
                            size = fileItem.Size,
                            lastModified = fileItem.LastMod,
                            absolutePath = combinePath(pathFromRemote, fileItem.Name)
                        ))
                    }

                    // 排序
                    newList.sortWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })

                    fileList.clear()
                    fileList.addAll(newList)
                    adapter.notifyDataSetChanged()

                } catch (e: Exception) {
                    Log.e("FileWS", "❌ 解析异常: ${e.message}")
                }
            }
        }
    }

    private fun combinePath(parent: String, child: String): String {
        return when {
            parent.endsWith("\\") -> "$parent$child"
            parent.endsWith("/") -> "$parent$child"
            parent.contains("\\") -> "$parent\\$child"
            else -> "$parent/$child"
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
        Toast.makeText(this, "正在请求打开文件...", Toast.LENGTH_SHORT).show()
    }

    private fun requestDeleteFile(file: RemoteFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle("确认删除")
            .setMessage("确定删除 ${file.name} 吗？")
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
        if (isRootDirectory()) {
            Toast.makeText(this, "已经是磁盘根目录", Toast.LENGTH_SHORT).show()
            return
        }

        val separator = if (currentPath.contains("\\")) "\\" else "/"
        val cleanPath = if (currentPath.endsWith(separator)) currentPath.dropLast(1) else currentPath
        val parent = cleanPath.substringBeforeLast(separator)

        val finalParent = if (parent.endsWith(":")) "$parent\\" else if (parent.isEmpty()) separator else parent
        loadFiles(finalParent)
    }

    override fun onStateChange(state: String) {}
    override fun onError(error: String) {}

    override fun onDestroy() {
        super.onDestroy()
        MonitorFragment.getWssManager(this)?.removeListener(this)
    }
}