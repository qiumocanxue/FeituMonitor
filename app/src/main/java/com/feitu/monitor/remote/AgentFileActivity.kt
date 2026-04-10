package com.feitu.monitor.remote

import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.feitu.monitor.MonitorFragment
import com.feitu.monitor.R
import com.feitu.monitor.common.models.MessageEnvelope
import com.feitu.monitor.common.utils.FileBase64Utils
import com.feitu.monitor.models.OnMessageReceivedListener
import com.feitu.monitor.remote.models.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class AgentFileActivity : AppCompatActivity(), OnMessageReceivedListener {
    private val TAG_LOG = "FileTransfer"
    private var currentPath = "C:\\"
    private var agentId = ""
    private var agentAlias = ""
    private val fileList = mutableListOf<RemoteFile>()
    private lateinit var adapter: FileAdapter
    private val gson = Gson()
    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    // 🌟 下载和上传的状态管理
    private var downloadFileOutputStream: FileOutputStream? = null
    private var totalDownloadedBytes = 0L
    private var pendingUploadUri: android.net.Uri? = null
    private var pendingRemotePath: String? = null

    private var pendingEditBytes: ByteArray? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { prepareUploadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        setContentView(R.layout.activity_agent_file)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRootDirectory()) finish() else goBackParent()
            }
        })

        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        agentAlias = intent.getStringExtra("AGENT_ALIAS") ?: "远程设备"

        initUI()
        MonitorFragment.getWssManager(this)?.addListener(this)
        loadFiles(currentPath)
    }

    // --- 🌟 核心：处理二进制帧（下载文件流） ---
    override fun onNewBinaryMessage(bytes: ByteString) {
        try {
            if (downloadFileOutputStream == null) {
                Log.e(TAG_LOG, "❌ 接收到二进制数据，但本地文件流未初始化！")
                return
            }
            val size = bytes.size
            downloadFileOutputStream?.write(bytes.toByteArray())
            totalDownloadedBytes += size

            // 调试日志：每接收到一部分数据打印一次
            Log.d(TAG_LOG, "<<< [接收中] 收到二进制帧: $size 字节, 累计接收: $totalDownloadedBytes 字节")
        } catch (e: Exception) {
            Log.e(TAG_LOG, "❌ 写入本地文件异常: ${e.message}", e)
        }
    }

    // --- 🌟 核心：处理控制信令（JSON） ---
    override fun onNewMessage(envelope: MessageEnvelope) {
        // 🌟 打印所有收到的控制信令，方便对比状态
        Log.i("FileTransfer", "📥 [收到信令] 类型: ${envelope.Type} | 来自: ${envelope.From}")

        runOnUiThread {
            val payloadJson = gson.toJson(envelope.Payload)
            val response = gson.fromJson(payloadJson, FileResponsePayload::class.java)

            when (envelope.Type) {
                "FileStart" -> {
                    Log.i("FileTransfer", "🏁 [下载开始] 准备接收文件: ${response.FileName}")
                    totalDownloadedBytes = 0L // 重置下载计数
                    startLocalDownloadStream(response.FileName ?: "file")
                }

                "FileEnd" -> {
                    Log.i("FileTransfer", "✅ [下载完成] 累计接收: $totalDownloadedBytes 字节")
                    closeDownloadStream(true)
                }

                "UploadReady" -> {
                    Log.i("FileTransfer", "🚀 [上传就绪] Agent 已准备好接收二进制流，开始读取本地文件...")
                    performBinaryUpload()
                }

                "FileResponse" -> {
                    // 收到任何文件响应，先把下拉刷新和加载弹窗停掉
                    findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false

                    if (response.Status == "Success") {
                        // 🌟 核心：删除成功后，Agent 会返回 Status: Success
                        // 我们在这里关闭“正在请求删除...”的弹窗
                        dismissProgressDialog()

                        if (response.Data == null) {
                            // 说明这是一个纯成功回执（如删除成功），我们手动刷新一次列表
                            Log.i(TAG_LOG, "🆗 删除操作成功，正在刷新列表...")
                            Toast.makeText(this, "文件已删除", Toast.LENGTH_SHORT).show()
                            loadFiles(currentPath)
                        } else {
                            // 说明是列表数据回来了，直接更新 UI
                            handleListFilesResponse(response.Data)
                        }
                    }
                }

                "Error" -> {
                    // 🌟 核心：如果删除失败（如文件被占用），务必关闭加载弹窗
                    dismissProgressDialog()
                    findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false

                    Log.e(TAG_LOG, "❌ 删除失败: ${response.Message}")
                    MaterialAlertDialogBuilder(this)
                        .setTitle("删除失败")
                        .setMessage(response.Message ?: "远程设备拒绝了删除请求")
                        .setPositiveButton("我知道了", null)
                        .show()
                }
            }
        }
    }

    // --- 🌟 上传流程逻辑 ---

    private fun prepareUploadFile(uri: android.net.Uri) {
        val fileName = getFileNameFromUri(uri) ?: "upload_${System.currentTimeMillis()}"
        val remotePath = combinePath(currentPath, fileName)

        var fileSize = 0L
        var totalChunks = 0
        val chunkSize = 64 * 1024 // 64KB
        val MAX_UPLOAD_SIZE = 12 * 1024 * 1024 // 🌟 限制为 12MB

        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fileSize = fd.statSize
            }
        } catch (e: Exception) {
            Log.e(TAG_LOG, "获取文件信息失败: ${e.message}")
        }

        // 🌟 核心拦截逻辑：如果文件超过 12MB，直接弹窗拦截
        if (fileSize > MAX_UPLOAD_SIZE) {
            MaterialAlertDialogBuilder(this)
                .setTitle("文件过大")
                .setMessage("当前限制上传最大 12MB 的文件。\n该文件大小为: ${String.format("%.2f", fileSize / 1024.0 / 1024.0)} MB")
                .setPositiveButton("我知道了", null)
                .show()
            Log.w(TAG_LOG, "⚠️ 上传终止: 文件大小 ($fileSize) 超过限制")
            return // 终止后续逻辑
        }

        // 计算分片数
        totalChunks = Math.ceil(fileSize.toDouble() / chunkSize).toInt()

        Log.i(TAG_LOG, "📤 [上传请求] 文件: $fileName | 大小: $fileSize | 总片数: $totalChunks")

        pendingUploadUri = uri
        pendingRemotePath = remotePath

        MaterialAlertDialogBuilder(this)
            .setTitle("确认上传")
            .setMessage("文件：$fileName\n大小：${String.format("%.2f", fileSize / 1024.0)} KB\n确认上传至远程设备？")
            .setPositiveButton("开始上传") { _, _ ->
                showProgressDialog("请稍候", "正在初始化上传请求...")

                val command = FileCommand(
                    From = "Android_Client",
                    To = agentId,
                    Payload = FilePayload(
                        Action = "UploadStart",
                        Path = remotePath,
                        FileSize = fileSize,
                        TotalChunks = totalChunks
                    )
                )

                val json = gson.toJson(command)
                Log.i("FileTransfer", "📤 [发送指令] Action: UploadStart")
                MonitorFragment.getWssManager(this)?.send(json)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performBinaryUpload() {
        val wss = MonitorFragment.getWssManager(this) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 模式 A：从手机选取的文件 (URI 模式)
                if (pendingUploadUri != null) {
                    contentResolver.openInputStream(pendingUploadUri!!)?.use { input ->
                        val totalSize = input.available().toLong()
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalSent = 0L
                        Log.i(TAG_LOG, "📦 [URI上传] 开始传输...")

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            wss.send(buffer.toByteString(0, bytesRead))
                            totalSent += bytesRead
                            updateUploadProgress(totalSent, totalSize)
                        }
                    }
                }
                // 模式 B：编辑器保存的文本 (ByteArray 模式)
                else if (pendingEditBytes != null) {
                    val data = pendingEditBytes!!
                    Log.i(TAG_LOG, "📦 [字节流上传] 正在保存编辑内容: ${data.size} 字节")
                    // 直接发送整个字节数组（如果是大文件，建议也分片，但文本通常很小）
                    wss.send(data.toByteString())
                }

                // 无论哪种模式，结束时发送 UploadEnd
                val endCommand = FileCommand(
                    From = "Android_Client", To = agentId,
                    Payload = FilePayload(Action = "UploadEnd", Path = "")
                )
                withContext(Dispatchers.Main) {
                    wss.send(gson.toJson(endCommand))
                    Log.i(TAG_LOG, "🏁 [上传结束] 指令已发送")
                }
            } catch (e: Exception) {
                Log.e(TAG_LOG, "❌ [上传/保存异常]: ${e.message}")
                withContext(Dispatchers.Main) { dismissProgressDialog() }
            } finally {
                pendingUploadUri = null
                pendingEditBytes = null
            }
        }
    }

    private suspend fun updateUploadProgress(sent: Long, total: Long) {
        if (sent % (256 * 1024) < 64 * 1024) { // 降低刷新频率
            withContext(Dispatchers.Main) {
                val percent = if (total > 0) (sent * 100 / total) else 0
                progressDialog?.setMessage("已发送: $percent% (${sent / 1024} KB)")
            }
        }
    }

    // --- 🌟 下载流程逻辑 ---

    private fun startLocalDownloadStream(fileName: String) {
        try {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, fileName)
            downloadFileOutputStream = FileOutputStream(file)
            Log.i(TAG_LOG, "📁 [下载流初始化] 本地保存路径: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG_LOG, "❌ [下载流初始化失败]: ${e.message}")
        }
    }

    private fun closeDownloadStream(success: Boolean) {
        try {
            downloadFileOutputStream?.flush()
            downloadFileOutputStream?.close()
            downloadFileOutputStream = null
            Log.i(TAG_LOG, "🔒 [下载流关闭] success=$success")
        } catch (e: Exception) {
            Log.e(TAG_LOG, "❌ [下载流关闭异常]: ${e.message}")
        }
    }

    // --- 原有 UI 与 辅助方法 ---

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun initUI() {
        val statusBarPlaceholder = findViewById<View>(R.id.status_bar_placeholder)
        ViewCompat.setOnApplyWindowInsetsListener(statusBarPlaceholder) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.layoutParams.height = statusBar.top
            insets
        }

        findViewById<TextView>(R.id.tv_current_path).text = currentPath
        val rvFiles = findViewById<RecyclerView>(R.id.rv_files)
        rvFiles.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(fileList) { file ->
            if (file.isDirectory) enterFolder(file.absolutePath) else showFileOptions(file)
        }
        rvFiles.adapter = adapter

        findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).setOnRefreshListener { loadFiles(currentPath) }
        findViewById<View>(R.id.btn_path_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_upload)?.setOnClickListener { pickFileLauncher.launch("*/*") }
        findViewById<View>(R.id.btn_switch_drive)?.setOnClickListener { showDriveSelector() }
    }

    private fun showProgressDialog(title: String, message: String) {
        runOnUiThread {
            if (progressDialog == null) {
                // 创建水平进度条
                val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = true
                    setPadding(50, 50, 50, 50)
                }

                progressDialog = MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setView(progressBar)
                    .setCancelable(false) // 传输中不允许点外部取消
                    .create()
            } else {
                progressDialog?.setTitle(title)
                progressDialog?.setMessage(message)
            }
            progressDialog?.show()
            acquireWakeLock()
        }
    }

    private fun dismissProgressDialog() {
        runOnUiThread {
            progressDialog?.dismiss()
            progressDialog = null
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Feitu:TransferLock")
            wakeLock?.acquire(10 * 60 * 1000L) // 10分钟超时
        } catch (e: Exception) {
            Log.e(TAG_LOG, "唤醒锁获取失败: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun handleOpenFileResponse(path: String, content: String) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_file, null)
        val editText = view.findViewById<EditText>(R.id.et_file_content)
        editText.setText(content)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("编辑: ${path.substringAfterLast("\\")}")
            .setView(view)
            .setCancelable(false)
            .setNeutralButton("关闭", null)
            .setPositiveButton("保存更改") { _, _ ->
                val newContentBytes = editText.text.toString().toByteArray()
                saveEditedFileAsBinary(path, newContentBytes)
            }
            .create()

        dialog.show()
        val feituBlue = androidx.core.content.ContextCompat.getColor(this, R.color.feitu_blue)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(feituBlue)
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setTextColor(feituBlue)
    }

    private fun saveEditedFileAsBinary(path: String, bytes: ByteArray) {
        // 1. 暂存数据，等待 UploadReady 回调时使用
        pendingEditBytes = bytes
        pendingRemotePath = path
        pendingUploadUri = null // 清除 URI，确保 performBinaryUpload 知道这是字节流模式

        Log.i(TAG_LOG, "📝 [准备保存编辑] 路径: $path | 字节数: ${bytes.size}")

        // 2. 发送 UploadStart 指令
        val command = FileCommand(
            From = "Android_Client",
            To = agentId,
            Payload = FilePayload(
                Action = "UploadStart",
                Path = path,
                FileSize = bytes.size.toLong(),
                TotalChunks = 1 // 文本文件通常很小，设为1片即可
            )
        )

        showProgressDialog("正在保存", "正在提交更改...")
        MonitorFragment.getWssManager(this)?.send(gson.toJson(command))
    }

    private fun loadFiles(path: String) {
        currentPath = path
        findViewById<TextView>(R.id.tv_current_path).text = path
        val swipe = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipe.isRefreshing = true
        val command = FileCommand(From="Android_Client", To=agentId, Payload=FilePayload(Action=FileAction.LIST, Path=path))
        MonitorFragment.getWssManager(this)?.send(gson.toJson(command))
    }

    private fun handleListFilesResponse(data: RemoteDataResponse) {
        val pathFromRemote = data.CurrentPath ?: currentPath
        currentPath = pathFromRemote
        findViewById<TextView>(R.id.tv_current_path).text = currentPath
        val newList = mutableListOf<RemoteFile>()
        data.SubFolders?.forEach { newList.add(RemoteFile(it, true, 0, "-", combinePath(pathFromRemote, it))) }
        data.Files?.forEach { newList.add(RemoteFile(it.Name, false, it.Size, it.LastMod, combinePath(pathFromRemote, it.Name))) }
        newList.sortWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        fileList.clear()
        fileList.addAll(newList)
        adapter.notifyDataSetChanged()
    }

    private fun showFileOptions(file: RemoteFile) {
        val options = mutableListOf<String>()
        val actionMap = mutableListOf<Int>()
        if (isTextFile(file.name)) { options.add("查看/编辑"); actionMap.add(0) }
        options.add("下载"); actionMap.add(1)
        options.add("删除"); actionMap.add(2)

        MaterialAlertDialogBuilder(this).setTitle(file.name).setItems(options.toTypedArray()) { _, which ->
            when (actionMap[which]) {
                0 -> requestOpenFile(file)
                1 -> requestDownloadFile(file)
                2 -> requestDeleteFile(file)
            }
        }.show()
    }

    private fun requestOpenFile(file: RemoteFile) {
        val command = FileCommand(
            From = "Android_Client",
            To = agentId,
            Payload = FilePayload(Action = "OpenFile", Path = file.absolutePath)
        )
        val json = gson.toJson(command)

        Log.i(TAG_LOG, "📤 [发送查看指令] 路径: ${file.absolutePath}")
        Log.d(TAG_LOG, "📝 JSON: $json")

        val wss = MonitorFragment.getWssManager(this)
        if (wss != null && wss.isConnected) {
            wss.send(json)
            Toast.makeText(this, "正在读取文件内容...", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG_LOG, "❌ 发送失败：WS 未连接")
            Toast.makeText(this, "未连接到远程设备", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestDownloadFile(file: RemoteFile) {
        val command = FileCommand(
            From = "Android_Client", To = agentId,
            Payload = FilePayload(Action = FileAction.DOWNLOAD, Path = file.absolutePath)
        )
        val json = gson.toJson(command)

        Log.i("FileTransfer", "📤 [发送指令] Action: Download | 远程文件: ${file.absolutePath}")
        Log.d("FileTransfer", "📝 [JSON 详情]: $json")

        MonitorFragment.getWssManager(this)?.send(json)
    }

    private fun requestDeleteFile(file: RemoteFile) {
        // 1. 先弹窗确认，防止误删
        MaterialAlertDialogBuilder(this)
            .setTitle("确认删除")
            .setMessage("确定要彻底删除文件：\n${file.name} 吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                // 2. 显示加载弹窗，表示正在操作
                showProgressDialog("请稍候", "正在请求删除文件...")

                // 3. 构造删除指令
                val command = FileCommand(
                    From = "Android_Client",
                    To = agentId,
                    Payload = FilePayload(
                        Action = "Delete", // 🌟 对应协议中的 Action
                        Path = file.absolutePath
                    )
                )

                val json = gson.toJson(command)
                Log.i(TAG_LOG, "📤 [发送删除指令] 路径: ${file.absolutePath}")
                Log.d(TAG_LOG, "📝 JSON: $json")

                // 4. 发送指令
                val wss = MonitorFragment.getWssManager(this)
                if (wss != null && wss.isConnected) {
                    wss.send(json)
                } else {
                    dismissProgressDialog()
                    Toast.makeText(this, "发送失败：连接已断开", Toast.LENGTH_SHORT).show()
                }
            }.apply {
                // 设置删除按钮为红色（警告色），取消按钮为 feitu_blue
                val dialog = this.show()
                val feituBlue = androidx.core.content.ContextCompat.getColor(context, R.color.feitu_blue)
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(Color.RED)
                dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setTextColor(feituBlue)
            }
    }

    // --- 🌟 补全缺失的辅助方法 ---

    /**
     * 进入文件夹：加载指定路径的文件列表
     */
    private fun enterFolder(path: String) {
        loadFiles(path)
    }

    /**
     * 显示磁盘选择对话框
     */
    private fun showDriveSelector() {
        val drives = arrayOf("C:\\", "D:\\", "E:\\", "F:\\", "G:\\")
        MaterialAlertDialogBuilder(this)
            .setTitle("切换磁盘")
            .setItems(drives) { _, which ->
                loadFiles(drives[which])
            }
            .show()
    }

    private fun isRootDirectory() = (currentPath.length <= 3 && currentPath.contains(":")) || currentPath == "/"
    private fun isTextFile(name: String) = listOf(".txt", ".log", ".ini", ".json", ".xml", ".conf", ".bat", ".sh", ".py", ".java", ".kt", ".properties").any { name.lowercase().endsWith(it) }
    private fun combinePath(p: String, c: String) = when { p.endsWith("\\") || p.endsWith("/") -> "$p$c"; p.contains("\\") -> "$p\\$c"; else -> "$p/$c" }
    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        if (uri.scheme == "content") contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) return it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) }
        return uri.path?.substringAfterLast('/')
    }
    private fun goBackParent() {
        val separator = if (currentPath.contains("\\")) "\\" else "/"
        val parent = currentPath.removeSuffix(separator).substringBeforeLast(separator)
        loadFiles(if (parent.endsWith(":")) "$parent\\" else if (parent.isEmpty()) separator else parent)
    }

    override fun onStateChange(state: String) {}
    override fun onError(error: String) { runOnUiThread { findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false } }
    override fun onDestroy() {
        super.onDestroy()
        dismissProgressDialog()
        releaseWakeLock()
        MonitorFragment.getWssManager(this)?.removeListener(this)
    }
}