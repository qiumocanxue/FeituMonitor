package com.feitu.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.models.DownloadStatus
import com.feitu.monitor.models.DownloadTask
import com.feitu.monitor.models.FtpDownloadManager
import com.feitu.monitor.models.FtpUtils

class DownloadListActivity : AppCompatActivity() {

    private lateinit var adapter: DownloadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_download_list)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }

        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.title_download_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_downloads)
        FtpDownloadManager.loadTasks(this)
        updateTotalSizeHeader()

        adapter = DownloadAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 🌟 优化后的刷新逻辑：使用 submitList
        // ListAdapter 会在后台线程计算差异，只刷新变化的 Item
        FtpDownloadManager.onStateChanged = {
            // 注意：必须传入一个新的 List 对象，否则 DiffUtil 会认为集合没变而不刷新
            adapter.submitList(ArrayList(FtpDownloadManager.tasks))
            updateTotalSizeHeader()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FtpDownloadManager.onStateChanged = null
    }

    // 🌟 1. 定义 DiffUtil 比较规则
    class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadTask>() {
        override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem.url == newItem.url // URL 相同则认为是同一个任务
        }

        override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            // 比较任务的所有内容（状态、进度、速度等）
            return oldItem == newItem
        }

        // 🌟 2. 进阶优化：局部刷新 Payload
        // 如果只有进度或速度变了，返回一个标记，避免重绘整个布局
        override fun getChangePayload(oldItem: DownloadTask, newItem: DownloadTask): Any? {
            return if (oldItem.status == newItem.status && oldItem.fileName == newItem.fileName) {
                "PAYLOAD_PROGRESS"
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }

    // 🌟 3. 改为继承 ListAdapter
    inner class DownloadAdapter : ListAdapter<DownloadTask, DownloadAdapter.ViewHolder>(DownloadDiffCallback()) {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_file_name)
            val tvSpeed: TextView = view.findViewById(R.id.tv_speed_info)
            val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
            val btnAction: Button = view.findViewById(R.id.btn_action)
            val btnDelete: ImageView = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_task, parent, false)
            return ViewHolder(view)
        }

        // 🌟 4. 处理局部刷新逻辑（核心优化点）
        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains("PAYLOAD_PROGRESS")) {
                val task = getItem(position)
                updateProgress(holder, task) // 只更新进度和速度
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = getItem(position)
            val context = holder.itemView.context

            holder.tvName.text = task.fileName
            updateProgress(holder, task)

            // 状态控制
            when (task.status) {
                DownloadStatus.DOWNLOADING -> holder.btnAction.text = context.getString(R.string.action_pause)
                DownloadStatus.PAUSED -> holder.btnAction.text = context.getString(R.string.action_resume)
                DownloadStatus.COMPLETED -> holder.btnAction.text = context.getString(R.string.action_open)
                DownloadStatus.FAILED -> holder.btnAction.text = context.getString(R.string.action_retry)
                else -> {}
            }

            val clickAction = View.OnClickListener {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> FtpDownloadManager.pauseDownload(task, this@DownloadListActivity)
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> FtpDownloadManager.startOrResumeDownload(task.url, task.fileName, this@DownloadListActivity)
                    DownloadStatus.COMPLETED -> FtpUtils.openFile(this@DownloadListActivity, task.savePath)
                    else -> {}
                }
            }

            holder.btnAction.setOnClickListener(clickAction)
            holder.itemView.setOnClickListener(clickAction)

            holder.btnDelete.setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@DownloadListActivity)
                    .setTitle(R.string.dialog_delete_title)
                    .setMessage(R.string.dialog_delete_msg)
                    .setPositiveButton(R.string.delete) { _, _ -> FtpDownloadManager.deleteDownload(task, this@DownloadListActivity) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        // 将重复的 UI 更新逻辑提取出来
        private fun updateProgress(holder: ViewHolder, task: DownloadTask) {
            val context = holder.itemView.context
            val downloadedStr = FtpUtils.formatSize(task.downloadedBytes.toString())
            val totalStr = FtpUtils.formatSize(task.totalBytes.toString())

            holder.progressBar.progress = task.progress

            holder.tvSpeed.text = when (task.status) {
                DownloadStatus.DOWNLOADING -> context.getString(R.string.status_downloading, downloadedStr, totalStr, task.speed)
                DownloadStatus.PAUSED -> context.getString(R.string.status_paused, downloadedStr, totalStr)
                DownloadStatus.COMPLETED -> context.getString(R.string.status_completed, totalStr)
                DownloadStatus.FAILED -> context.getString(R.string.status_failed, task.speed)
                else -> context.getString(R.string.status_waiting)
            }
        }
    }

    private fun updateTotalSizeHeader() {
        val totalBytes = FtpDownloadManager.tasks.sumOf { it.downloadedBytes }
        val totalStr = FtpUtils.formatSize(totalBytes.toString())
        supportActionBar?.subtitle = getString(R.string.total_space_usage, totalStr)
    }
}